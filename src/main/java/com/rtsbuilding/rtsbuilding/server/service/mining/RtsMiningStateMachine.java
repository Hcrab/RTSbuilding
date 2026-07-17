package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.WorkflowPipeline;
import com.rtsbuilding.rtsbuilding.server.pipeline.sync.HistoryRecordPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolReturnPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.server.task.mining.MiningSliceResult;
import com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskCodec;
import com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState;
import com.rtsbuilding.rtsbuilding.server.task.mining.MiningWaitHint;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * State machine for single-block remote mining progress.
 *
 * <p>This class owns the per-tick accumulation loop
 * ({@link #tickActiveMining}) and the low-level block-destruction helpers
 * ({@link #destroyMinedBlock}, {@link #computeRemoteDestroyStep}).  Every
 * method is stateless — all mutable state lives in
 * {@link RtsStorageSession}.</p>
 *
 * <p><b>Improvements over the monolithic original:</b>
 * <ul>
 *   <li>Waterlogged blocks are no longer incorrectly excluded.</li>
 *   <li>Multi-block structures (doors, beds, double-plants) that are
 *       collateral-destroyed by vanilla are now tracked for history.</li>
 *   <li>Temporary context-switching helpers are kept package-private.</li>
 * </ul>
 */
public final class RtsMiningStateMachine {

    /**
     * 工作流条目 ID 现在存储在 {@link com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningState#workflowEntryId}
     * 中，而非使用独立的静态 WORKFLOW_ENTRY_IDS 映射。
     * 消除了两套平行追踪系统导致的不一致风险。
     */

    private RtsMiningStateMachine() {
    }

    // =========================================================================
    //  Mining Job Queue
    // =========================================================================

    /**
     * A single queued mining operation (independent thread) waiting to be
     * activated when the current operation finishes.
     *
     * <p>Analogous to {@link com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch.PlaceBatchJob}
     * but for mining operations.  Each job carries its own workflow entry ID
     * and target positions so multiple range-mining tasks can coexist in a
     * FIFO queue.</p>
     *
     * @param workflowEntryId  the workflow entry tracking this job's progress
     * @param targets          the block positions to destroy
     * @param totalTargets     total number of targets before validation losses
     */
    public record MiningJob(int workflowEntryId, Deque<BlockPos> targets, int totalTargets) {
    }

    /** 单次任务调度片产生的真实挖掘结果。 */
    public record MiningAdvance(
            int processedUnits,
            int succeededUnits,
            int failedUnits,
            boolean operationEnded,
            boolean waitingForBuffer) {
        public static MiningAdvance idle() {
            return new MiningAdvance(0, 0, 0, false, false);
        }

        public static MiningAdvance ended(int processed, int succeeded, int failed) {
            return new MiningAdvance(processed, succeeded, failed, true, false);
        }

        public static MiningAdvance bufferBlocked() {
            return new MiningAdvance(0, 0, 0, false, true);
        }

        public MiningAdvance plus(MiningAdvance other) {
            return new MiningAdvance(
                    processedUnits + other.processedUnits,
                    succeededUnits + other.succeededUnits,
                    failedUnits + other.failedUnits,
                    operationEnded || other.operationEnded,
                    waitingForBuffer || other.waitingForBuffer);
        }
    }

    /**
     * Activates the next queued mining job by loading its data into the
     * session's active mining state fields and updating the workflow entry
     * ID tracking map.
     */
    public static void tickActiveMining(ServerPlayer player, RtsStorageSession session) {
        tickActiveMining(player, session, RtsMiningValidator.ultimineBlocksPerTick(), Long.MAX_VALUE);
    }

    /** 在统一任务引擎分配的双预算内推进挖掘。 */
    public static MiningAdvance tickActiveMining(ServerPlayer player, RtsStorageSession session,
            int maxUnits, long deadlineNanos) {
        if (session.miningDropBuffer.isFull()) {
            return MiningAdvance.bufferBlocked();
        }
        // 工作流是否仍存在由 Task Engine 执行器校验；暂停状态只由 TaskRecord 决定。
        int entryId = session.mining.workflowEntryId;
        if (entryId >= 0) {
            var tokenOpt = RtsWorkflowEngine.getInstance().from(player, entryId);
            if (tokenOpt.isEmpty()) {
                RtsbuildingMod.LOGGER.warn("[RtsMiningStateMachine] tickActiveMining: workflow token not found for entryId={}, stopping for {}", entryId, player.getGameProfile().getName());
                // 工作流已被关闭（删除），停止挖掘操作
                cancelMiningTask(player, session, entryId);
                return MiningAdvance.ended(0, 0, 0);
            }
        }

        if (session.mining.miningPos == null) {
            if (!session.mining.ultimineTargets.isEmpty()) {
                return RtsUltimineProcessor.processUltimineTargets(player, session, maxUnits, deadlineNanos);
            }
            return MiningAdvance.ended(0, 0, 0);
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, session.mining.miningPos)) {
            stopCurrentMiningTask(player, session);
            return MiningAdvance.ended(1, 0, 1);
        }
        if (!RtsClaimProtectionService.canBreakBlock(player, session.mining.miningPos, session.mining.miningFace)) {
            stopCurrentMiningTask(player, session);
            return MiningAdvance.ended(1, 0, 1);
        }

        ServerLevel level = player.level();
        BlockPos pos = session.mining.miningPos;
        BlockState state = level.getBlockState(pos);
        // FIXED: No longer incorrectly excludes waterlogged blocks
        if (!RtsMiningValidator.isBreakableBlock(state)
                || !RtsMiningValidator.hasValidDestroySpeed(state, level, pos)) {
            stopCurrentMiningTask(player, session);
            return MiningAdvance.ended(1, 0, 1);
        }
        if (RtsMiningValidator.isToolNearBreak(player, session)) {
            stopCurrentMiningTask(player, session);
            return MiningAdvance.ended(1, 0, 1);
        }

        float step = MiningSpeedCalculator.computeRemoteDestroyStep(player, state, pos, session.mining.miningToolSlot,
                session.mining.miningToolLease.stack(), session.mining.miningSelectedToolRequested);
        if (step <= 0.0F) {
            return MiningAdvance.idle();
        }

        session.mining.miningProgress += step;
        if (session.mining.miningProgress < 1.0F) {
            int stage = RtsMiningValidator.visibleMiningStage(session.mining.miningProgress);
            if (stage != session.mining.miningStage) {
                level.destroyBlockProgress(player.getId(), pos, stage);
                RtsMiningNetworkHelper.sendMineProgress(player, pos, stage);
                session.mining.miningStage = stage;
            }
            return MiningAdvance.idle();
        }

        // --- Progress complete: break the block ---

        // Capture before-state for history (must be done before destroy)
        HistoryBlockRecord preRecord = ServerHistoryManager.captureBlock(player.level(), pos);
        // Also capture neighbor states for multi-block tracking
        List<HistoryBlockRecord> neighborRecords = MultiBlockTracker.captureNeighborRecords(player.level(), pos);

        MiningBreakResult result = destroyMinedBlock(player, session, pos, session.mining.miningToolSlot);
        level.destroyBlockProgress(player.getId(), pos, -1);

        if (result.broken() && !session.mining.ultimineTargets.isEmpty()) {
            // Part of an ultimine batch — advance to next target
            removeUltimineTarget(session, pos);
            session.mining.ultimineProcessedTargets = Math.max(session.mining.ultimineProcessedTargets, 1);
            session.mining.ultimineBrokenTargets++;
            if (preRecord != null) {
                session.mining.ultimineProcessedPositions.add(preRecord);
            }
            // Record any collateral blocks (multi-block structures)
            MultiBlockTracker.recordCollateralBlocks(player.level(), session, neighborRecords, pos);
            reportWorkflowResult(player, session, 1, 0);
            // 连锁挖掘中途进度：只延迟标脏，避免每方块一次同步刷新储存页。
            // 完整的 afterModification（含页面刷新和持久化）在 finalizeMiningOperation 中执行
            session.mining.miningPos = null;
            session.mining.miningProgress = 0.0F;
            session.mining.miningStage = -1;
            MiningAdvance tail = RtsUltimineProcessor.processUltimineTargets(
                    player, session, Math.max(0, maxUnits - 1), deadlineNanos);
            return new MiningAdvance(1, 1, 0, false, false).plus(tail);
        }

        // Single-block mode — finish
        RtsMiningNetworkHelper.clearMineProgress(player, pos);
        List<HistoryBlockRecord> miningRecords = new ArrayList<>();
        if (result.broken()) {
            if (preRecord != null) {
                miningRecords.add(preRecord);
            }
            // Add any collateral blocks
            for (HistoryBlockRecord nr : neighborRecords) {
                BlockState currentState = player.level().getBlockState(nr.pos());
                if (currentState.isAir() && !nr.state().isAir()) {
                    miningRecords.add(nr);
                }
            }
        }
        reportWorkflowResult(player, session, result.broken() ? 1 : 0, result.broken() ? 0 : 1);
        finalizeMiningOperation(player, session, miningRecords, session.mining.miningFace);
        return MiningAdvance.ended(1, result.broken() ? 1 : 0, result.broken() ? 0 : 1);
    }

    /**
     * 将当前活动挖掘冻结成纯值状态；后续 detached slice 不再读取这些 Session cursor 字段。
     */
    public static MiningTaskState snapshotDetachedActive(RtsStorageSession session) {
        if (session == null) throw new IllegalArgumentException("session 不能为空");
        var mining = session.mining;
        List<BlockPos> remaining = new ArrayList<>();
        MiningTaskState.Mode mode;
        if (mining.miningPos != null) {
            mode = MiningTaskState.Mode.PROGRESSIVE_SINGLE;
            remaining.add(mining.miningPos.immutable());
            for (BlockPos target : mining.ultimineTargets) {
                if (!target.equals(mining.miningPos)) remaining.add(target.immutable());
            }
        } else {
            mode = MiningTaskState.Mode.BATCH;
            mining.ultimineTargets.stream().map(BlockPos::immutable).forEach(remaining::add);
        }
        if (remaining.isEmpty()) throw new IllegalArgumentException("当前 Session 没有活动挖掘目标");
        int cursor = Math.max(0, mining.ultimineProcessedTargets);
        int succeeded = Math.max(0, Math.min(cursor, mining.ultimineBrokenTargets));
        int total = Math.max(cursor + remaining.size(), Math.max(1, mining.ultimineTotalTargets));
        List<net.minecraft.nbt.CompoundTag> history = mining.ultimineProcessedPositions.stream()
                .map(MiningTaskCodec::encodeHistory).toList();
        return new MiningTaskState(
                mode, mining.workflowEntryId, remaining,
                total, cursor, succeeded, Math.max(0, cursor - succeeded),
                mining.miningFace, mining.miningToolSlot,
                mining.miningSelectedToolRequested, mining.miningToolProtectionEnabled,
                Math.max(0.0F, Math.min(0.999999F, mining.miningProgress)),
                Math.max(-1, Math.min(9, mining.miningStage)), history);
    }

    /** 将旧队列中的一个 MiningJob 冻结成独立 BATCH task。 */
    public static MiningTaskState snapshotDetachedQueued(
            RtsStorageSession session, MiningJob job) {
        if (session == null || job == null || job.targets() == null || job.targets().isEmpty()) {
            throw new IllegalArgumentException("session/job 不能为空且必须包含目标");
        }
        return new MiningTaskState(
                MiningTaskState.Mode.BATCH,
                job.workflowEntryId(),
                job.targets().stream().map(BlockPos::immutable).toList(),
                Math.max(job.totalTargets(), job.targets().size()),
                0, 0, 0,
                session.mining.miningFace,
                session.mining.miningToolSlot,
                session.mining.miningSelectedToolRequested,
                session.mining.miningToolProtectionEnabled,
                0.0F, -1, List.of());
    }

    /**
     * 推进一个不依赖 Session mining cursor/queue 的主线程调度片。
     * Session 只提供真实工具租约、库存、掉落缓冲和 Capability；目标与生命周期只读写返回 snapshot。
     */
    public static MiningSliceResult tickDetachedMiningSlice(
            ServerPlayer player, RtsStorageSession session, MiningTaskState state,
            int maxUnits, long deadlineNanos) {
        if (player == null || session == null || state == null) {
            throw new IllegalArgumentException("player/session/state 不能为空");
        }
        if (state.complete()) {
            return new MiningSliceResult(state, 0, 0, 0, 0,
                    MiningSliceResult.Outcome.COMPLETE, null);
        }
        if (session.miningDropBuffer.isFull()) {
            return new MiningSliceResult(state, 0, 0, 0, 0,
                    MiningSliceResult.Outcome.WAITING, MiningWaitHint.buffer());
        }

        Direction originalFace = session.mining.miningFace;
        int originalToolSlot = session.mining.miningToolSlot;
        boolean originalSelectedTool = session.mining.miningSelectedToolRequested;
        boolean originalToolProtection = session.mining.miningToolProtectionEnabled;
        session.mining.miningFace = state.face();
        session.mining.miningToolSlot = state.toolSlot();
        session.mining.miningSelectedToolRequested = state.selectedToolRequested();
        session.mining.miningToolProtectionEnabled = state.toolProtectionEnabled();
        try {
            return executeDetachedMiningSlice(player, session, state, maxUnits, deadlineNanos);
        } finally {
            // 只恢复借用的运行环境；destroyMinedBlock 对真实 tool lease 的损耗必须保留。
            session.mining.miningFace = originalFace;
            session.mining.miningToolSlot = originalToolSlot;
            session.mining.miningSelectedToolRequested = originalSelectedTool;
            session.mining.miningToolProtectionEnabled = originalToolProtection;
        }
    }

    private static MiningSliceResult executeDetachedMiningSlice(
            ServerPlayer player, RtsStorageSession session, MiningTaskState state,
            int maxUnits, long deadlineNanos) {
        List<BlockPos> remaining = new ArrayList<>(state.remainingTargets());
        // 热路径保留已经编码的历史，只为本批新增记录编码一次；完整解码延后到终态。
        List<net.minecraft.nbt.CompoundTag> history = new ArrayList<>();
        state.appendFrozenHistoryTo(history);
        MiningTaskState.Mode mode = state.mode();
        int processed = 0;
        int succeeded = 0;
        int failed = 0;
        float progress = state.blockProgress();
        int stage = state.visibleStage();
        int unitLimit = Math.max(0, Math.min(RtsMiningValidator.ultimineBlocksPerTick(), maxUnits));
        MiningWaitHint waitHint = null;
        boolean deferUntilNextTick = false;

        if (mode == MiningTaskState.Mode.PROGRESSIVE_SINGLE && !remaining.isEmpty()
                && unitLimit > 0 && System.nanoTime() < deadlineNanos) {
            BlockPos target = remaining.getFirst();
            if (!player.level().hasChunkAt(target)) {
                waitHint = MiningWaitHint.chunk(player.level().dimension(), target);
            } else if (RtsMiningValidator.isToolNearBreak(player, session)) {
                waitHint = MiningWaitHint.tool();
            } else if (!canDetachedMineTarget(player, target, state.face())) {
                clearDetachedProgress(player, target);
                remaining.removeFirst();
                processed++;
                failed++;
                progress = 0.0F;
                stage = -1;
            } else {
                BlockState targetState = player.level().getBlockState(target);
                float step = MiningSpeedCalculator.computeRemoteDestroyStep(
                        player, targetState, target, state.toolSlot(),
                        session.mining.miningToolLease.stack(), state.selectedToolRequested());
                if (step <= 0.0F) {
                    waitHint = MiningWaitHint.tool();
                } else {
                    progress += step;
                    if (progress < 1.0F) {
                        deferUntilNextTick = true;
                        int nextStage = RtsMiningValidator.visibleMiningStage(progress);
                        if (nextStage != stage) {
                            player.level().destroyBlockProgress(player.getId(), target, nextStage);
                            RtsMiningNetworkHelper.sendMineProgress(player, target, nextStage);
                            stage = nextStage;
                        }
                    } else {
                        boolean broken = destroyDetachedTarget(player, session, target, history);
                        clearDetachedProgress(player, target);
                        remaining.removeFirst();
                        processed++;
                        if (broken) {
                            succeeded++;
                        } else {
                            failed++;
                        }
                        progress = 0.0F;
                        stage = -1;
                        if (!remaining.isEmpty()) mode = MiningTaskState.Mode.BATCH;
                    }
                }
            }
            if (processed > 0 && !remaining.isEmpty()) {
                // 当前渐进目标已经消费；同一任务的其余连锁目标进入批处理模式。
                mode = MiningTaskState.Mode.BATCH;
            }
        }

        while (waitHint == null && mode == MiningTaskState.Mode.BATCH
                && processed < unitLimit && System.nanoTime() < deadlineNanos && !remaining.isEmpty()) {
            if (RtsMiningValidator.isToolNearBreak(player, session)) {
                waitHint = MiningWaitHint.tool();
                break;
            }
            BlockPos target = remaining.getFirst();
            if (!player.level().hasChunkAt(target)) {
                waitHint = MiningWaitHint.chunk(player.level().dimension(), target);
                break;
            }
            if (!canDetachedMineTarget(player, target, state.face())) {
                remaining.removeFirst();
                processed++;
                failed++;
                continue;
            }
            BlockState targetState = player.level().getBlockState(target);
            float step = MiningSpeedCalculator.computeRemoteDestroyStep(
                    player, targetState, target, state.toolSlot(),
                    session.mining.miningToolLease.stack(), state.selectedToolRequested());
            if (step <= 0.0F) {
                waitHint = MiningWaitHint.tool();
                break;
            }
            boolean broken = destroyDetachedTarget(player, session, target, history);
            remaining.removeFirst();
            processed++;
            if (broken) {
                succeeded++;
            } else {
                failed++;
            }
        }

        int nextCursor = state.cursorUnits() + processed;
        int nextSucceeded = state.succeededUnits() + succeeded;
        int nextFailed = state.failedUnits() + failed;
        MiningTaskState next = state.next(
                remaining.isEmpty() ? MiningTaskState.Mode.BATCH : mode,
                remaining, nextCursor, nextSucceeded, nextFailed, progress, stage, history);

        MiningSliceResult.Outcome outcome;
        if (waitHint != null) outcome = MiningSliceResult.Outcome.WAITING;
        else if (next.complete()) outcome = MiningSliceResult.Outcome.COMPLETE;
        else if (deferUntilNextTick) outcome = MiningSliceResult.Outcome.NEXT_TICK;
        else outcome = MiningSliceResult.Outcome.CONTINUE;
        if (outcome == MiningSliceResult.Outcome.COMPLETE) {
            finalizeDetachedMining(player, session, decodeDetachedHistory(player, history), state.face());
        }
        return new MiningSliceResult(next, processed, processed, succeeded, failed, outcome, waitHint);
    }

    private static boolean canDetachedMineTarget(ServerPlayer player, BlockPos target, Direction face) {
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)
                || !RtsClaimProtectionService.canBreakBlock(player, target, face)) return false;
        BlockState targetState = player.level().getBlockState(target);
        return RtsMiningValidator.isBreakableBlock(targetState)
                && RtsMiningValidator.hasValidDestroySpeed(targetState, player.level(), target);
    }

    private static boolean destroyDetachedTarget(
            ServerPlayer player, RtsStorageSession session, BlockPos target,
            List<net.minecraft.nbt.CompoundTag> history) {
        HistoryBlockRecord before = ServerHistoryManager.captureBlock(player.level(), target);
        List<HistoryBlockRecord> neighbors = MultiBlockTracker.captureNeighborRecords(player.level(), target);
        MiningBreakResult result = destroyMinedBlock(player, session, target, session.mining.miningToolSlot);
        if (!result.broken()) return false;
        if (before != null) history.add(MiningTaskCodec.encodeHistory(before));
        for (HistoryBlockRecord neighbor : neighbors) {
            if (!neighbor.pos().equals(target)
                    && player.level().getBlockState(neighbor.pos()).isAir()
                    && !neighbor.state().isAir()) {
                history.add(MiningTaskCodec.encodeHistory(neighbor));
            }
        }
        return true;
    }

    private static void clearDetachedProgress(ServerPlayer player, BlockPos target) {
        player.level().destroyBlockProgress(player.getId(), target, -1);
        RtsMiningNetworkHelper.clearMineProgress(player, target);
    }

    private static void finalizeDetachedMining(
            ServerPlayer player, RtsStorageSession session,
            List<HistoryBlockRecord> history, Direction face) {
        if (!history.isEmpty()) ServerHistoryManager.recordBreakWithRecords(player, history, face);
        if (session.mining.miningToolLease != null && !session.mining.miningToolLease.isEmpty()) {
            RtsToolLeaseManager.returnMiningTool(player, session, session.mining.miningToolLease);
            session.mining.miningToolLease = RtsToolLease.empty();
        }
        RtsEffectAccumulator.INSTANCE.markStorageViewDirty(player.getUUID(), player.level().dimension());
        RtsEffectAccumulator.INSTANCE.markPersistence(player.getUUID(), player.level().dimension());
    }

    private static List<HistoryBlockRecord> decodeDetachedHistory(
            ServerPlayer player, List<net.minecraft.nbt.CompoundTag> history) {
        return history.stream()
                .map(tag -> MiningTaskCodec.decodeHistory(player.registryAccess(), tag))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /** 取消 detached mining 时仅收尾已经发生的历史与工具租约，不再读取 Session cursor。 */
    public static void finalizeDetachedCancellation(
            ServerPlayer player, RtsStorageSession session, MiningTaskState state) {
        if (player == null || session == null || state == null) return;
        if (state.mode() == MiningTaskState.Mode.PROGRESSIVE_SINGLE
                && !state.remainingTargets().isEmpty()) {
            clearDetachedProgress(player, state.remainingTargets().getFirst());
        }
        List<HistoryBlockRecord> history = decodeDetachedHistory(player, state.historyRecords());
        finalizeDetachedMining(player, session, history, state.face());
    }

    /** 将真实成功/失败投影到工作流，网络发送由 Tick 末合并。 */
    private static void reportWorkflowResult(ServerPlayer player, RtsStorageSession session,
            int succeeded, int failed) {
        int entryId = session.mining.workflowEntryId;
        if (entryId < 0) return;
        RtsWorkflowEngine.getInstance().from(player, entryId).ifPresent(token -> {
            if (succeeded > 0) token.updateProgress(succeeded, null);
            if (failed > 0) token.recordFailures(failed);
        });
    }

    // =========================================================================
    //  Stop
    // =========================================================================

    /**
     * Stops all active mining/ultimine activity for the given session,
     * clears break-stage particles on the client, returns the borrowed tool,
     * and resets the session's mining state.
     *
     * @param preserveEntry if {@code true}, the workflow entry is <b>not</b>
     *                       cancelled — only the runtime mining state is
     *                       cleared. Use when the entry was already paused
     *                       (e.g. RTS mode disabled) and should remain
     *                       visible in the UI.
     */
    public static void stopActiveMining(ServerPlayer player, RtsStorageSession session, boolean preserveEntry) {
        RtsbuildingMod.LOGGER.info("[RtsMiningStateMachine] stopActiveMining: player={}, miningPos={}, ultimineTargets={}, preserveEntry={}",
                player.getGameProfile().getName(),
                session.mining.miningPos,
                session.mining.ultimineTargets.size(),
                preserveEntry);
        boolean hadMiningState = session.mining.miningPos != null
                || session.mining.ultimineProgressPos != null
                || !session.mining.ultimineTargets.isEmpty()
                || !session.mining.miningToolLease.isEmpty();
        boolean hadUltimine = session.mining.ultimineProgressPos != null || !session.mining.ultimineTargets.isEmpty();

        // Complete workflow tracking via entry ID if single-block mining was active
        BlockPos progressPos = session.mining.miningPos != null ? session.mining.miningPos : session.mining.ultimineProgressPos;
        if (progressPos != null) {
            player.level().destroyBlockProgress(player.getId(), progressPos, -1);
            RtsMiningNetworkHelper.sendMineProgress(player, progressPos, -1);
        }
        if (hadUltimine) {
            RtsMiningNetworkHelper.sendUltimineProgress(player, -1, 0);
        }

        if (!preserveEntry) {
            // Cancel the workflow entry (if any) before returning tool
            int entryId = session.mining.workflowEntryId;
            session.mining.workflowEntryId = -1;
            if (entryId >= 0) {
                RtsWorkflowEngine.getInstance().from(player, entryId)
                        .ifPresent(token -> token.cancel());
            }

        }

        RtsToolLeaseManager.returnMiningTool(player, session, session.mining.miningToolLease);
        if (hadMiningState) {
            ServiceRegistry.getInstance().page().markStorageViewDirty(player, session);
        }
        resetMiningState(session);
    }

    /**
     * Stops all active mining/ultimine activity, cancelling the workflow
     * entries. Equivalent to {@code stopActiveMining(player, session, false)}.
     *
     * @see #stopActiveMining(ServerPlayer, RtsStorageSession, boolean)
     */
    public static void stopActiveMining(ServerPlayer player, RtsStorageSession session) {
        stopActiveMining(player, session, false);
    }

    private static void stopCurrentMiningTask(ServerPlayer player, RtsStorageSession session) {
        int entryId = session.mining.workflowEntryId;
        if (entryId >= 0) {
            RtsWorkflowEngine.getInstance().from(player, entryId).ifPresent(token -> token.cancel());
            cancelMiningTask(player, session, entryId);
        } else {
            stopActiveMining(player, session);
        }
    }

    /**
     * 只取消指定工作流对应的挖掘任务。与旧 stopActiveMining 不同，本方法不会误清空其它排队任务。
     */
    public static boolean cancelMiningTask(ServerPlayer player, RtsStorageSession session, int workflowEntryId) {
        if (player == null || session == null || workflowEntryId < 0) return false;
        if (session.mining.workflowEntryId == workflowEntryId) {
            BlockPos progressPos = session.mining.miningPos != null
                    ? session.mining.miningPos : session.mining.ultimineProgressPos;
            if (progressPos != null) {
                RtsMiningNetworkHelper.clearMineProgress(player, progressPos);
            }
            List<HistoryBlockRecord> records = new ArrayList<>(session.mining.ultimineProcessedPositions);
            if (!records.isEmpty()) {
                ServerHistoryManager.recordBreakWithRecords(player, records, session.mining.miningFace);
            }
            session.mining.workflowEntryId = -1;
            if (session.mining.miningToolLease != null
                    && !session.mining.miningToolLease.isEmpty()) {
                RtsToolLeaseManager.returnMiningTool(player, session, session.mining.miningToolLease);
            }
            resetMiningState(session);
            markMiningCleanupDirty(player);
            return true;
        }

        return false;
    }

    private static void markMiningCleanupDirty(ServerPlayer player) {
        RtsEffectAccumulator.INSTANCE.markStorageViewDirty(player.getUUID(), player.level().dimension());
        RtsEffectAccumulator.INSTANCE.markWorkflow(player.getUUID(), player.level().dimension());
        RtsEffectAccumulator.INSTANCE.markPersistence(player.getUUID(), player.level().dimension());
    }

    /**
     * Releases mining resources (borrowed tool, break-stage particles) without
     * cancelling the workflow entry or clearing the runtime mining state.
     *
     * <p>Use this when RTS mode is disabled — the workflow entries are paused
     * separately via {@code pauseAllActive()}, and when the player re-enables
     * RTS mode and unpauses, the mining operation can continue from where it
     * left off because the mining state ({@code miningPos},
     * {@code ultimineTargets}, etc.) is preserved.</p>
     *
     * <p>This is intentionally <b>not</b> equivalent to
     * {@code stopActiveMining(player, session, true)} — that method still
     * clears the runtime mining state via {@code resetMiningState()} and
     * making resumption impossible.</p>
     */
    public static void releaseMiningResources(ServerPlayer player, RtsStorageSession session) {
        // Clear break-stage particles on the client
        BlockPos progressPos = session.mining.miningPos != null
                ? session.mining.miningPos
                : session.mining.ultimineProgressPos;
        if (progressPos != null) {
            player.level().destroyBlockProgress(player.getId(), progressPos, -1);
            RtsMiningNetworkHelper.sendMineProgress(player, progressPos, -1);
        }
        boolean hadUltimine = session.mining.ultimineProgressPos != null
                || !session.mining.ultimineTargets.isEmpty();
        if (hadUltimine) {
            RtsMiningNetworkHelper.sendUltimineProgress(player, -1, 0);
        }

        // Return the borrowed tool (if any) — player gets their item back
        RtsToolLeaseManager.returnMiningTool(player, session, session.mining.miningToolLease);

        // Keep the mining state intact — do NOT reset miningPos, ultimineTargets,
        // or workflow entry IDs. The workflow entry is paused
        // by the caller (pauseAllActive), and tickActiveMining() will skip
        // paused entries. When unpaused, mining resumes from where it stopped.
        // We only reset the tool lease since we already returned it.
        session.mining.miningToolLease = RtsToolLease.empty();
        session.mining.miningSelectedToolRequested = false;
    }

    // =========================================================================
    //  Mining Init
    // =========================================================================

    /**
     * Initialises remote mining state for the given block position, clearing
     * any previous break-stage particles from a different target.
     */
    public static void beginRemoteMining(ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face,
            int toolSlot) {
        if (session.mining.miningPos != null && !session.mining.miningPos.equals(pos)) {
            RtsMiningNetworkHelper.clearMineProgress(player, session.mining.miningPos);
        }
        session.mining.miningPos = pos.immutable();
        session.mining.miningFace = face == null ? Direction.DOWN : face;
        session.mining.miningToolSlot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        session.mining.miningProgress = 0.0F;
        session.mining.miningStage = -1;
    }

    // =========================================================================
    //  Block Destruction
    // =========================================================================

    /**
     * Result of a {@link #destroyMinedBlock} call.
     *
     * @param broken  whether the target block was successfully broken
     * @param remainder  the tool stack remainder after breaking
     */
    public record MiningBreakResult(boolean broken, ItemStack remainder) {
    }

    /**
     * Destroys the block at {@code pos}, either via a borrowed tool lease
     * (which tracks the mutated remainder) or by temporarily switching the
     * player's selected hotbar slot.
     */
    public static MiningBreakResult destroyMinedBlock(ServerPlayer player, RtsStorageSession session, BlockPos pos, int toolSlot) {
        Direction face = session != null && session.mining.miningFace != null ? session.mining.miningFace : Direction.DOWN;
        if (!RtsClaimProtectionService.canBreakBlock(player, pos, face)) {
            return new MiningBreakResult(false, ItemStack.EMPTY);
        }
        BlockState beforeState = player.level().getBlockState(pos);
        return RtsMiningDropCapture.capture(player, session, () -> {
            boolean broken;
            ItemStack remainder;
            if (session.mining.miningToolLease != null && !session.mining.miningToolLease.isEmpty()) {
                RtsToolLease lease = session.mining.miningToolLease;
                MiningBreakResult outcome = destroyBlockWithTemporaryMainHand(player, pos, lease.stack());
                remainder = RtsToolLeaseManager.protectBorrowedToolRemainder(player, lease, outcome.remainder());
                session.mining.miningToolLease = lease.withStack(remainder);
                broken = outcome.broken();
            } else if (session.mining.miningSelectedToolRequested) {
                broken = false;
                remainder = ItemStack.EMPTY;
            } else {
                broken = TemporaryContextSwitcher.withTemporarySelectedSlot(
                        player, toolSlot, () -> player.gameMode.destroyBlock(pos));
                remainder = ItemStack.EMPTY;
            }
            if (broken) {
                BlockState resultState = player.level().getBlockState(pos);
                RtsMiningNetworkHelper.sendBreakAnimation(player, pos, beforeState, resultState);
                RtsPlacementSound.playRemoteBlockBreakSound(player, player.level(), pos, beforeState);
            }
            return new MiningBreakResult(broken, remainder);
        });
    }

    // =========================================================================
    //  Progress Calculation — 已迁移至 MiningSpeedCalculator
    // =========================================================================

    /** @deprecated 使用 {@link MiningSpeedCalculator#computeRemoteDestroyStep} */
    @Deprecated
    public static float computeRemoteDestroyStep(ServerPlayer player, BlockState state, BlockPos pos, int toolSlot,
            ItemStack linkedTool, boolean selectedToolRequested) {
        return MiningSpeedCalculator.computeRemoteDestroyStep(player, state, pos, toolSlot, linkedTool, selectedToolRequested);
    }

    // =========================================================================
    //  MiningDestroyOutcome (temporary swapper)
    // =========================================================================

    /**
     * Swaps the player's main hand to the given tool stack, destroys the
     * block, reads back the (possibly damaged) remainder, and restores the
     * original main-hand item.
     */
    static MiningBreakResult destroyBlockWithTemporaryMainHand(ServerPlayer player, BlockPos pos, ItemStack tool) {
        ItemStack previousMainHand = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        boolean broken;
        ItemStack remainder;
        try {
            broken = player.gameMode.destroyBlock(pos);
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new MiningBreakResult(broken, remainder);
    }

    // =========================================================================
    //  Mining Completion (shared cleanup)
    // =========================================================================

    /**
     * Finalises a completed mining operation by delegating to the three
     * cleanup pipes ({@link ToolReturnPipe},
     * {@link HistoryRecordPipe}), then refreshing the storage page and
     * resetting the mining state.
     *
     * <p>This method builds a {@link PipelineContext} from the current
     * session state so that the three pipes can execute with the same
     * data contract as the sync-phase pipeline.  Cleanup is best-effort:
     * failures are logged via {@link WorkflowPipeline#runCleanupSequence}
     * but do not prevent subsequent cleanup steps.</p>
     *
     * @param player     the server-side player
     * @param session    the player's storage session
     * @param records    history records captured before block break (may be empty)
     * @param face       the mining face used (may be null)
     */
    static void finalizeMiningOperation(ServerPlayer player, RtsStorageSession session,
            List<HistoryBlockRecord> records, @Nullable Direction face) {

        // ── Build pipeline context from session state ────────────────
        PipelineContext ctx = new PipelineContext(player, Map.of());

        // Session (required by ToolReturnPipe and HistoryRecordPipe)
        ctx.setData(SessionValidatePipe.KEY_SESSION, session);

        // Workflow entry ID (from session.mining)
        int wfEntryId = session.mining.workflowEntryId;
        session.mining.workflowEntryId = -1;
        if (wfEntryId >= 0) {
            ctx.setData(PipelineContext.KEY_WORKFLOW_ENTRY_ID, wfEntryId);
        }

        RtsbuildingMod.LOGGER.info("[RtsMiningStateMachine] finalizeMiningOperation: entryId={}, records={} for {}", wfEntryId, records.size(), player.getGameProfile().getName());

        // Borrowed tool lease — only return if this is the LAST job
        if (session.mining.miningToolLease != null
                && !session.mining.miningToolLease.isEmpty()) {
            ctx.setData(ToolReturnPipe.KEY_TOOL_LEASE, session.mining.miningToolLease);
        }

        // History records (pre-captured before-state)
        if (!records.isEmpty()) {
            ctx.setData(HistoryRecordPipe.ARG_HISTORY_RECORDS, records);
            ctx.setData(HistoryRecordPipe.ARG_HISTORY_FACE, face != null ? face : Direction.DOWN);
        }

        // ── Execute cleanup sequence via WorkflowPipeline ───────────
        // 当队列中还有排队作业时，跳过 ToolReturnPipe（工具由下一个作业继续使用）
        var cleanupPipes = new ArrayList<PipelinePipe<? super PipelineContext>>();
        cleanupPipes.add(new ToolReturnPipe());
        cleanupPipes.add(new HistoryRecordPipe());
        WorkflowPipeline.runCleanupSequence(ctx, cleanupPipes);

        // 触发储存页面刷新以保证GUI实时更新
        ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
        resetMiningState(session);
    }

    // =========================================================================
    //  State Reset
    // =========================================================================

    public static void resetMiningState(RtsStorageSession session) {
        session.mining.miningPos = null;
        session.mining.ultimineTargets.clear();
        session.mining.ultimineProgressPos = null;
        session.mining.ultimineTotalTargets = 0;
        session.mining.ultimineProcessedTargets = 0;
        session.mining.ultimineBrokenTargets = 0;
        session.mining.ultimineProcessedPositions.clear();
        session.mining.ultimineNotifyAccumulator = 0;
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = Direction.DOWN;
        session.mining.miningProgress = 0.0F;
        session.mining.miningStage = -1;
        session.mining.miningToolLease = RtsToolLease.empty();
        session.mining.miningSelectedToolRequested = false;
        session.mining.miningToolProtectionEnabled = true;
        session.mining.workflowEntryId = -1;
    }


    /**
     * Removes a specific position from the ultimine target queue.
     */
    private static void removeUltimineTarget(RtsStorageSession session, BlockPos pos) {
        session.mining.ultimineTargets.removeIf(target -> target.equals(pos));
    }
}
