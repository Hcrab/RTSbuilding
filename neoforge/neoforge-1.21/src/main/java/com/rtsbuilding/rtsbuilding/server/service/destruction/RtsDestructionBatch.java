package com.rtsbuilding.rtsbuilding.server.service.destruction;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsBatchJobTickOps;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.service.mining.*;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;

import java.util.*;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;

/**
 * Batch area destruction job manager, responsible for queuing and per-tick throttling of remote area destruction (AREA_DESTROY).
 *
 * <p>Manages the complete lifecycle of destruction jobs: queues area destruction requests as {@link DestructionJob},
 * processes them with adaptive per-tick throttling via {@link #tickDestroyJobs},
 * and handles job pause/resume/completion flow.
 *
 * <p>Aligned with {@link com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch} architecture:
 * Pipeline only handles enqueuing, actual processing is dispatched uniformly through
 * {@link com.rtsbuilding.rtsbuilding.server.service.ServerTickOrchestrator} with asyncCompletion lifecycle.
 *
 * <p>Not responsible for: tool borrowing ({@link com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe}),
 * protocol progress initialization ({@link com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe}).
 */
public final class RtsDestructionBatch {

    /** Maximum destruction targets processed per tick, aligned with area placement {@code BUILD_BATCH_MAX_BLOCKS_PER_TICK}. */
    private static final int DESTROY_MAX_BLOCKS_PER_TICK = 64;

    /** Maximum queued jobs for quick-build destruction. */
    private static final int DESTROY_MAX_QUEUED_JOBS = 4;

    private RtsDestructionBatch() {
    }

    // =========================================================================
    //  Enqueue
    // =========================================================================

    /**
     * Queues an area destruction request as a pending {@link DestructionJob}.
     *
     * <p>Both creative and survival modes are queued as jobs, processed asynchronously tick by tick starting from the next tick.
     * Quick-build destruction (shape preview) is limited by {@link #DESTROY_MAX_QUEUED_JOBS}.
     *
     * @return {@code true} if the job was queued; {@code false} if there are no valid targets
     */
    public static boolean enqueueDestroyBatch(ServerPlayer player, RtsStorageSession session,
            List<BlockPos> positions, byte toolSlot, boolean toolProtectionEnabled,
            int workflowEntryId) {
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

        // Collect and validate targets
        Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, linkedTool,
                selectedToolRequested, creative);
        if (targets.isEmpty()) {
            return false;
        }

        // Quick-build destruction limit: at most DESTROY_MAX_QUEUED_JOBS queued jobs
        // Both creative and survival modes use the same per-tick async queue processing
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
    //  Tick Processing
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

        // First attempt to resume pending destruction jobs (tool repair or replacement)
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

        // Record destroyed count before this tick for each job, for per-job independent workflow progress update
        Map<Integer, Integer> destroyedBeforeTick = new HashMap<>();
        List<DestructionJob> fullyCompletedJobs = new ArrayList<>();
        for (DestructionJob j : session.destruction.destroyJobs) {
            destroyedBeforeTick.put(j.workflowEntryId(), j.destroyedPositions.size());
        }

        var pausedJobsSkipped = new RtsBatchJobTickOps.MutableInt(0);
        ServerLevel level = player.serverLevel();

        while (remaining > 0 && !session.destruction.destroyJobs.isEmpty()) {
            DestructionJob job = session.destruction.destroyJobs.peekFirst();

            // ── Workflow state check ──────────────────────────────────────
            var checkResult = RtsBatchJobTickOps.checkPausedOrCancelled(
                    session.destruction.destroyJobs, job, player,
                    DestructionJob::workflowEntryId, pausedJobsSkipped);
            if (checkResult == null) {
                break; // All remaining jobs are paused
            }
            if (checkResult.isEmpty()) {
                continue; // This job was skipped (cancelled or paused)
            }
            var tokenOpt = Optional.ofNullable(checkResult.get().token());

            // ── Tool durability check ────────────────────────────────────────
            if (job.toolProtectionEnabled && RtsMiningValidator.isToolNearBreak(player, session)) {
                // Tool is about to break, suspend to pendingDestroyJobs
                session.destruction.destroyJobs.removeFirst();
                session.destruction.pendingDestroyJobs.addLast(job);
                tokenOpt.get().suspend();
                RtsbuildingMod.LOGGER.info("[RtsDestructionBatch] {} tool near break, suspending destroy job #{}",
                        player.getGameProfile().getName(), job.workflowEntryId());
                break;
            }

            // ── Process blocks ────────────────────────────────────────────
            boolean madeProgress = false;
            while (remaining > 0 && job.hasNext()) {
                BlockPos target = job.next();

                // Verification: world reachability
                if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
                    job.skippedWhileProcessing++;
                    continue;
                }
                BlockState state = level.getBlockState(target);
                // Verification: breakable + valid destroy speed
                if (!RtsMiningValidator.isBreakableBlock(state)
                        || !RtsMiningValidator.hasValidDestroySpeed(state, level, target)) {
                    job.skippedWhileProcessing++;
                    continue;
                }
                // Verification: tool can make progress on it (creative can destroy any block, skip this check)
                if (!player.isCreative() && MiningSpeedCalculator.computeRemoteDestroyStep(player, state, target,
                        job.toolSlot(),
                        session.mining.miningToolLease != null ? session.mining.miningToolLease.stack() : ItemStack.EMPTY,
                        job.selectedToolRequested()) <= 0.0F) {
                    job.skippedWhileProcessing++;
                    continue;
                }

                // Capture history snapshot before breaking
                HistoryBlockRecord preRecord = ServerHistoryManager.captureBlock(level, target);
                List<HistoryBlockRecord> neighborRecords = captureNeighborRecords(level, target);

                // Execute destruction
                var result = RtsMiningStateMachine.destroyMinedBlock(player, session, target, job.toolSlot());

                if (result.broken()) {
                    job.destroyedPositions.add(target);
                    if (preRecord != null) {
                        job.processedRecords.add(preRecord);
                    }
                    // Record collateral destruction (multiblock structures)
                    recordCollateralBlocks(level, job, neighborRecords, target);

                    // Absorb drops
                    if (RtsMiningValidator.canAutoStoreDrops(player, session)) {
                        RtsDropAbsorber.absorbMinedDropsImmediately(player, session, target);
                    }

                    madeProgress = true;

                    // Check tool durability again after breaking
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
                    // destroyMinedBlock failed to break this block (tool broken, block changed, etc.), count as failure
                    job.skippedWhileProcessing++;
                }
                remaining--;
            }

