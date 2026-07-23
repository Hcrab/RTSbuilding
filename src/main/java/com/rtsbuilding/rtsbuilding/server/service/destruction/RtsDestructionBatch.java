package com.rtsbuilding.rtsbuilding.server.service.destruction;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyPayload;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.mining.*;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionSliceResult;
import com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionTaskState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 远程范围破坏（AREA_DESTROY）的命令构造与单 slice 执行器。
 *
 * <p>本类把请求冻结为 {@link DestructionJob} / {@link DestructionTaskState}，并在任务引擎
 * 分配的数量与纳秒预算内执行；跨 tick 生命周期与等待状态只由 TaskStore 持有。
 *
 * <p>对齐 {@link com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch} 的架构：
 * Pipeline 只负责提交，实际处理由统一任务引擎调度。
 *
 * <p>不负责：工具借用（{@link com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe}）、
 * 协议进度初始化（{@link com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe}）。
 */
public final class RtsDestructionBatch {

    /** 单个 tick 中处理的最大破坏目标数，与范围放置 {@code BUILD_BATCH_MAX_BLOCKS_PER_TICK} 对齐。 */
    private static final int DESTROY_MAX_BLOCKS_PER_TICK = 64;

    /** 快速建造破坏的最大排队作业数。 */
    public static final int DESTROY_MAX_QUEUED_JOBS = 4;

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
        DestructionJob job = new DestructionJob(
                new ArrayList<>(targets),
                (byte) slot,
                toolProtectionEnabled,
                selectedToolRequested,
                workflowEntryId,
                targets.size());
        if (!com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                .submitDestructionJob(player, job)) return false;

