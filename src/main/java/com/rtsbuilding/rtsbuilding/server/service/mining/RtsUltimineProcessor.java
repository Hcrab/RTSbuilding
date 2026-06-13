package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.common.AreaOperationExecutor;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLeaseManager;
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
     * position.  Creative mode breaks instantly; survival mode borrows a tool
     * and begins remote break progress on the first target.
     */
    public static void startUltimine(ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face,
            byte toolSlot, String toolItemId, ItemStack toolPrototype, int requestedLimit, byte mode,
            boolean toolProtectionEnabled) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

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
                RtsMiningStateMachine.stopActiveMining(player, session);
                return;
            }
            RtsMiningStateMachine.stopActiveMining(player, session);
            breakCreativeUltimineTargets(player, session, targets, slot);
            RtsPageService.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        RtsMiningStateMachine.stopActiveMining(player, session);
        boolean selectedToolRequested = RtsMiningValidator.isSelectedMiningToolRequested(toolItemId, toolPrototype);
        RtsToolLease toolLease = RtsToolLeaseManager.borrowMiningTool(player, session, toolItemId, toolPrototype, slot);
        if (selectedToolRequested && toolLease.isEmpty()) {
            return;
        }
        Deque<BlockPos> targets = RtsMiningValidator.collectUltimineTargets(player, pos, slot, toolLease.stack(),
                selectedToolRequested, limit, false, mode);
        if (targets.isEmpty()) {
            RtsToolLeaseManager.returnMiningTool(player, session, toolLease);
            return;
        }

        session.miningToolLease = toolLease;
        session.miningSelectedToolRequested = selectedToolRequested;
        session.miningToolProtectionEnabled = toolProtectionEnabled;
        session.ultimineTargets.clear();
        session.ultimineTargets.addAll(targets);
        session.ultimineProgressPos = targets.peekFirst();
        session.ultimineTotalTargets = targets.size();
        session.ultimineProcessedTargets = 0;
        session.ultimineProcessedPositions.clear();
        session.ultimineAbsorbedDrops = false;
        session.miningFace = face == null ? Direction.DOWN : face;
        session.miningToolSlot = slot;
        RtsMiningNetworkHelper.sendUltimineProgress(player, 0, targets.size());
        RtsMiningStateMachine.beginRemoteMining(player, session, targets.peekFirst(), face, slot);
    }

    // =========================================================================
    //  Area Mine
    // =========================================================================

    /**
     * Starts an area-mine operation: breaks all breakable blocks within the
     * specified 3D volume bounds, filtered by shape/fill type.
     *
     * <p>Delegates position generation to {@link AreaOperationExecutor} and
     * feeds the results into the ultimine batch processing pipeline.</p>
     */
    public static void areaMine(ServerPlayer player, RtsStorageSession session,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            byte toolSlot, String toolItemId, ItemStack toolPrototype,
            byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

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

        RtsMiningStateMachine.stopActiveMining(player, session);
        boolean selectedToolRequested = !player.isCreative() && RtsMiningValidator.isSelectedMiningToolRequested(toolItemId, toolPrototype);
        RtsToolLease toolLease = player.isCreative()
                ? RtsToolLease.empty()
                : RtsToolLeaseManager.borrowMiningTool(player, session, toolItemId, toolPrototype, slot);
        if (selectedToolRequested && toolLease.isEmpty()) {
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
            RtsToolLeaseManager.returnMiningTool(player, session, toolLease);
            return;
        }

        if (player.isCreative()) {
            RtsMiningStateMachine.stopActiveMining(player, session);
            breakCreativeUltimineTargets(player, session, targets, slot);
            RtsPageService.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        session.miningToolLease = toolLease;
        session.miningSelectedToolRequested = selectedToolRequested;
        session.miningToolProtectionEnabled = toolProtectionEnabled;
        session.ultimineTargets.clear();
        session.ultimineTargets.addAll(targets);
        session.ultimineProgressPos = targets.peekFirst();
        session.ultimineTotalTargets = targets.size();
        session.ultimineProcessedTargets = 0;
        session.ultimineProcessedPositions.clear();
        session.ultimineAbsorbedDrops = false;
        session.miningFace = Direction.DOWN;
        session.miningToolSlot = slot;
        RtsMiningNetworkHelper.sendUltimineProgress(player, 0, targets.size());
        RtsMiningStateMachine.beginRemoteMining(player, session, targets.peekFirst(), null, slot);
    }

    // =========================================================================
    //  Area Destroy
    // =========================================================================

    /**
     * Destroys blocks at the given explicit positions (from Quick Build shape
     * preview).  Creative mode breaks instantly; survival mode feeds targets
     * into the ultimine batch processing pipeline.
     */
    public static void areaDestroy(ServerPlayer player, RtsStorageSession session, List<BlockPos> positions,
            byte toolSlot, String toolItemId, ItemStack toolPrototype, boolean toolProtectionEnabled) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.AREA_DESTROY)) {
            return;
        }
        if (session == null || positions == null || positions.isEmpty()) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        if (player.isCreative()) {
            Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, ItemStack.EMPTY, false, true);
            if (targets.isEmpty()) {
                RtsMiningStateMachine.stopActiveMining(player, session);
                return;
            }
            RtsMiningStateMachine.stopActiveMining(player, session);
            breakCreativeUltimineTargets(player, session, targets, slot);
            RtsPageService.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        RtsMiningStateMachine.stopActiveMining(player, session);
        boolean selectedToolRequested = RtsMiningValidator.isSelectedMiningToolRequested(toolItemId, toolPrototype);
        RtsToolLease toolLease = RtsToolLeaseManager.borrowMiningTool(player, session, toolItemId, toolPrototype, slot);
        if (selectedToolRequested && toolLease.isEmpty()) {
            return;
        }
        Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, toolLease.stack(),
                selectedToolRequested, false);
        if (targets.isEmpty()) {
            RtsToolLeaseManager.returnMiningTool(player, session, toolLease);
            return;
        }

        session.miningToolLease = toolLease;
        session.miningSelectedToolRequested = selectedToolRequested;
        session.miningToolProtectionEnabled = toolProtectionEnabled;
        session.ultimineTargets.clear();
        session.ultimineTargets.addAll(targets);
        session.ultimineProgressPos = targets.peekFirst();
        session.ultimineTotalTargets = targets.size();
        session.ultimineProcessedTargets = 0;
        session.ultimineProcessedPositions.clear();
        session.ultimineAbsorbedDrops = false;
        session.miningFace = Direction.DOWN;
        session.miningToolSlot = slot;
        RtsMiningNetworkHelper.sendUltimineProgress(player, 0, targets.size());
        RtsMiningStateMachine.beginRemoteMining(player, session, targets.peekFirst(), null, slot);
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
        LinkedHashSet<BlockPos> unique = new LinkedHashSet<>();
        for (BlockPos raw : positions) {
            if (raw == null || unique.size() >= RtsMiningValidator.AREA_DESTROY_MAX_TARGETS) {
                continue;
            }
            BlockPos pos = raw.immutable();
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            // FIXED: No longer incorrectly excludes waterlogged blocks
            if (!RtsMiningValidator.isBreakableBlock(state)
                    || !RtsMiningValidator.hasValidDestroySpeed(state, level, pos)) {
                continue;
            }
            if (!creative && RtsMiningStateMachine.computeRemoteDestroyStep(player, state, pos, toolSlot, linkedTool,
                    selectedToolRequested) <= 0.0F) {
                continue;
            }
            unique.add(pos);
        }
        return new ArrayDeque<>(unique);
    }

    // =========================================================================
    //  Ultimine Batch Processing
    // =========================================================================

    /**
     * Processes up to {@link RtsMiningValidator#ULTIMINE_BLOCKS_PER_TICK}
     * queued ultimine targets.
     */
    static void processUltimineTargets(ServerPlayer player, RtsStorageSession session) {
        if (session.ultimineTargets.isEmpty()) {
            finishUltimineBatch(player, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        int processedThisTick = 0;
        while (processedThisTick < RtsMiningValidator.ULTIMINE_BLOCKS_PER_TICK && !session.ultimineTargets.isEmpty()) {
            if (RtsMiningValidator.isToolNearBreak(player, session)) {
                finishUltimineBatch(player, session);
                return;
            }
            BlockPos target = session.ultimineTargets.removeFirst();
            processedThisTick++;
            session.ultimineProcessedTargets++;

            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
                continue;
            }
            BlockState targetState = level.getBlockState(target);
            if (!RtsMiningValidator.isBreakableBlock(targetState)
                    || !RtsMiningValidator.hasValidDestroySpeed(targetState, level, target)) {
                continue;
            }
            if (RtsMiningStateMachine.computeRemoteDestroyStep(player, targetState, target, session.miningToolSlot,
                    session.miningToolLease.stack(), session.miningSelectedToolRequested) <= 0.0F) {
                continue;
            }

            // Capture before state for history (including neighbors for multi-block tracking)
            HistoryBlockRecord preRecord = ServerHistoryManager.captureBlock(player.serverLevel(), target);
            List<HistoryBlockRecord> neighborRecords = captureNeighborRecords(level, target);

            RtsMiningStateMachine.MiningBreakResult result = RtsMiningStateMachine.destroyMinedBlock(
                    player, session, target, session.miningToolSlot);

            if (result.broken() && preRecord != null) {
                session.ultimineProcessedPositions.add(preRecord);
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
        if (session.ultimineTargets.isEmpty()) {
            finishUltimineBatch(player, session);
        }
    }

    /**
     * Finalises an ultimine batch: clears progress, returns the borrowed tool,
     * marks the storage page dirty, and resets the mining state.
     */
    static void finishUltimineBatch(ServerPlayer player, RtsStorageSession session) {
        if (!session.ultimineProcessedPositions.isEmpty()) {
            ServerHistoryManager.recordBreakWithRecords(player, new ArrayList<>(session.ultimineProcessedPositions), session.miningFace);
            session.ultimineProcessedPositions.clear();
        }
        RtsMiningNetworkHelper.sendUltimineProgress(player, -1, 0);
        RtsToolLeaseManager.returnMiningTool(player, session, session.miningToolLease);
        RtsPageService.markStorageViewDirty(player, session);
        if (session.ultimineProgressPos != null) {
            RtsMiningNetworkHelper.clearMineProgress(player, session.ultimineProgressPos);
        }
        RtsMiningStateMachine.resetMiningState(session);
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
                Direction face = session != null && session.miningFace != null ? session.miningFace : Direction.DOWN;
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
                session.ultimineProcessedPositions.add(nr);
            }
        }
    }
}