            // ── Job completion detection ─────────────────────────────────────────
            if (!session.destruction.destroyJobs.isEmpty()
                    && session.destruction.destroyJobs.peekFirst() == job
                    && !job.hasNext()) {
                session.destruction.destroyJobs.removeFirst();
                fullyCompletedJobs.add(job);
            }
        }

        // ── Process jobs completed this tick ────────────────────────────────
        RtsBatchJobTickOps.processCompletedJobs(
                player, session,
                fullyCompletedJobs, destroyedBeforeTick,
                DestructionJob::workflowEntryId,
                j -> j.destroyedPositions.size(),
                j -> j.skippedWhileProcessing,
                (p, job) -> {
                    // 使用破坏前捕获的 records（destroyedPositions 是破坏后的位置，
                    // 事后重新捕获会得到空气状态，撤销记录将为空）
                    if (!job.processedRecords.isEmpty()) {
                        ServerHistoryManager.recordBreakWithRecords(p, job.processedRecords, Direction.DOWN);
                    }
                },
                (p, job) -> RtsbuildingMod.LOGGER.info("[RtsDestructionBatch] {} completed destroy job #{} ({} destroyed)",
                        p.getGameProfile().getName(), job.workflowEntryId(), job.destroyedPositions.size()));

        // ── Update mid-progress ────────────────────────────────────────────
        RtsBatchJobTickOps.updateMidProgress(
                player, session,
                session.destruction.destroyJobs, destroyedBeforeTick,
                DestructionJob::workflowEntryId,
                j -> j.destroyedPositions.size());

        // ── Return tool when jobs are consumed / no more jobs ──────────────────────
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
    //  Pending Job Recovery
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
            RtsServer.get().serviceOp().markDirty(player, session);
        }
    }

    // =========================================================================
    //  Target Collection & Validation
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

        // Sort by Y descending (destroy from top down)
        List<BlockPos> sortedPositions = new ArrayList<>(positions);
        sortedPositions.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());

        LinkedHashSet<BlockPos> unique = new LinkedHashSet<>();
        for (BlockPos raw : sortedPositions) {
            if (raw == null || unique.size() >= NetworkConstants.MAX_POSITIONS) {
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
    //  Multi-block Collateral Tracking
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
    //  DestructionJob
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

        /** Successfully destroyed positions (for history and workflow progress). */
        final List<BlockPos> destroyedPositions = new ArrayList<>();

        /** Skips due to block state changes (breakable at enqueue time, no longer satisfies conditions at execution),
         *  reported as failedBlocks at job completion, ensuring completedBlocks + failedBlocks == totalTargets. */
        int skippedWhileProcessing;

        /** Pre-break captured history records (including collateral destruction). */
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

        // ── Index management ──────────────────────────────────────────────────

        private boolean hasNext() {
            return this.index < this.positions.size();
        }

        int remainingCount() {
            return this.positions.size() - this.index;
        }

        private BlockPos next() {
            return this.positions.get(this.index++);
        }

        /** Roll back index to retry same position on the next tick. */
        void unconsumeLast() {
            if (this.index > 0) {
                this.index--;
            }
        }

        int totalCount() {
            return this.positions.size();
        }

        // ── Accessors ─────────────────────────────────────────────────────

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
        //  NBT Serialization — for session persistence
        // ──────────────────────────────────────────────────────────

        private static final String NBT_POSITIONS = "positions";
        private static final String NBT_TOOL_SLOT = "toolSlot";
        private static final String NBT_TOOL_PROTECTION = "toolProtection";
        private static final String NBT_SELECTED_TOOL = "selectedTool";
        private static final String NBT_WORKFLOW_ENTRY_ID = "workflowEntryId";
        private static final String NBT_TOTAL_TARGETS = "totalTargets";
        private static final String NBT_INDEX = "index";

        /**
         * Serializes this destruction job to a {@link CompoundTag} for persistent storage.
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
         * Deserializes a {@link DestructionJob} from a {@link CompoundTag}.
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