        RtsbuildingMod.LOGGER.info("[RtsDestructionBatch] {} submitted {} destroy targets to TaskStore",
                player.getGameProfile().getName(), targets.size());
        return true;
    }

    // =========================================================================
    //  Tick 处理
    // =========================================================================

    /**
     * Tick 处理器，从排队的破坏作业中处理最多 {@link #DESTROY_MAX_BLOCKS_PER_TICK}
     * 个方块，实际处理量同时受全局任务数量预算与纳秒截止时间限制。
     *
     * <p>在处理前先尝试恢复挂起的破坏作业（{@link #tryResumePendingDestroyJobs}）。
     *
     * <p>当完整的作业完成时，记录历史、更新工作流进度、归还工具（如果是最后的作业）、
     * 刷新储存页面。
     */
    public static DestructionTaskState snapshotDetachedState(DestructionJob job) {
        if (job == null) throw new IllegalArgumentException("job 不能为空");
        List<CompoundTag> history = job.processedRecords.stream()
                .map(RtsDestructionBatch::encodeHistoryRecord)
                .toList();
        return new DestructionTaskState(
                job.positions,
                job.toolSlot,
                job.toolProtectionEnabled,
                job.selectedToolRequested,
                job.workflowEntryId,
                job.index,
                job.destroyedPositions.size(),
                job.skippedWhileProcessing,
                job.destroyedPositions,
                history);
    }

    /**
     * 在一个主线程预算片内推进 detached destruction。
     *
     * <p>本方法从纯值状态临时重建 DestructionJob，仅借用 player/session 完成世界、工具、
     * 掉落和 Capability 的真实事务。它不读取或修改 Session destruction 队列，不依赖对象
     * identity，也不直接管理 Workflow 生命周期；所有跨 tick 状态都通过返回值交回 TaskStore。</p>
     */
    public static DestructionSliceResult tickDetachedDestructionSlice(
            ServerPlayer player, RtsStorageSession session, DestructionTaskState state,
            int maxBlocks, long deadlineNanos) {
        if (player == null || session == null || state == null) {
            throw new IllegalArgumentException("player/session/state 不能为空");
        }
        if (state.complete()) {
            return new DestructionSliceResult(
                    state, 0, 0, 0, 0, DestructionSliceResult.Outcome.COMPLETE);
        }
        if (state.toolProtectionEnabled() && RtsMiningValidator.isToolNearBreak(player, session)) {
            return new DestructionSliceResult(
                    state, 0, 0, 0, 0, DestructionSliceResult.Outcome.WAITING_RESOURCE);
        }

        DestructionJob job = new DestructionJob(
                new ArrayList<>(state.targets()),
                state.toolSlot(),
                state.toolProtectionEnabled(),
                state.selectedToolRequested(),
                state.workflowEntryId(),
                state.totalUnits());
        job.index = state.cursorUnits();
        job.skippedWhileProcessing = state.failedUnits();

        int beforeCursor = job.index;
        int beforeFailed = job.skippedWhileProcessing;
        int limit = Math.max(0, Math.min(DESTROY_MAX_BLOCKS_PER_TICK, maxBlocks));
        int processed = 0;
        DestructionSliceResult.Outcome outcome = DestructionSliceResult.Outcome.CONTINUE;
        ServerLevel level = player.serverLevel();
        // 同一 slice 的掉落先合并进轻量缓存，避免每破坏一个方块都触发一次外部储存写入。
        List<BlockPos> dropsToAbsorb = new ArrayList<>();

        while (processed < limit && System.nanoTime() < deadlineNanos && job.hasNext()) {
            BlockPos target = job.next();
            processed++;

            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)
                    || !RtsClaimProtectionService.canBreakBlock(player, target, Direction.DOWN)) {
                job.skippedWhileProcessing++;
                continue;
            }
            BlockState blockState = level.getBlockState(target);
            if (!RtsMiningValidator.isBreakableBlock(blockState)
                    || !RtsMiningValidator.hasValidDestroySpeed(blockState, level, target)) {
                job.skippedWhileProcessing++;
                continue;
            }
            ItemStack linkedTool = session.mining.miningToolLease == null
                    ? ItemStack.EMPTY : session.mining.miningToolLease.stack();
            if (!player.isCreative()
                    && MiningSpeedCalculator.computeRemoteDestroyStep(
                            player, blockState, target, job.toolSlot(), linkedTool,
                            job.selectedToolRequested()) <= 0.0F) {
                job.skippedWhileProcessing++;
                continue;
            }

            HistoryBlockRecord preRecord = ServerHistoryManager.captureBlock(level, target);
            List<HistoryBlockRecord> neighborRecords = captureNeighborRecords(level, target);
            var result = RtsMiningStateMachine.destroyMinedBlock(
                    player, session, target, job.toolSlot());
            if (!result.broken()) {
                job.skippedWhileProcessing++;
                continue;
            }

            job.destroyedPositions.add(target);
            if (preRecord != null) job.processedRecords.add(preRecord);
            recordCollateralBlocks(level, job, neighborRecords, target);
            if (RtsMiningValidator.canAutoStoreDrops(player, session)) {
                dropsToAbsorb.add(target);
            }
            if (job.hasNext() && job.toolProtectionEnabled
                    && RtsMiningValidator.isToolNearBreak(player, session)) {
                outcome = DestructionSliceResult.Outcome.WAITING_RESOURCE;
                break;
            }
        }

        if (!dropsToAbsorb.isEmpty()) {
            RtsDropAbsorber.absorbMinedDropsBatch(player, session, dropsToAbsorb);
        }

        if (!job.hasNext()) outcome = DestructionSliceResult.Outcome.COMPLETE;

        List<BlockPos> destroyed = new ArrayList<>(state.destroyedPositions());
        destroyed.addAll(job.destroyedPositions);
        List<CompoundTag> history = new ArrayList<>(state.historyRecords());
        job.processedRecords.stream()
                .map(RtsDestructionBatch::encodeHistoryRecord)
                .forEach(history::add);
        int succeededDelta = job.destroyedPositions.size();
        int failedDelta = Math.max(0, job.skippedWhileProcessing - beforeFailed);
        int cursorDelta = Math.max(0, job.index - beforeCursor);
        DestructionTaskState next = state.advance(
                job.index,
                state.succeededUnits() + succeededDelta,
                job.skippedWhileProcessing,
                destroyed,
                history);
        return new DestructionSliceResult(
                next, processed, cursorDelta, succeededDelta, failedDelta, outcome);
    }

    /**
     * 把 detached task 已捕获的破坏前快照写入撤销历史。
     * 调用方只能在 Task 首次进入 terminal/cancel 边界时调用一次。
     */
    public static void recordDetachedHistory(ServerPlayer player, DestructionTaskState state) {
        if (player == null || state == null || state.historyRecords().isEmpty()) return;
        List<HistoryBlockRecord> records = new ArrayList<>();
        for (CompoundTag encoded : state.historyRecords()) {
            records.add(decodeHistoryRecord(player, encoded));
        }
        ServerHistoryManager.recordBreakWithRecords(player, records, Direction.DOWN);
    }

    /**
     * 归还 detached destruction 使用的工具租约。
     * 调用方必须先通过 TaskStore 确认该玩家已没有其它活跃拆除任务。
     */
    public static void returnDetachedDestroyTool(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null
                || session.mining.miningToolLease == null
                || session.mining.miningToolLease.isEmpty()) return;
        RtsToolLeaseManager.returnMiningTool(player, session, session.mining.miningToolLease);
        session.mining.miningToolLease = RtsToolLease.empty();
        session.mining.miningSelectedToolRequested = false;
        session.mining.workflowEntryId = -1;
        RtsbuildingMod.LOGGER.info("[RtsDestructionBatch] {} detached destroy tool returned",
                player.getGameProfile().getName());
    }

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
        List<BlockPos> harvestTierBlockedPositions = new ArrayList<>();
        ItemStack actualTool = RtsMiningValidator.resolveMiningTool(player, toolSlot, linkedTool);
        int maxRequiredLevel = RtsMiningValidator.rangeMiningMaxRequiredLevel(player, creative);
        for (BlockPos raw : sortedPositions) {
            if (raw == null || unique.size() >= C2SRtsAreaDestroyPayload.MAX_POSITIONS) {
                continue;
            }
            BlockPos pos = raw.immutable();
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                continue;
            }
            if (!RtsClaimProtectionService.canBreakBlock(player, pos, Direction.DOWN)) {
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
            if (!RtsMiningValidator.canRangeMineWithTool(
                    state, actualTool, creative, maxRequiredLevel)) {
                if (RtsMiningValidator.isBlockedByRangeMiningHarvestTier(
                        state, actualTool, creative, maxRequiredLevel)) {
                    harvestTierBlockedPositions.add(pos);
                }
                continue;
            }
            unique.add(pos);
        }
        if (!harvestTierBlockedPositions.isEmpty()) {
            RtsMiningNetworkHelper.notifyHarvestTierLimit(player, harvestTierBlockedPositions);
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

    private static CompoundTag encodeHistoryRecord(HistoryBlockRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", record.pos().asLong());
        tag.put("state", NbtUtils.writeBlockState(record.state()));
        if (record.blockEntityData() != null) {
            tag.put("blockEntity", record.blockEntityData().copy());
        }
        return tag;
    }

    private static HistoryBlockRecord decodeHistoryRecord(ServerPlayer player, CompoundTag tag) {
        BlockState state = NbtUtils.readBlockState(
                player.registryAccess().lookupOrThrow(Registries.BLOCK), tag.getCompound("state"));
        if (state.isAir()) throw new IllegalArgumentException("detached destruction history 方块状态无效");
        CompoundTag blockEntity = tag.contains("blockEntity", net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? tag.getCompound("blockEntity").copy() : null;
        return new HistoryBlockRecord(BlockPos.of(tag.getLong("pos")), state, blockEntity);
    }

    // =========================================================================
    //  DestructionJob —— 破坏作业
    // =========================================================================

    /**
     * 单个批处理破坏作业，持有共享的破坏参数和有序的目标位置列表。
     * 每个作业由 {@link #tickDestroyJobs} 以数量与纳秒双预算节流处理。
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

        public int remainingCount() {
            return this.positions.size() - this.index;
        }

        private BlockPos next() {
            return this.positions.get(this.index++);
        }

        public int totalCount() {
            return this.positions.size();
        }

        public int getIndex() {
            return this.index;
        }

        public int successfulCount() {
            return this.destroyedPositions.size();
        }

        public int failedCount() {
            return this.skippedWhileProcessing;
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
