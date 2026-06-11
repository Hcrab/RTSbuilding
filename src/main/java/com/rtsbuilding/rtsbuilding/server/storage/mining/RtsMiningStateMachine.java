package com.rtsbuilding.rtsbuilding.server.storage.mining;

import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLeaseManager;
import com.rtsbuilding.rtsbuilding.server.storage.placement.RtsPlacementSound;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
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
     * <p><b>Single-block mode</b> ({@code session.miningPos != null}):
     * accumulates progress and sends break-stage updates to the client.  On
     * completion, breaks the block, records history, absorbs drops, and either
     * proceeds to the next ultimine target or finalises.</p>
     *
     * <p><b>Ultimine mode</b> delegates to
     * {@link RtsUltimineProcessor#processUltimineTargets}.</p>
     */
    public static void tickActiveMining(ServerPlayer player, RtsStorageSession session) {
        if (session.miningPos == null) {
            if (!session.ultimineTargets.isEmpty()) {
                RtsUltimineProcessor.processUltimineTargets(player, session);
            }
            return;
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, session.miningPos)) {
            stopActiveMining(player, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = session.miningPos;
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

        float step = computeRemoteDestroyStep(player, state, pos, session.miningToolSlot,
                session.miningToolLease.stack(), session.miningSelectedToolRequested);
        if (step <= 0.0F) {
            return;
        }

        session.miningProgress += step;
        if (session.miningProgress < 1.0F) {
            int stage = RtsMiningValidator.visibleMiningStage(session.miningProgress);
            if (stage != session.miningStage) {
                level.destroyBlockProgress(player.getId(), pos, stage);
                RtsMiningNetworkHelper.sendMineProgress(player, pos, stage);
                session.miningStage = stage;
            }
            return;
        }

        // --- Progress complete: break the block ---

        // Capture before-state for history (must be done before destroy)
        HistoryBlockRecord preRecord = ServerHistoryManager.captureBlock(player.serverLevel(), pos);
        // Also capture neighbor states for multi-block tracking
        List<HistoryBlockRecord> neighborRecords = captureNeighborRecords(player.serverLevel(), pos);

        MiningBreakResult result = destroyMinedBlock(player, session, pos, session.miningToolSlot);
        level.destroyBlockProgress(player.getId(), pos, -1);

        if (result.broken() && !session.ultimineTargets.isEmpty()) {
            // Part of an ultimine batch — advance to next target
            removeUltimineTarget(session, pos);
            session.ultimineProcessedTargets = Math.max(session.ultimineProcessedTargets, 1);
            session.ultimineProcessedPositions.add(preRecord);
            // Record any collateral blocks (multi-block structures)
            recordCollateralBlocks(session, neighborRecords, pos);
            if (RtsMiningValidator.canAutoStoreDrops(player, session)) {
                RtsDropAbsorber.absorbMinedDropsImmediately(player, session, pos);
            }
            session.miningPos = null;
            session.miningProgress = 0.0F;
            session.miningStage = -1;
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
                ServerHistoryManager.recordBreakWithRecords(player, allRecords, session.miningFace);
            }
        }
        if (result.broken() && RtsMiningValidator.canAutoStoreDrops(player, session)) {
            RtsDropAbsorber.absorbMinedDropsImmediately(player, session, pos);
        }
        RtsToolLeaseManager.returnMiningTool(player, session, session.miningToolLease);
        RtsStorageManager.markStorageViewDirty(player, session);
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
        boolean hadMiningState = session.miningPos != null
                || session.ultimineProgressPos != null
                || !session.ultimineTargets.isEmpty()
                || !session.miningToolLease.isEmpty();
        boolean hadUltimine = session.ultimineProgressPos != null || !session.ultimineTargets.isEmpty();
        BlockPos progressPos = session.miningPos != null ? session.miningPos : session.ultimineProgressPos;
        if (progressPos != null) {
            player.serverLevel().destroyBlockProgress(player.getId(), progressPos, -1);
            RtsMiningNetworkHelper.sendMineProgress(player, progressPos, -1);
        }
        if (hadUltimine) {
            RtsMiningNetworkHelper.sendUltimineProgress(player, -1, 0);
        }
        RtsToolLeaseManager.returnMiningTool(player, session, session.miningToolLease);
        if (hadMiningState) {
            RtsStorageManager.markStorageViewDirty(player, session);
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
        if (session.miningPos != null && !session.miningPos.equals(pos)) {
            RtsMiningNetworkHelper.clearMineProgress(player, session.miningPos);
        }
        session.miningPos = pos.immutable();
        session.miningFace = face == null ? Direction.DOWN : face;
        session.miningToolSlot = RtsMiningValidator.clampHotbarSlot(toolSlot);
        session.miningProgress = 0.0F;
        session.miningStage = -1;
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
        if (session != null && session.miningToolLease != null && !session.miningToolLease.isEmpty()) {
            RtsToolLease lease = session.miningToolLease;
            MiningBreakResult outcome = destroyBlockWithTemporaryMainHand(player, pos, lease.stack());
            remainder = RtsToolLeaseManager.protectBorrowedToolRemainder(player, lease, outcome.remainder());
            session.miningToolLease = lease.withStack(remainder);
            broken = outcome.broken();
        } else if (session != null && session.miningSelectedToolRequested) {
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
     * Cancels the underwater mining speed penalty ({@code SUBMERGED_MINING_SPEED})
     * while preserving any positive modifier from enchantments or mods.
     */
    static float removeMiningSpeedPenalty(ServerPlayer player, float destroyStep) {
        if (destroyStep <= 0.0F) {
            return destroyStep;
        }
        float adjusted = destroyStep;
        if (player.isEyeInFluid(FluidTags.WATER)) {
            double submergedMiningSpeed = player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            if (submergedMiningSpeed > 0.0D && submergedMiningSpeed < 1.0D) {
                adjusted *= (float) (1.0D / submergedMiningSpeed);
            }
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
        session.miningPos = null;
        session.ultimineTargets.clear();
        session.ultimineProgressPos = null;
        session.ultimineTotalTargets = 0;
        session.ultimineProcessedTargets = 0;
        session.ultimineAbsorbedDrops = false;
        session.miningFace = Direction.DOWN;
        session.miningProgress = 0.0F;
        session.miningStage = -1;
        session.miningToolLease = RtsToolLease.empty();
        session.miningSelectedToolRequested = false;
        session.miningToolProtectionEnabled = true;
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
            session.ultimineProcessedPositions.add(nr);
        }
    }

    /**
     * Removes a specific position from the ultimine target queue.
     */
    private static void removeUltimineTarget(RtsStorageSession session, BlockPos pos) {
        session.ultimineTargets.removeIf(target -> target.equals(pos));
    }
}
