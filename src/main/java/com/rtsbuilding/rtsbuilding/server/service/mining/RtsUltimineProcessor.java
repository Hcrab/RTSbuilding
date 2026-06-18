package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.AreaOperationExecutor;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Ultimine / area-mine / area-destroy batch processing.
 *
 * <p>This class owns the collection, per-tick processing, and finalisation of
 * multi-block mining batches.  It delegates single-block operations to
 * {@link RtsMiningStateMachine} and validation to
 * {@link RtsMiningValidator}.</p>
 *
 * <p><b>Improvements over the monolithic original:</b>
 * <ul>
 *   <li>Waterlogged blocks are no longer incorrectly excluded in
 *       {@link #collectAreaDestroyTargets}.</li>
 *   <li>Multi-block collateral (doors, beds) is tracked via
 *   <li>All session state manipulation is explicit and local.</li>
 * </ul>
 */
public final class RtsUltimineProcessor {

    private RtsUltimineProcessor() {
    }

    // =========================================================================
    //  Ultimine Start
    // =========================================================================

    /**
     * Starts an ultimine batch (connected-block mining) at the given seed
     * position.  Creative mode breaks instantly; survival mode begins remote
     * break progress on the first target.
     *
     * <p><b>Preconditions (guaranteed by pipeline):</b> feature gate passed,
     * session resolved and dimension-sanitised, previous mining stopped,
     * tool borrowed (stored in {@code session.mining.miningToolLease}),
     * workflow started ({@code ctx data: workflowEntryId}).</p>
     */
    public static void startUltimine(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, Direction face, byte toolSlot, int requestedLimit,
            byte mode, boolean toolProtectionEnabled) {
        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        int progressionLimit = RtsProgressionManager.getUltimineLimit(player);
        if (progressionLimit <= 0) {
            return;
        }
        int limit = Math.max(1, Math.min(Math.min(RtsMiningValidator.ULTIMINE_MAX_BLOCKS, progressionLimit), requestedLimit));

        if (player.isCreative()) {
            Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot, ItemStack.EMPTY, false,
                    limit, true, mode);
            if (targets.isEmpty()) {
                return;
            }
            breakCreativeUltimineTargets(player, session, targets, slot);
            // UiRefresh handled by pipeline
            return;
        }

        boolean selectedToolRequested = session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = session.mining.miningToolLease;
        if (toolLease == null) {
            return;
        }
        Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot, toolLease.stack(),
                selectedToolRequested, limit, false, mode);
        if (targets.isEmpty()) {
            return;
        }

        session.mining.miningToolProtectionEnabled = toolProtectionEnabled;
        session.mining.ultimineTargets.clear();
        session.mining.ultimineTargets.addAll(targets);
        session.mining.ultimineProgressPos = targets.peekFirst();
        session.mining.ultimineTotalTargets = targets.size();
        session.mining.ultimineProcessedTargets = 0;
        session.mining.ultimineBrokenTargets = 0;
        session.mining.ultimineProcessedPositions.clear();
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = face == null ? Direction.DOWN : face;
        session.mining.miningToolSlot = slot;
        // Workflow token already set by upstream WorkflowStartPipe via UltimineExecutePipe
        RtsMiningStateMachine.beginRemoteMining(player, session, targets.peekFirst(), face, slot);
    }

    // =========================================================================
    //  Area Mine
    // =========================================================================

    /**
     * Starts an area-mine operation: breaks all breakable blocks within the
     * specified 3D volume bounds, filtered by shape/fill type.
     *
     * <p><b>Preconditions (guaranteed by pipeline):</b> feature gate passed,
     * session resolved, dimension sanitised, previous mining stopped, tool
     * borrowed ({@code session.mining.miningToolLease}), workflow started
     * (tracked via pipeline context).</p>
     */
    public static void areaMine(ServerPlayer player, RtsStorageSession session,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            byte toolSlot, byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        if (RtsProgressionManager.getUltimineLimit(player) <= 0) {
            return;
        }

        // Clamp bounds
        int clampedMinX = minX;
        int clampedMaxX = Math.min(clampedMinX + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxX);
        int clampedMinZ = minZ;
        int clampedMaxZ = Math.min(clampedMinZ + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxZ);
        int clampedMinY = minY;
        int clampedMaxY = Math.min(clampedMinY + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxY);

        boolean selectedToolRequested = !player.isCreative() && session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = player.isCreative()
                ? RtsToolLease.empty()
                : session.mining.miningToolLease;
        if (!player.isCreative() && toolLease == null) {
            return;
        }

        // Use shared shape system
        List<BlockPos> candidatePositions = AreaOperationExecutor.scanAreaMineTargets(
                player.serverLevel(),
                clampedMinX, clampedMaxX,
                clampedMinY, clampedMaxY,
                clampedMinZ, clampedMaxZ,
                player,
                shapeType, fillType);
        Deque<BlockPos> targets = new ArrayDeque<>(candidatePositions);

        if (targets.isEmpty()) {
            return;
        }

        if (player.isCreative()) {
            breakCreativeUltimineTargets(player, session, targets, slot);
            return;
        }

        session.mining.miningToolProtectionEnabled = toolProtectionEnabled;
        session.mining.ultimineTargets.clear();
        session.mining.ultimineTargets.addAll(targets);
        session.mining.ultimineProgressPos = targets.peekFirst();
        session.mining.ultimineTotalTargets = targets.size();
        session.mining.ultimineProcessedTargets = 0;
        session.mining.ultimineBrokenTargets = 0;
        session.mining.ultimineProcessedPositions.clear();
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = Direction.DOWN;
        session.mining.miningToolSlot = slot;
        // Workflow token already set by upstream WorkflowStartPipe via UltimineExecutePipe
        RtsMiningStateMachine.beginRemoteMining(player, session, targets.peekFirst(), null, slot);
    }

    // =========================================================================
    //  Area Destroy
    // =========================================================================

    /**
     * Destroys blocks at the given explicit positions (from Quick Build shape
     * preview).  Creative mode breaks instantly; survival mode feeds targets
     * into the ultimine batch processing pipeline.
     *
     * <p><b>Preconditions (guaranteed by pipeline):</b> feature gate passed,
     * session resolved, dimension sanitised, previous mining stopped, tool
     * borrowed ({@code session.mining.miningToolLease}), workflow started
     * (tracked via pipeline context).</p>
     */
    public static void areaDestroy(ServerPlayer player, RtsStorageSession session, List<BlockPos> positions,
            byte toolSlot, boolean toolProtectionEnabled) {
        if (positions == null || positions.isEmpty()) {
            return;
        }

        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        if (player.isCreative()) {
            Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, ItemStack.EMPTY, false, true);
            if (targets.isEmpty()) {
                return;
            }
            breakCreativeUltimineTargets(player, session, targets, slot);
            return;
        }

        boolean selectedToolRequested = session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = session.mining.miningToolLease;
        if (toolLease == null) {
            return;
        }
        Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, toolLease.stack(),
                selectedToolRequested, false);
        if (targets.isEmpty()) {
            return;
        }

        session.mining.miningToolProtectionEnabled = toolProtectionEnabled;
        session.mining.ultimineTargets.clear();
        session.mining.ultimineTargets.addAll(targets);
        session.mining.ultimineProgressPos = targets.peekFirst();
        session.mining.ultimineTotalTargets = targets.size();
        session.mining.ultimineProcessedTargets = 0;
        session.mining.ultimineBrokenTargets = 0;
        session.mining.ultimineProcessedPositions.clear();
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = Direction.DOWN;
        session.mining.miningToolSlot = slot;
        RtsbuildingMod.LOGGER.info("[RtsUltimineProcessor] areaDestroy: {} valid targets out of {} positions for {}",
                targets.size(), positions.size(), player.getGameProfile().getName());
        // Workflow token already set by upstream WorkflowStartPipe via UltimineExecutePipe
        RtsMiningStateMachine.beginRemoteMining(player, session, targets.peekFirst(), null, slot);
    }

    // =========================================================================
    //  Queue Mode (deferred execution for independent threads)
    // =========================================================================

    /**
     * Queues an area-destroy operation as a pending {@code MiningJob}.  The
     * targets will be processed when all earlier jobs in the queue complete.
     *
     * <p>In creative mode the blocks are broken immediately and the workflow
     * entry is completed right away, since creative-mode breaking requires
     * special handling that cannot go through the normal
     * {@link #processUltimineTargets} path.</p>
     *
     * @param workflowEntryId  the workflow entry created by WorkflowStartPipe
     * @return number of targets queued (or broken immediately for creative),
     *         or 0 if no valid targets
     */
    public static int queueAreaDestroy(ServerPlayer player, RtsStorageSession session, List<BlockPos> positions,
            byte toolSlot, boolean toolProtectionEnabled, int workflowEntryId) {
        if (positions == null || positions.isEmpty()) {
            return 0;
        }

        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);

        // Creative mode: break immediately to avoid slow per-tick processing
        if (player.isCreative()) {
            Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, ItemStack.EMPTY, false, true);
            if (targets.isEmpty()) {
                return 0;
            }
            // Preserve the current (active job's) tool lease by temporarily clearing it
            RtsToolLease savedLease = session.mining.miningToolLease;
            session.mining.miningToolLease = RtsToolLease.empty();
            try {
                breakCreativeUltimineTargets(player, session, targets, slot);
            } finally {
                session.mining.miningToolLease = savedLease;
            }
            // Complete the workflow entry immediately (blocks already broken)
            RtsWorkflowEngine.getInstance().from(player, workflowEntryId)
                    .ifPresent(token -> {
                        token.setTotalBlocks(targets.size());
                        token.setCompletedBlocks(targets.size());
                        token.complete();
                    });
            return targets.size();
        }

        // Survival mode: queue for deferred processing
        boolean selectedToolRequested = session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = session.mining.miningToolLease;
        if (toolLease == null) {
            return 0;
        }
        Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, toolLease.stack(),
                selectedToolRequested, false);
        if (targets.isEmpty()) {
            return 0;
        }

        session.mining.ultimineJobQueue.addLast(
                new RtsMiningStateMachine.MiningJob(workflowEntryId, targets, targets.size()));
        RtsbuildingMod.LOGGER.info("[RtsUltimineProcessor] queueAreaDestroy: queued {} targets, queue size = {} for {}",
                targets.size(), session.mining.ultimineJobQueue.size(), player.getGameProfile().getName());
        return targets.size();
    }

    /**
     * Queues an ultimine (connected-block) operation as a pending {@code MiningJob}.
     *
     * @param workflowEntryId  the workflow entry created by WorkflowStartPipe
     * @return number of targets queued, or 0 if no valid targets
     */
    public static int queueStartUltimine(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, Direction face, byte toolSlot, int requestedLimit,
            byte mode, boolean toolProtectionEnabled, int workflowEntryId) {
        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        int progressionLimit = RtsProgressionManager.getUltimineLimit(player);
        if (progressionLimit <= 0) {
            return 0;
        }
        int limit = Math.max(1, Math.min(Math.min(RtsMiningValidator.ULTIMINE_MAX_BLOCKS, progressionLimit), requestedLimit));

        // Creative mode: break immediately
        if (player.isCreative()) {
            Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot, ItemStack.EMPTY, false,
                    limit, true, mode);
            if (targets.isEmpty()) {
                return 0;
            }
            RtsToolLease savedLease = session.mining.miningToolLease;
            session.mining.miningToolLease = RtsToolLease.empty();
            try {
                breakCreativeUltimineTargets(player, session, targets, slot);
            } finally {
                session.mining.miningToolLease = savedLease;
            }
            RtsWorkflowEngine.getInstance().from(player, workflowEntryId)
                    .ifPresent(token -> {
                        token.setTotalBlocks(targets.size());
                        token.setCompletedBlocks(targets.size());
                        token.complete();
                    });
            return targets.size();
        }

        boolean selectedToolRequested = session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = session.mining.miningToolLease;
        if (toolLease == null) {
            return 0;
        }
        Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot, toolLease.stack(),
                selectedToolRequested, limit, false, mode);
        if (targets.isEmpty()) {
            return 0;
        }

        session.mining.ultimineJobQueue.addLast(
                new RtsMiningStateMachine.MiningJob(workflowEntryId, targets, targets.size()));
        return targets.size();
    }

    /**
     * Queues an area-mine operation as a pending {@code MiningJob}.
     *
     * @param workflowEntryId  the workflow entry created by WorkflowStartPipe
     * @return number of targets queued, or 0 if no valid targets
     */
    public static int queueAreaMine(ServerPlayer player, RtsStorageSession session,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            byte toolSlot, byte shapeType, byte fillType, boolean toolProtectionEnabled, int workflowEntryId) {
        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        if (RtsProgressionManager.getUltimineLimit(player) <= 0) {
            return 0;
        }

        // Clamp bounds
        int clampedMinX = minX;
        int clampedMaxX = Math.min(clampedMinX + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxX);
        int clampedMinZ = minZ;
        int clampedMaxZ = Math.min(clampedMinZ + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxZ);
        int clampedMinY = minY;
        int clampedMaxY = Math.min(clampedMinY + RtsMiningValidator.AREA_MINE_MAX_SIZE - 1, maxY);

        // Creative mode: break immediately
        if (player.isCreative()) {
            List<BlockPos> candidatePositions = AreaOperationExecutor.scanAreaMineTargets(
                    player.serverLevel(),
                    clampedMinX, clampedMaxX,
                    clampedMinY, clampedMaxY,
                    clampedMinZ, clampedMaxZ,
                    player,
                    shapeType, fillType);
            Deque<BlockPos> targets = new ArrayDeque<>(candidatePositions);
            if (targets.isEmpty()) {
                return 0;
            }
            RtsToolLease savedLease = session.mining.miningToolLease;
            session.mining.miningToolLease = RtsToolLease.empty();
            try {
                breakCreativeUltimineTargets(player, session, targets, slot);
            } finally {
                session.mining.miningToolLease = savedLease;
            }
            RtsWorkflowEngine.getInstance().from(player, workflowEntryId)
                    .ifPresent(token -> {
                        token.setTotalBlocks(targets.size());
                        token.setCompletedBlocks(targets.size());
                        token.complete();
                    });
            return targets.size();
        }

        boolean selectedToolRequested = session.mining.miningSelectedToolRequested;
        RtsToolLease toolLease = session.mining.miningToolLease;
        if (toolLease == null) {
            return 0;
        }

        List<BlockPos> candidatePositions = AreaOperationExecutor.scanAreaMineTargets(
                player.serverLevel(),
                clampedMinX, clampedMaxX,
                clampedMinY, clampedMaxY,
                clampedMinZ, clampedMaxZ,
                player,
                shapeType, fillType);
        Deque<BlockPos> targets = new ArrayDeque<>(candidatePositions);
        if (targets.isEmpty()) {
            return 0;
        }

        session.mining.ultimineJobQueue.addLast(
                new RtsMiningStateMachine.MiningJob(workflowEntryId, targets, targets.size()));
        return targets.size();
    }

    /**
     * Filters a list of explicit positions to valid, breakable targets.
     * Unlike the original, waterlogged blocks are <b>not</b> excluded.
     */
    private static Deque<BlockPos> collectAreaDestroyTargets(ServerPlayer player, List<BlockPos> positions,
            int toolSlot, ItemStack linkedTool, boolean selectedToolRequested, boolean creative) {
        if (player == null || positions == null || positions.isEmpty()) {
            return new ArrayDeque<>();
        }
        ServerLevel level = player.serverLevel();
        // 从上往下逐层破坏：按Y降序排列
        List<BlockPos> sortedPositions = new ArrayList<>(positions);
        sortedPositions.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());
        return RtsMiningTargetQueue.collectExplicitDestroyTargets(
                sortedPositions,
                pos -> RtsLinkedStorageResolver.canAccessWorldTarget(player, pos),
                pos -> {
                    BlockState state = level.getBlockState(pos);
                    // FIXED: No longer incorrectly excludes waterlogged blocks
                    if (!RtsMiningValidator.isBreakableBlock(state)
                            || !RtsMiningValidator.hasValidDestroySpeed(state, level, pos)) {
                        return false;
                    }
                    return creative
                            || RtsMiningStateMachine.computeRemoteDestroyStep(player, state, pos, toolSlot, linkedTool,
                                    selectedToolRequested) > 0.0F;
                });
    }

    // =========================================================================
    //  Ultimine Batch Processing
    // =========================================================================

    /**
     * Processes up to {@link RtsMiningValidator#ULTIMINE_BLOCKS_PER_TICK}
     * queued ultimine targets.
     */
    static void processUltimineTargets(ServerPlayer player, RtsStorageSession session) {
        if (session.mining.ultimineTargets.isEmpty()) {
            RtsbuildingMod.LOGGER.info("[RtsUltimineProcessor] processUltimineTargets: no remaining targets, finishing batch for {}",
                    player.getGameProfile().getName());
            finishUltimineBatch(player, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        int processedThisTick = 0;

        while (RtsMiningTargetQueue.canProcessAnotherTargetThisTick(processedThisTick, session.mining.ultimineTargets)) {
            if (RtsMiningValidator.isToolNearBreak(player, session)) {
                finishUltimineBatch(player, session);
                return;
            }
            BlockPos target = RtsMiningTargetQueue.pollNextTarget(session.mining.ultimineTargets);
            if (target == null) {
                break;
            }
            processedThisTick++;
            session.mining.ultimineProcessedTargets++;

            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
                continue;
            }
            BlockState targetState = level.getBlockState(target);
            if (!RtsMiningValidator.isBreakableBlock(targetState)
                    || !RtsMiningValidator.hasValidDestroySpeed(targetState, level, target)) {
                continue;
            }
            if (RtsMiningStateMachine.computeRemoteDestroyStep(player, targetState, target, session.mining.miningToolSlot,
                    session.mining.miningToolLease.stack(), session.mining.miningSelectedToolRequested) <= 0.0F) {
                continue;
            }

            // Capture before state for history (including neighbors for multi-block tracking)
            HistoryBlockRecord preRecord = ServerHistoryManager.captureBlock(player.serverLevel(), target);
            List<HistoryBlockRecord> neighborRecords = captureNeighborRecords(level, target);

            RtsMiningStateMachine.MiningBreakResult result = RtsMiningStateMachine.destroyMinedBlock(
                    player, session, target, session.mining.miningToolSlot);

            if (result.broken() && preRecord != null) {
                session.mining.ultimineProcessedPositions.add(preRecord);
                session.mining.ultimineBrokenTargets++;
                // Record any collateral multi-block destruction
                recordCollateralBlocks(level, session, neighborRecords, target);
            }
            if (result.broken() && RtsMiningValidator.canAutoStoreDrops(player, session)) {
                RtsDropAbsorber.absorbMinedDropsImmediately(player, session, target);
            }
            if (result.broken() && RtsMiningValidator.isToolNearBreak(player, session)) {
                finishUltimineBatch(player, session);
                return;
            }
        }

        RtsMiningNetworkHelper.sendUltimineBatchProgress(player, session);
        if (session.mining.ultimineTargets.isEmpty()) {
            finishUltimineBatch(player, session);
        }
    }

    /**
     * Finalises an ultimine batch: clears progress, returns the borrowed tool,
     * marks the storage page dirty, and resets the mining state.
     */
    static void finishUltimineBatch(ServerPlayer player, RtsStorageSession session) {
        RtsbuildingMod.LOGGER.info("[RtsUltimineProcessor] finishUltimineBatch: {} broken / {} processed / {} total for {}",
                session.mining.ultimineBrokenTargets, session.mining.ultimineProcessedTargets,
                session.mining.ultimineTotalTargets, player.getGameProfile().getName());
        // Copy history records before clearing the session list
        List<HistoryBlockRecord> records = new ArrayList<>(session.mining.ultimineProcessedPositions);
        session.mining.ultimineProcessedPositions.clear();

        RtsMiningNetworkHelper.sendUltimineProgress(player, -1, 0);
        if (session.mining.ultimineProgressPos != null) {
            RtsMiningNetworkHelper.clearMineProgress(player, session.mining.ultimineProgressPos);
        }
        RtsMiningStateMachine.finalizeMiningOperation(player, session, records, session.mining.miningFace);
    }

    /**
     * Instantly breaks all queued ultimine targets for a creative-mode player.
     */
    static void breakCreativeUltimineTargets(ServerPlayer player, RtsStorageSession session, Deque<BlockPos> targets,
            int toolSlot) {
        if (!targets.isEmpty()) {
            List<BlockPos> validTargets = new ArrayList<>();
            for (BlockPos target : targets) {
                if (RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
                    validTargets.add(target);
                }
            }
            if (!validTargets.isEmpty()) {
                Direction face = session != null && session.mining.miningFace != null ? session.mining.miningFace : Direction.DOWN;
                ServerHistoryManager.recordBreak(player, validTargets, face);
            }
        }
        while (!targets.isEmpty()) {
            BlockPos target = targets.removeFirst();
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
                continue;
            }
            RtsMiningStateMachine.destroyMinedBlock(player, session, target, toolSlot);
        }
    }

    // =========================================================================
    //  Multi-Block Collateral Tracking
    // =========================================================================

    /**
     * Captures the before-break state of all 6 neighbors for multi-block
     * structure tracking.
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
     * and records them as collateral in the session.
     */
    private static void recordCollateralBlocks(ServerLevel level, RtsStorageSession session,
            List<HistoryBlockRecord> neighborRecords, BlockPos brokenPos) {
        for (HistoryBlockRecord nr : neighborRecords) {
            if (nr.pos().equals(brokenPos)) {
                continue;
            }
            // If the neighbor was solid before but is now air, it was collateral-destroyed
            BlockState currentState = level.getBlockState(nr.pos());
            if (currentState.isAir() && !nr.state().isAir()) {
                session.mining.ultimineProcessedPositions.add(nr);
            }
        }
    }
}
