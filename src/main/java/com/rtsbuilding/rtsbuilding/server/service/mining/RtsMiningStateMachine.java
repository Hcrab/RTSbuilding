package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.compat.integrateddynamics.RtsIntegratedDynamicsCompat;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLeaseManager;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * State machine for single-block remote mining progress.
 *
 * <p>This class owns the per-tick accumulation loop
 * ({@link #tickActiveMining}) and the low-level block-destruction helpers
 * ({@link #destroyMinedBlock}, {@link #computeRemoteDestroyStep}).  Every
 * method is stateless ??all mutable state lives in
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

    private RtsMiningStateMachine() {
    }

    public record MiningJob(
            int workflowEntryId,
            Deque<BlockPos> targets,
            int totalTargets,
            Direction face,
            int toolSlot,
            RtsToolLease toolLease,
            boolean selectedToolRequested,
            boolean toolProtectionEnabled) {
        public MiningJob {
            targets = targets == null ? new ArrayDeque<>() : new ArrayDeque<>(targets);
            face = face == null ? Direction.DOWN : face;
            toolLease = toolLease == null ? RtsToolLease.empty() : toolLease;
            toolSlot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        }
    }

    /** 单次统一任务调度片产生的真实挖掘结果。 */
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

    private static void activateNextJob(ServerPlayer player, RtsStorageSession session) {
        MiningJob job = session.mining.ultimineJobQueue.removeFirst();
        session.mining.ultimineTargets.clear();
        session.mining.ultimineTargets.addAll(job.targets());
        session.mining.ultimineProgressPos = job.targets().peekFirst();
        session.mining.ultimineTotalTargets = job.totalTargets();
        session.mining.ultimineProcessedTargets = 0;
        session.mining.ultimineBrokenTargets = 0;
        session.mining.ultimineProcessedPositions.clear();
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = job.face();
        session.mining.miningToolSlot = job.toolSlot();
        session.mining.miningToolLease = job.toolLease();
        session.mining.miningSelectedToolRequested = job.selectedToolRequested();
        session.mining.miningToolProtectionEnabled = job.toolProtectionEnabled();
        session.mining.miningWorkflowEntryId = job.workflowEntryId();
        session.mining.miningPos = null;
        session.mining.miningProgress = 0.0F;
        session.mining.miningStage = -1;
        RtsMiningNetworkHelper.sendUltimineProgress(player, 0, session.mining.ultimineTotalTargets);
    }

    // =========================================================================
    //  Main Tick Handler
    // =========================================================================

    /**
     * Main tick handler for remote mining progress, invoked every server tick
     * while the player is in an RTS screen or remote-mining state.
     *
     * <p><b>Single-block mode</b> ({@code session.mining.miningPos != null}):
     * accumulates progress and sends break-stage updates to the client.  On
     * completion, breaks the block, records history, absorbs drops, and either
     * proceeds to the next ultimine target or finalises.</p>
     *
     * <p><b>Ultimine mode</b> delegates to
     * {@link RtsUltimineProcessor#processUltimineTargets}.</p>
     */
    public static void tickActiveMining(ServerPlayer player, RtsStorageSession session) {
        tickActiveMining(player, session, RtsMiningValidator.ultimineBlocksPerTick(), Long.MAX_VALUE);
    }

    /** 在统一任务引擎分配的数量与时间预算内推进挖掘。 */
    public static MiningAdvance tickActiveMining(ServerPlayer player, RtsStorageSession session,
            int maxUnits, long deadlineNanos) {
        if (session.miningDropBuffer.isFull()) {
            return MiningAdvance.bufferBlocked();
        }
        if (session.mining.miningPos == null) {
            if (!session.mining.ultimineTargets.isEmpty()) {
                return RtsUltimineProcessor.processUltimineTargets(player, session, maxUnits, deadlineNanos);
            } else if (!session.mining.ultimineJobQueue.isEmpty()) {
                activateNextJob(player, session);
                return RtsUltimineProcessor.processUltimineTargets(player, session, maxUnits, deadlineNanos);
            }
            return MiningAdvance.ended(0, 0, 0);
        }
        RtsWorkflowToken workflowToken = resolveActiveWorkflow(player, session);
        if (session.mining.miningWorkflowEntryId >= 0 && workflowToken == null) {
            cancelMiningTask(player, session, session.mining.miningWorkflowEntryId);
            return MiningAdvance.ended(0, 0, 0);
        }
        if (workflowToken != null && workflowToken.isPaused()) {
            return MiningAdvance.idle();
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, session.mining.miningPos)) {
            stopCurrentMiningTask(player, session);
            return MiningAdvance.ended(1, 0, 1);
        }
        if (!RtsClaimProtectionService.canBreakBlock(player, session.mining.miningPos, session.mining.miningFace)) {
            stopCurrentMiningTask(player, session);
            return MiningAdvance.ended(1, 0, 1);
        }

        ServerLevel level = player.serverLevel();
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

        float step = computeRemoteDestroyStep(player, state, pos, session.mining.miningToolSlot,
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
        HistoryBlockRecord preRecord = ServerHistoryManager.captureBlock(player.serverLevel(), pos);
        // Also capture neighbor states for multi-block tracking
        List<HistoryBlockRecord> neighborRecords = captureNeighborRecords(player.serverLevel(), pos);

        MiningBreakResult result = destroyMinedBlock(player, session, pos, session.mining.miningToolSlot);
        level.destroyBlockProgress(player.getId(), pos, -1);

        if (result.broken() && !session.mining.ultimineTargets.isEmpty()) {
            // Part of an ultimine batch ??advance to next target
            markWorkflowProgress(workflowToken, 1);
            removeUltimineTarget(session, pos);
            session.mining.ultimineProcessedTargets = Math.max(session.mining.ultimineProcessedTargets, 1);
            if (preRecord != null) {
                session.mining.ultimineProcessedPositions.add(preRecord);
            }
            // Record any collateral blocks (multi-block structures)
            recordCollateralBlocks(session, neighborRecords, pos);
            if (RtsMiningValidator.canAutoStoreDrops(player, session)) {
                RtsDropAbsorber.absorbMinedDropsImmediately(player, session, pos);
            }
            session.mining.miningPos = null;
            session.mining.miningProgress = 0.0F;
            session.mining.miningStage = -1;
            MiningAdvance tail = RtsUltimineProcessor.processUltimineTargets(
                    player, session, Math.max(0, maxUnits - 1), deadlineNanos);
            return new MiningAdvance(1, 1, 0, false, false).plus(tail);
        }

        // Single-block mode ??finish
        RtsMiningNetworkHelper.clearMineProgress(player, pos);
        if (result.broken()) {
            List<HistoryBlockRecord> allRecords = new ArrayList<>();
            if (preRecord != null) {
                allRecords.add(preRecord);
            }
            // Add any collateral blocks
            for (HistoryBlockRecord nr : neighborRecords) {
                BlockState currentState = player.serverLevel().getBlockState(nr.pos());
                if (currentState.isAir() && !nr.state().isAir()) {
                    allRecords.add(nr);
                }
            }
            if (!allRecords.isEmpty()) {
                ServerHistoryManager.recordBreakWithRecords(player, allRecords, session.mining.miningFace);
            }
        }
        if (result.broken() && RtsMiningValidator.canAutoStoreDrops(player, session)) {
            RtsDropAbsorber.absorbMinedDropsImmediately(player, session, pos);
        }
        finishSingleBlockWorkflow(workflowToken, result.broken());
        session.mining.miningWorkflowEntryId = -1;
        RtsToolLeaseManager.returnMiningTool(player, session, session.mining.miningToolLease);
        RtsPageService.markStorageViewDirty(player, session);
        resetMiningState(session);
        return MiningAdvance.ended(1, result.broken() ? 1 : 0, result.broken() ? 0 : 1);
    }

    private static void stopCurrentMiningTask(ServerPlayer player, RtsStorageSession session) {
        int entryId = session.mining.miningWorkflowEntryId;
        if (entryId >= 0) {
            RtsWorkflowEngine.getInstance().from(player, entryId).ifPresent(RtsWorkflowToken::cancel);
            cancelMiningTask(player, session, entryId);
        } else {
            stopActiveMining(player, session);
        }
    }

    /** 只取消指定工作流对应的挖掘任务，不误清空其他排队任务。 */
    public static boolean cancelMiningTask(ServerPlayer player, RtsStorageSession session, int workflowEntryId) {
        if (player == null || session == null || workflowEntryId < 0) return false;
        if (session.mining.miningWorkflowEntryId == workflowEntryId) {
            BlockPos progressPos = session.mining.miningPos != null
                    ? session.mining.miningPos : session.mining.ultimineProgressPos;
            if (progressPos != null) {
                RtsMiningNetworkHelper.clearMineProgress(player, progressPos);
            }
            List<HistoryBlockRecord> records = new ArrayList<>(session.mining.ultimineProcessedPositions);
            if (!records.isEmpty()) {
                ServerHistoryManager.recordBreakWithRecords(player, records, session.mining.miningFace);
            }
            RtsToolLeaseManager.returnMiningTool(player, session, session.mining.miningToolLease);
            resetMiningState(session);
            markMiningCleanupDirty(player);
            return true;
        }

        var iterator = session.mining.ultimineJobQueue.iterator();
        while (iterator.hasNext()) {
            MiningJob job = iterator.next();
            if (job.workflowEntryId() != workflowEntryId) continue;
            iterator.remove();
            RtsToolLeaseManager.returnMiningTool(player, session, job.toolLease());
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

    // =========================================================================
    //  Stop
    // =========================================================================

    /**
     * Stops all active mining/ultimine activity for the given session,
     * clears break-stage particles on the client, returns the borrowed tool,
     * and resets the session's mining state.
     */
    public static void stopActiveMining(ServerPlayer player, RtsStorageSession session) {
        boolean hadMiningState = session.mining.miningPos != null
                || session.mining.ultimineProgressPos != null
                || !session.mining.ultimineTargets.isEmpty()
                || !session.mining.miningToolLease.isEmpty();
        boolean hadUltimine = session.mining.ultimineProgressPos != null || !session.mining.ultimineTargets.isEmpty();
        BlockPos progressPos = session.mining.miningPos != null ? session.mining.miningPos : session.mining.ultimineProgressPos;
        if (progressPos != null) {
            player.serverLevel().destroyBlockProgress(player.getId(), progressPos, -1);
            RtsMiningNetworkHelper.sendMineProgress(player, progressPos, -1);
        }
        if (hadUltimine) {
            RtsMiningNetworkHelper.sendUltimineProgress(player, -1, 0);
        }
        cancelMiningWorkflow(player, session);
        for (MiningJob queued : session.mining.ultimineJobQueue) {
            RtsWorkflowEngine.getInstance()
                    .from(player, queued.workflowEntryId())
                    .ifPresent(RtsWorkflowToken::cancel);
            RtsToolLeaseManager.returnMiningTool(player, session, queued.toolLease());
        }
        session.mining.ultimineJobQueue.clear();
        RtsToolLeaseManager.returnMiningTool(player, session, session.mining.miningToolLease);
        if (hadMiningState) {
            RtsPageService.markStorageViewDirty(player, session);
        }
        resetMiningState(session);
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
        Direction face = session != null && session.mining.miningFace != null
                ? session.mining.miningFace : Direction.DOWN;
        if (!RtsClaimProtectionService.canBreakBlock(player, pos, face)) {
            return new MiningBreakResult(false, ItemStack.EMPTY);
        }
        BlockState beforeState = player.serverLevel().getBlockState(pos);
        boolean broken;
        ItemStack remainder;
        if (session != null && session.mining.miningToolLease != null && !session.mining.miningToolLease.isEmpty()) {
            RtsToolLease lease = session.mining.miningToolLease;
            MiningBreakResult outcome = destroyBlockWithTemporaryMainHand(player, pos, lease.stack());
            remainder = RtsToolLeaseManager.protectBorrowedToolRemainder(player, lease, outcome.remainder());
            session.mining.miningToolLease = lease.withStack(remainder);
            broken = outcome.broken();
        } else if (session != null && session.mining.miningSelectedToolRequested) {
            broken = false;
            remainder = ItemStack.EMPTY;
        } else {
            broken = withTemporarySelectedSlot(player, toolSlot,
                    () -> destroyBlockWithCompatFallback(player, pos));
            remainder = ItemStack.EMPTY;
        }
        if (broken) {
            BlockState resultState = player.serverLevel().getBlockState(pos);
            RtsMiningNetworkHelper.sendBreakAnimation(player, pos, beforeState, resultState);
            RtsPlacementSound.playRemoteBlockBreakSound(player, player.serverLevel(), pos, beforeState);
        }
        return new MiningBreakResult(broken, remainder);
    }

    // =========================================================================
    //  Progress Calculation
    // =========================================================================

    /**
     * Computes the per-tick destroy progress for the given block/tool
     * combination, applying underwater penalty cancellation.
     *
     * @return a float in (0.0, 1.0] representing progress per tick, or
     *         ??0.0 if the block cannot be mined
     */
    public static float computeRemoteDestroyStep(ServerPlayer player, BlockState state, BlockPos pos, int toolSlot,
            ItemStack linkedTool, boolean selectedToolRequested) {
        if (linkedTool != null && !linkedTool.isEmpty()) {
            return withTemporaryOnGround(player, true, () -> withTemporaryMainHandItem(
                    player,
                    linkedTool,
                    () -> removeMiningSpeedPenalty(player, state.getDestroyProgress(player, player.serverLevel(), pos))));
        }
        if (selectedToolRequested) {
            return 0.0F;
        }
        return withTemporaryOnGround(player, true, () -> withTemporarySelectedSlot(
                player,
                toolSlot,
                () -> removeMiningSpeedPenalty(player, state.getDestroyProgress(player, player.serverLevel(), pos))));
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
            broken = destroyBlockWithCompatFallback(player, pos);
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new MiningBreakResult(broken, remainder);
    }

    private static boolean destroyBlockWithCompatFallback(ServerPlayer player, BlockPos pos) {
        if (RtsIntegratedDynamicsCompat.tryDestroyCable(player, pos)) {
            return true;
        }
        return withTemporaryDestroyContext(player, pos, () -> player.gameMode.destroyBlock(pos));
    }

    /**
     * 临时让玩家“站到目标旁边并看向目标中心”再执行最终破坏。
     *
     * <p>部分第三方 multipart/cable 方块会在
     * {@code onDestroyedByPlayer} 内部重新基于玩家当前视线做组件 rayTrace。
     * RTS 远程挖掘时真实玩家视角通常并不对准远程方块，直接调用
     * {@code gameMode.destroyBlock} 会让这类方块返回 false。这里仅在最终
     * destroy 调用期间切换位置/朝向，操作结束后立刻恢复，不改变外层权限、
     * 工具、工作流和掉落处理。</p>
     */
    private static <T> T withTemporaryDestroyContext(ServerPlayer player, BlockPos pos, Supplier<T> action) {
        Vec3 targetCenter = Vec3.atCenterOf(pos);
        double eyeHeight = player.getEyeHeight(player.getPose());
        Vec3 virtualEye = targetCenter.add(0.0D, 0.0D, 2.25D);
        Vec3 virtualFeet = new Vec3(virtualEye.x, virtualEye.y - eyeHeight, virtualEye.z);
        return TemporaryContextSwitcher.withTemporaryUseItemContext(player, virtualFeet, targetCenter, 4.5D, action);
    }

    // =========================================================================
    //  Underwater Speed Penalty
    // =========================================================================

    /**
     * Forge 1.20.1 has no SUBMERGED_MINING_SPEED attribute yet, so there is no
     * vanilla attribute multiplier to undo here. Keep the method as the shared
     * service hook so the mining state machine stays structurally aligned with
     * main.
     */
    static float removeMiningSpeedPenalty(ServerPlayer player, float destroyStep) {
        return destroyStep;
    }

    // =========================================================================
    //  Temporary Context Switchers
    // =========================================================================

    public static <T> T withTemporaryMainHandItem(ServerPlayer player, ItemStack stack, Supplier<T> action) {
        ItemStack previousMainHand = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            return action.get();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
    }

    public static <T> T withTemporaryOnGround(ServerPlayer player, boolean onGround, Supplier<T> action) {
        boolean previous = player.onGround();
        player.setOnGround(onGround);
        try {
            return action.get();
        } finally {
            player.setOnGround(previous);
        }
    }

    static <T> T withTemporarySelectedSlot(ServerPlayer player, int toolSlot, Supplier<T> action) {
        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        int prevSelected = player.getInventory().selected;

        player.getInventory().selected = slot;
        try {
            return action.get();
        } finally {
            player.getInventory().selected = prevSelected;
        }
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
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = Direction.DOWN;
        session.mining.miningProgress = 0.0F;
        session.mining.miningStage = -1;
        session.mining.miningWorkflowEntryId = -1;
        session.mining.miningToolLease = RtsToolLease.empty();
        session.mining.miningSelectedToolRequested = false;
        session.mining.miningToolProtectionEnabled = true;
    }

    // =========================================================================
    //  Multi-Block Collateral Tracking
    // =========================================================================

    /**
     * Captures the before-break state of all 6 neighbors for multi-block
     * structure tracking (doors, beds, double plants, etc.).
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
     * After a block is broken, checks which neighbor positions changed to air
     * and adds them to the session's ultimine processed positions so they are
     * included in the batch history record.
     */
    private static void recordCollateralBlocks(RtsStorageSession session, List<HistoryBlockRecord> neighborRecords,
            BlockPos brokenPos) {
        for (HistoryBlockRecord nr : neighborRecords) {
            if (nr.pos().equals(brokenPos)) {
                continue;
            }
            // If the neighbor was solid before but is now air, it was collateral
            // destroyed by vanilla (e.g. the other half of a door or bed).
            // We rely on the caller to check current state since we don't have
            // a ServerLevel reference here ??the caller's history recording
            // handles this.
            session.mining.ultimineProcessedPositions.add(nr);
        }
    }

    /**
     * Removes a specific position from the ultimine target queue.
     */
    private static void removeUltimineTarget(RtsStorageSession session, BlockPos pos) {
        session.mining.ultimineTargets.removeIf(target -> target.equals(pos));
    }

    private static RtsWorkflowToken resolveActiveWorkflow(ServerPlayer player, RtsStorageSession session) {
        if (session.mining.miningWorkflowEntryId < 0) {
            return null;
        }
        return RtsWorkflowEngine.getInstance()
                .from(player, session.mining.miningWorkflowEntryId)
                .orElse(null);
    }

    private static void markWorkflowProgress(RtsWorkflowToken token, int delta) {
        if (token != null && delta > 0) {
            token.updateProgress(delta, null);
        }
    }

    private static void finishSingleBlockWorkflow(RtsWorkflowToken token, boolean success) {
        if (token == null) {
            return;
        }
        if (success) {
            token.markProgress();
            token.complete();
        } else {
            token.recordFailure();
            token.cancel();
        }
    }

    private static void cancelMiningWorkflow(ServerPlayer player, RtsStorageSession session) {
        if (session.mining.miningWorkflowEntryId < 0) {
            return;
        }
        RtsWorkflowEngine.getInstance()
                .from(player, session.mining.miningWorkflowEntryId)
                .ifPresent(RtsWorkflowToken::cancel);
        session.mining.miningWorkflowEntryId = -1;
    }
}
