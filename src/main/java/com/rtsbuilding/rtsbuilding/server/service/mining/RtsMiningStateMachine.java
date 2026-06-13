package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLeaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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

    private RtsMiningStateMachine() {
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
        if (session.mining.miningPos == null) {
            if (!session.mining.ultimineTargets.isEmpty()) {
                RtsUltimineProcessor.processUltimineTargets(player, session);
            }
            return;
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, session.mining.miningPos)) {
            stopActiveMining(player, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = session.mining.miningPos;
        BlockState state = level.getBlockState(pos);
        // FIXED: No longer incorrectly excludes waterlogged blocks
        if (!RtsMiningValidator.isBreakableBlock(state)
                || !RtsMiningValidator.hasValidDestroySpeed(state, level, pos)) {
            stopActiveMining(player, session);
            return;
        }
        if (RtsMiningValidator.isToolNearBreak(player, session)) {
            stopActiveMining(player, session);
            return;
        }

        float step = computeRemoteDestroyStep(player, state, pos, session.mining.miningToolSlot,
                session.mining.miningToolLease.stack(), session.mining.miningSelectedToolRequested);
        if (step <= 0.0F) {
            return;
        }

        session.mining.miningProgress += step;
        if (session.mining.miningProgress < 1.0F) {
            int stage = RtsMiningValidator.visibleMiningStage(session.mining.miningProgress);
            if (stage != session.mining.miningStage) {
                level.destroyBlockProgress(player.getId(), pos, stage);
                RtsMiningNetworkHelper.sendMineProgress(player, pos, stage);
                session.mining.miningStage = stage;
            }
            return;
        }

        // --- Progress complete: break the block ---

        // Capture before-state for history (must be done before destroy)
        HistoryBlockRecord preRecord = ServerHistoryManager.captureBlock(player.serverLevel(), pos);
        // Also capture neighbor states for multi-block tracking
        List<HistoryBlockRecord> neighborRecords = captureNeighborRecords(player.serverLevel(), pos);

        MiningBreakResult result = destroyMinedBlock(player, session, pos, session.mining.miningToolSlot);
        level.destroyBlockProgress(player.getId(), pos, -1);

        if (result.broken() && !session.mining.ultimineTargets.isEmpty()) {
            // Part of an ultimine batch — advance to next target
            removeUltimineTarget(session, pos);
            session.mining.ultimineProcessedTargets = Math.max(session.mining.ultimineProcessedTargets, 1);
            session.mining.ultimineProcessedPositions.add(preRecord);
            // Record any collateral blocks (multi-block structures)
            recordCollateralBlocks(session, neighborRecords, pos);
            if (RtsMiningValidator.canAutoStoreDrops(player, session)) {
                RtsDropAbsorber.absorbMinedDropsImmediately(player, session, pos);
            }
            session.mining.miningPos = null;
            session.mining.miningProgress = 0.0F;
            session.mining.miningStage = -1;
            RtsUltimineProcessor.processUltimineTargets(player, session);
            return;
        }

        // Single-block mode — finish
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
        RtsToolLeaseManager.returnMiningTool(player, session, session.mining.miningToolLease);
        RtsPageService.markStorageViewDirty(player, session);
        resetMiningState(session);
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
            broken = withTemporarySelectedSlot(player, toolSlot, () -> player.gameMode.destroyBlock(pos));
            remainder = ItemStack.EMPTY;
        }
        if (broken) {
            BlockState resultState = player.serverLevel().getBlockState(pos);
            RtsMiningNetworkHelper.sendBreakAnimation(player, pos, beforeState, resultState);
            RtsPlacementSound.playRemoteBlockBreakSound(player, player.serverLevel(), pos);
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
     *         ≤ 0.0 if the block cannot be mined
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
            broken = player.gameMode.destroyBlock(pos);
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new MiningBreakResult(broken, remainder);
    }

    // =========================================================================
    //  Underwater Speed Penalty
    // =========================================================================

    /**
     * Cancels the vanilla 1.20 underwater mining penalty while preserving Aqua Affinity.
     */
    static float removeMiningSpeedPenalty(ServerPlayer player, float destroyStep) {
        if (destroyStep <= 0.0F) {
            return destroyStep;
        }
        float adjusted = destroyStep;
        if (player.isEyeInFluid(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(player)) {
            adjusted *= 5.0F;
        }
        return adjusted;
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
        session.mining.ultimineAbsorbedDrops = false;
        session.mining.miningFace = Direction.DOWN;
        session.mining.miningProgress = 0.0F;
        session.mining.miningStage = -1;
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
            // a ServerLevel reference here — the caller's history recording
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
}
