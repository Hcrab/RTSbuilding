package com.rtsbuilding.rtsbuilding.server.service.destruction;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyPayload;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsBatchJobTickOps;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsDropAbsorber;
import com.rtsbuilding.rtsbuilding.server.service.mining.MiningSpeedCalculator;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLeaseManager;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 批处理范围破坏作业管理器，负责远程范围破坏（AREA_DESTROY）的排队和每 tick 节流处理。
 *
 * <p>管理破坏作业的完整生命周期：将范围破坏请求排队为 {@link DestructionJob}，
 * 通过 {@link #tickDestroyJobs} 以自适应每 tick 处理量节流处理，
 * 以及作业的暂停/恢复/完成流程。
 *
 * <p>对齐 {@link com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch} 的架构：
 * Pipeline 仅负责入队，实际处理通过 {@link com.rtsbuilding.rtsbuilding.server.service.ServerTickOrchestrator}
 * 统一调度，采用 asyncCompletion 生命周期。
 *
 * <p>不负责：工具借用（{@link com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe}）、
 * 协议进度初始化（{@link com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe}）。
 */
public final class RtsDestructionBatch {

    /** 单个 tick 中处理的最大破坏目标数，与范围放置 {@code BUILD_BATCH_MAX_BLOCKS_PER_TICK} 对齐。 */
    private static final int DESTROY_MAX_BLOCKS_PER_TICK = 64;

    /** 快速建造破坏的最大排队作业数。 */
    private static final int DESTROY_MAX_QUEUED_JOBS = 4;

    private RtsDestructionBatch() {
    }

    // =========================================================================
    //  入队
    // =========================================================================

    /**
     * 将范围破坏请求排队为待处理的 {@link DestructionJob}。
     *
     * <p>创造模式与生存模式均排队为作业，由下一 tick 开始逐 tick 异步处理。
     * 快速建造破坏（形状预览）受 {@link #DESTROY_MAX_QUEUED_JOBS} 限制。
     *
     * @return {@code true} 如果作业被排队；{@code false} 如果没有有效目标
     */
    public static boolean enqueueDestroyBatch(ServerPlayer player, RtsStorageSession session,
            List<BlockPos> positions, byte toolSlot, boolean toolProtectionEnabled,
            int workflowEntryId) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.AREA_DESTROY)) {
            return false;
        }
        if (session == null || positions == null || positions.isEmpty()) {
            return false;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        boolean creative = player.isCreative();
        boolean selectedToolRequested = session.mining.miningSelectedToolRequested;
        ItemStack linkedTool = (creative || session.mining.miningToolLease == null)
                ? ItemStack.EMPTY
                : session.mining.miningToolLease.stack();

        // 收集并验证目标
        Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, linkedTool,
                selectedToolRequested, creative);
        if (targets.isEmpty()) {
            return false;
        }

        // 快速建造破坏限制：最多 DESTROY_MAX_QUEUED_JOBS 个排队作业
        // 创造模式与生存模式均使用相同的逐 tick 异步队列处理
        if (session.destruction.destroyJobs.size() >= DESTROY_MAX_QUEUED_JOBS) {
            RtsbuildingMod.LOGGER.warn("[RtsDestructionBatch] {} destroy job queue is full (max {}), rejecting new job",
                    player.getGameProfile().getName(), DESTROY_MAX_QUEUED_JOBS);
            return false;
        }

        session.destruction.destroyJobs.addLast(new DestructionJob(
                new ArrayList<>(targets),
                (byte) slot,
                toolProtectionEnabled,
                selectedToolRequested,
                workflowEntryId,
                targets.size()));

        RtsbuildingMod.LOGGER.info("[RtsDestructionBatch] {} enqueued {} destroy targets (queue size={})",
                player.getGameProfile().getName(), targets.size(), session.destruction.destroyJobs.size());
        return true;
    }

    // =========================================================================
    //  Tick 处理
    // =========================================================================

    /**
     * Tick 处理器，从排队的破坏作业中处理最多 {@link #DESTROY_MAX_BLOCKS_PER_TICK}
     * 个方块。使用自适应公式：处理量 = min(64, max(1, total/10))。
     *
     * <p>在处理前先尝试恢复挂起的破坏作业（{@link #tryResumePendingDestroyJobs}）。
     *
     * <p>当完整的作业完成时，记录历史、更新工作流进度、归还工具（如果是最后的作业）、
     * 刷新储存页面。
     */
    public static void tickDestroyJobs(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) {
            return;
        }

        // 先尝试恢复挂起的破坏作业（工具修复或更换后）
        tryResumePendingDestroyJobs(player, session);

        if (session.destruction.destroyJobs.isEmpty()) {
            return;
        }

        int totalBlocks = 0;
        for (DestructionJob j : session.destruction.destroyJobs) {
            totalBlocks += j.totalCount();
        }
        if (totalBlocks <= 0) {
            return;
        }

        int remaining = Math.min(DESTROY_MAX_BLOCKS_PER_TICK, Math.max(1, totalBlocks / 10));
        if (remaining <= 0) {
            return;
        }

        // 记录此 tick 开始前每个 job 的已破坏数，用于按 job 独立更新工作流进度
        Map<Integer, Integer> destroyedBeforeTick = new HashMap<>();
        List<DestructionJob> fullyCompletedJobs = new ArrayList<>();
        for (DestructionJob j : session.destruction.destroyJobs) {
            destroyedBeforeTick.put(j.workflowEntryId(), j.destroyedPositions.size());
        }

        var pausedJobsSkipped = new RtsBatchJobTickOps.MutableInt(0);
        ServerLevel level = player.serverLevel();

        while (remaining > 0 && !session.destruction.destroyJobs.isEmpty()) {
            DestructionJob job = session.destruction.destroyJobs.peekFirst();

            // ── 工作流状态检查 ──────────────────────────────────────
            var checkResult = RtsBatchJobTickOps.checkPausedOrCancelled(
                    session.destruction.destroyJobs, job, player,
                    DestructionJob::workflowEntryId, pausedJobsSkipped);
            if (checkResult == null) {
                break; // 所有剩余 job 都已暂停
            }
            if (checkResult.isEmpty()) {
                continue; // 此 job 被跳过（已取消或暂停中）
            }
            var tokenOpt = Optional.ofNullable(checkResult.get().token());

            // ── 工具耐久检查 ────────────────────────────────────────
            if (job.toolProtectionEnabled && RtsMiningValidator.isToolNearBreak(player, session)) {
                // 工具即将损坏，挂起到 pendingDestroyJobs
                session.destruction.destroyJobs.removeFirst();
                session.destruction.pendingDestroyJobs.addLast(job);
                tokenOpt.get().suspend();
                RtsbuildingMod.LOGGER.info("[RtsDestructionBatch] {} tool near break, suspending destroy job #{}",
                        player.getGameProfile().getName(), job.workflowEntryId());
                break;
            }

            // ── 处理方块 ────────────────────────────────────────────
            boolean madeProgress = false;
            while (remaining > 0 && job.hasNext()) {
                BlockPos target = job.next();

                // 验证：世界可达性
                if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
                    job.skippedWhileProcessing++;
                    continue;
                }
                BlockState state = level.getBlockState(target);
                // 验证：可破坏 + 有效破坏速度
                if (!RtsMiningValidator.isBreakableBlock(state)
                        || !RtsMiningValidator.hasValidDestroySpeed(state, level, target)) {
                    job.skippedWhileProcessing++;
                    continue;
                }
                // 验证：工具能对其造成进度（创造模式可破坏任何方块，跳过此检查）
                if (!player.isCreative() && MiningSpeedCalculator.computeRemoteDestroyStep(player, state, target,
                        job.toolSlot(),
                        session.mining.miningToolLease != null ? session.mining.miningToolLease.stack() : ItemStack.EMPTY,
                        job.selectedToolRequested()) <= 0.0F) {
                    job.skippedWhileProcessing++;
                    continue;
                }

                // 破坏前捕获历史快照
                HistoryBlockRecord preRecord = ServerHistoryManager.captureBlock(level, target);
                List<HistoryBlockRecord> neighborRecords = captureNeighborRecords(level, target);

                // 执行破坏
                var result = RtsMiningStateMachine.destroyMinedBlock(player, session, target, job.toolSlot());

                if (result.broken()) {
                    job.destroyedPositions.add(target);
                    if (preRecord != null) {
                        job.processedRecords.add(preRecord);
                    }
                    // 记录附属破坏（多方块结构）
                    recordCollateralBlocks(level, job, neighborRecords, target);

                    // 吸收掉落物
                    if (RtsMiningValidator.canAutoStoreDrops(player, session)) {
                        RtsDropAbsorber.absorbMinedDropsImmediately(player, session, target);
                    }

                    madeProgress = true;

                    // 破坏后再次检查工具耐久
                    if (job.toolProtectionEnabled && RtsMiningValidator.isToolNearBreak(player, session)) {
                        job.unconsumeLast();
                        session.destruction.destroyJobs.removeFirst();
                        session.destruction.pendingDestroyJobs.addLast(job);
                        tokenOpt.get().suspend();
                        RtsbuildingMod.LOGGER.info("[RtsDestructionBatch] {} tool near break after block break, suspending destroy job #{}",
                                player.getGameProfile().getName(), job.workflowEntryId());
                        madeProgress = false;
                        break;
                    }
                } else {
                    // destroyMinedBlock 未能破坏此方块（工具损坏、方块已变化等），计为失败
                    job.skippedWhileProcessing++;
                }
                remaining--;
            }

            // ── Job 完成检测 ─────────────────────────────────────────
            if (!session.destruction.destroyJobs.isEmpty()
                    && session.destruction.destroyJobs.peekFirst() == job
                    && !job.hasNext()) {
                session.destruction.destroyJobs.removeFirst();
                fullyCompletedJobs.add(job);
            }
        }

        // ── 处理此 tick 内完成的 job ────────────────────────────────
        RtsBatchJobTickOps.processCompletedJobs(
                player, session,
                fullyCompletedJobs, destroyedBeforeTick,
                DestructionJob::workflowEntryId,
                j -> j.destroyedPositions.size(),
                j -> j.skippedWhileProcessing,
                (p, job) -> {
                    if (!job.destroyedPositions.isEmpty()) {
                        ServerHistoryManager.recordBreak(p, job.destroyedPositions, Direction.DOWN);
                    }
                },
                (p, job) -> RtsbuildingMod.LOGGER.info("[RtsDestructionBatch] {} completed destroy job #{} ({} destroyed)",
                        p.getGameProfile().getName(), job.workflowEntryId(), job.destroyedPositions.size()));

        // ── 更新中途进度 ────────────────────────────────────────────
        RtsBatchJobTickOps.updateMidProgress(
                player, session,
                session.destruction.destroyJobs, destroyedBeforeTick,
                DestructionJob::workflowEntryId,
                j -> j.destroyedPositions.size());

        // ── 在作业被消耗/无更多 job 时归还工具 ──────────────────────
        if (session.destruction.destroyJobs.isEmpty() && session.destruction.pendingDestroyJobs.isEmpty()) {
            if (session.mining.miningToolLease != null && !session.mining.miningToolLease.isEmpty()) {
                RtsToolLeaseManager.returnMiningTool(player, session, session.mining.miningToolLease);
                session.mining.miningToolLease = RtsToolLease.empty();
                RtsbuildingMod.LOGGER.info("[RtsDestructionBatch] {} all destroy jobs complete, tool returned",
                        player.getGameProfile().getName());
            }
        }
    }

    // =========================================================================
    //  挂起作业恢复
    // =========================================================================

    /**
     * 尝试恢复所有因工具耐久不足而挂起的破坏作业。
     *
     * <p>对齐 {@link com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService#resumeAllPendingJobs}
     * 的模式：当工具不再处于即将损坏状态（已修复/更换/工具保护已关闭）时，
     * 将挂起作业从 {@code pendingDestroyJobs} 移回 {@code destroyJobs} 继续执行。
     *
     * <p>如果工具仍然即将损坏，尝试归还原工具并从玩家库存或链接存储中借用一把新工具。
     * 若借用成功则恢复作业；否则保持挂起状态，等待下次机会。
     */
    public static void tryResumePendingDestroyJobs(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) {
            return;
        }
        if (session.destruction.pendingDestroyJobs.isEmpty()) {
            return;
        }

        // 检查当前工具是否可用
        boolean toolAvailable = !session.mining.miningToolProtectionEnabled
                || !RtsMiningValidator.isToolNearBreak(player, session);

        // 工具仍然即将损坏 — 尝试归还原工具并借用新工具
        if (!toolAvailable
                && session.mining.miningToolLease != null
                && !session.mining.miningToolLease.isEmpty()) {
            ItemStack currentTool = session.mining.miningToolLease.stack();
            if (!currentTool.isEmpty()) {
                String toolItemId = BuiltInRegistries.ITEM.getKey(currentTool.getItem()).toString();

                // 归还原工具
                RtsToolLeaseManager.returnMiningTool(player, session, session.mining.miningToolLease);
                session.mining.miningToolLease = RtsToolLease.empty();

                // 尝试借用新工具（使用第一个 pending job 的 toolSlot）
                byte toolSlot = session.destruction.pendingDestroyJobs.peekFirst().toolSlot();
                RtsToolLease newLease = RtsToolLeaseManager.borrowMiningTool(
                        player, session, toolItemId, currentTool, toolSlot);
                if (!newLease.isEmpty()) {
                    session.mining.miningToolLease = newLease;
                    toolAvailable = true;
                }
            }
        }

        if (!toolAvailable) {
            return;
        }

        // 将所有挂起作业移回活跃队列，恢复工作流
        List<DestructionJob> resumed = new ArrayList<>();
        while (!session.destruction.pendingDestroyJobs.isEmpty()) {
            DestructionJob job = session.destruction.pendingDestroyJobs.removeFirst();
            session.destruction.destroyJobs.addLast(job);
            resumed.add(job);
        }

        for (DestructionJob job : resumed) {
            RtsWorkflowEngine.getInstance().from(player, job.workflowEntryId())
                    .ifPresent(token -> token.resume());
            RtsbuildingMod.LOGGER.info("[RtsDestructionBatch] {} resumed pending destroy job #{} ({} remaining)",
                    player.getGameProfile().getName(), job.workflowEntryId(), job.remainingCount());
        }

        if (!resumed.isEmpty()) {
            ServiceRegistry.getInstance().serviceOp().markDirty(player, session);
        }
    }

    // =========================================================================
    //  目标收集与验证
    // =========================================================================

    /**
     * 过滤给定的显式位置列表，返回可破坏的有效目标。
     * 按 Y 降序排列（从上往下破坏），去重，验证可达性/可破坏性/破坏速度。
     */
    private static Deque<BlockPos> collectAreaDestroyTargets(ServerPlayer player, List<BlockPos> positions,
            int toolSlot, ItemStack linkedTool, boolean selectedToolRequested, boolean creative) {
        if (player == null || positions == null || positions.isEmpty()) {
            return new ArrayDeque<>();
        }
        ServerLevel level = player.serverLevel();

        // 按 Y 降序排列（从上往下逐层破坏）
        List<BlockPos> sortedPositions = new ArrayList<>(positions);
        sortedPositions.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());

        LinkedHashSet<BlockPos> unique = new LinkedHashSet<>();
        for (BlockPos raw : sortedPositions) {
            if (raw == null || unique.size() >= C2SRtsAreaDestroyPayload.MAX_POSITIONS) {
                continue;
            }
            BlockPos pos = raw.immutable();
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!RtsMiningValidator.isBreakableBlock(state)
                    || !RtsMiningValidator.hasValidDestroySpeed(state, level, pos)) {
                continue;
            }
            if (!creative && MiningSpeedCalculator.computeRemoteDestroyStep(
                    player, state, pos, toolSlot, linkedTool, selectedToolRequested) <= 0.0F) {
                continue;
            }
            unique.add(pos);
        }
        return new ArrayDeque<>(unique);
    }

    // =========================================================================
    //  多方块附属追踪
    // =========================================================================

    /**
     * 捕获所有 6 个邻居的破坏前状态，用于多方块结构追踪（门、床、双高植物等）。
     */
    private static List<HistoryBlockRecord> captureNeighborRecords(ServerLevel level, BlockPos pos) {
        List<HistoryBlockRecord> records = new ArrayList<>(6);
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState state = level.getBlockState(neighbor);
            if (!state.isAir()) {
                records.add(new HistoryBlockRecord(neighbor.immutable(), state));
            }
        }
        return records;
    }

    /**
     * 方块被破坏后，检查哪些邻居位置变成了空气，
     * 并将它们添加到 job 的已记录位置中，以便包含在批次历史记录中。
     */
    private static void recordCollateralBlocks(ServerLevel level, DestructionJob job,
            List<HistoryBlockRecord> neighborRecords, BlockPos brokenPos) {
        for (HistoryBlockRecord nr : neighborRecords) {
            if (nr.pos().equals(brokenPos)) {
                continue;
            }
            BlockState currentState = level.getBlockState(nr.pos());
            if (currentState.isAir() && !nr.state().isAir()) {
                job.processedRecords.add(nr);
            }
        }
    }

    // =========================================================================
    //  DestructionJob —— 破坏作业
    // =========================================================================

    /**
     * 单个批处理破坏作业，持有共享的破坏参数和有序的目标位置列表。
     * 每个作业由 {@link #tickDestroyJobs} 以自适应每 tick 处理量节流处理。
     */
    public static final class DestructionJob {
        private final List<BlockPos> positions;
        private final byte toolSlot;
        private final boolean toolProtectionEnabled;
        private final boolean selectedToolRequested;
        private final int workflowEntryId;
        private final int totalTargets;
        private int index;

        /** 已成功破坏的位置（用于历史记录和工作流进度）。 */
        final List<BlockPos> destroyedPositions = new ArrayList<>();

        /** 因方块状态变更而跳过的数量（入队后可破坏，执行时已不满足条件），
         *  在 job 完成时报告为 failedBlocks，确保 completedBlocks + failedBlocks == totalTargets。 */
        int skippedWhileProcessing;

        /** 破坏前捕获的历史记录（含附属破坏）。 */
        final List<HistoryBlockRecord> processedRecords = new ArrayList<>();

        private DestructionJob(List<BlockPos> positions, byte toolSlot, boolean toolProtectionEnabled,
                boolean selectedToolRequested, int workflowEntryId, int totalTargets) {
            this.positions = positions;
            this.toolSlot = toolSlot;
            this.toolProtectionEnabled = toolProtectionEnabled;
            this.selectedToolRequested = selectedToolRequested;
            this.workflowEntryId = workflowEntryId;
            this.totalTargets = totalTargets;
        }

        // ── 索引管理 ──────────────────────────────────────────────────

        private boolean hasNext() {
            return this.index < this.positions.size();
        }

        int remainingCount() {
            return this.positions.size() - this.index;
        }

        private BlockPos next() {
            return this.positions.get(this.index++);
        }

        /** 回退索引，下个 tick 重试同一位置。 */
        void unconsumeLast() {
            if (this.index > 0) {
                this.index--;
            }
        }

        int totalCount() {
            return this.positions.size();
        }

        // ── 访问器 ─────────────────────────────────────────────────────

        public int workflowEntryId() {
            return this.workflowEntryId;
        }

        public byte toolSlot() {
            return this.toolSlot;
        }

        public boolean toolProtectionEnabled() {
            return this.toolProtectionEnabled;
        }

        public boolean selectedToolRequested() {
            return this.selectedToolRequested;
        }

        public int targetCount() {
            return this.totalTargets;
        }

        public List<BlockPos> destroyedPositions() {
            return java.util.Collections.unmodifiableList(this.destroyedPositions);
        }

        // ──────────────────────────────────────────────────────────
        //  NBT 序列化——用于会话持久化
        // ──────────────────────────────────────────────────────────

        private static final String NBT_POSITIONS = "positions";
        private static final String NBT_TOOL_SLOT = "toolSlot";
        private static final String NBT_TOOL_PROTECTION = "toolProtection";
        private static final String NBT_SELECTED_TOOL = "selectedTool";
        private static final String NBT_WORKFLOW_ENTRY_ID = "workflowEntryId";
        private static final String NBT_TOTAL_TARGETS = "totalTargets";
        private static final String NBT_INDEX = "index";

        /**
         * 将此破坏作业序列化为 {@link CompoundTag} 用于持久化存储。
         */
        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            long[] posArray = new long[positions.size()];
            for (int i = 0; i < positions.size(); i++) {
                posArray[i] = positions.get(i).asLong();
            }
            tag.putLongArray(NBT_POSITIONS, posArray);
            tag.putByte(NBT_TOOL_SLOT, toolSlot);
            tag.putBoolean(NBT_TOOL_PROTECTION, toolProtectionEnabled);
            tag.putBoolean(NBT_SELECTED_TOOL, selectedToolRequested);
            tag.putInt(NBT_WORKFLOW_ENTRY_ID, workflowEntryId);
            tag.putInt(NBT_TOTAL_TARGETS, totalTargets);
            tag.putInt(NBT_INDEX, index);
            return tag;
        }

        /**
         * 从 {@link CompoundTag} 反序列化 {@link DestructionJob}。
         */
        public static DestructionJob fromNbt(CompoundTag tag) {
            long[] posArray = tag.getLongArray(NBT_POSITIONS);
            List<BlockPos> positions = new ArrayList<>(posArray.length);
            for (long l : posArray) {
                positions.add(BlockPos.of(l));
            }
            byte toolSlot = tag.getByte(NBT_TOOL_SLOT);
            boolean toolProtectionEnabled = tag.getBoolean(NBT_TOOL_PROTECTION);
            boolean selectedToolRequested = tag.getBoolean(NBT_SELECTED_TOOL);
            int workflowEntryId = tag.getInt(NBT_WORKFLOW_ENTRY_ID);
            int totalTargets = tag.getInt(NBT_TOTAL_TARGETS);
            int index = tag.getInt(NBT_INDEX);

            DestructionJob job = new DestructionJob(
                    positions, toolSlot, toolProtectionEnabled,
                    selectedToolRequested, workflowEntryId, totalTargets);
            job.index = index;
            return job;
        }
    }
}
