package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.storage.mining.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Facade for the remote mining &amp; ultimine state machine.
 *
 * <p>All methods delegate to the appropriate sub-module in the
 * {@code mining} package.  This class exists solely to preserve the
 * existing call sites in {@link RtsStorageManager} and the network
 * layer without import changes.</p>
 *
 * <p>The actual implementation lives in:
 * <ul>
 *   <li>{@link RtsMiningStateMachine} — single-block progress &amp; context switchers</li>
 *   <li>{@link RtsUltimineProcessor} — ultimine / area-mine / area-destroy batch processing</li>
 *   <li>{@link RtsMiningValidator} — validation predicates</li>
 *   <li>{@link RtsToolLeaseManager} — tool borrow / return / protection</li>
 *   <li>{@link RtsDropAbsorber} — post-break drop collection</li>
 *   <li>{@link RtsMiningNetworkHelper} — network synchronisation</li>
 * </ul>
 *
 * @see RtsMiningStateMachine
 * @see RtsUltimineProcessor
 */
public final class RtsStorageMining {

    private RtsStorageMining() {
    }

    // =========================================================================
    //  Public API — delegated to sub-modules
    // =========================================================================

    public static void mine(ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face, boolean start,
            byte toolSlot, String toolItemId, ItemStack toolPrototype, boolean allowPlacedBlockRecovery,
            boolean toolProtectionEnabled) {
        if (start && !com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager.canUse(
                player, com.rtsbuilding.rtsbuilding.progression.RtsFeature.REMOTE_BREAK)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = RtsMiningValidator.clampHotbarSlot(toolSlot);

        if (start) {
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                RtsMiningStateMachine.stopActiveMining(player, session);
                return;
            }

            // Placed block recovery
            if (allowPlacedBlockRecovery
                    && RtsMiningValidator.tryRecoverPlacedBlock(player, session, pos, face)) {
                RtsMiningStateMachine.stopActiveMining(player, session);
                return;
            }

            RtsMiningStateMachine.stopActiveMining(player, session);
            if (player.isCreative()) {
                Direction actualFace = face == null ? Direction.DOWN : face;
                com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager.recordBreak(
                        player, List.of(pos.immutable()), actualFace);
                RtsMiningStateMachine.destroyMinedBlock(player, session, pos, slot);
                com.rtsbuilding.rtsbuilding.server.RtsStorageManager.requestPage(
                        player, session.page, session.search, session.category, session.sort, session.ascending);
                return;
            }

            session.miningSelectedToolRequested = RtsMiningValidator.isSelectedMiningToolRequested(toolItemId, toolPrototype);
            session.miningToolLease = RtsToolLeaseManager.borrowMiningTool(
                    player, session, toolItemId, toolPrototype, slot);
            if (session.miningSelectedToolRequested && session.miningToolLease.isEmpty()) {
                RtsMiningStateMachine.resetMiningState(session);
                return;
            }
            session.miningToolProtectionEnabled = toolProtectionEnabled;
            RtsMiningStateMachine.beginRemoteMining(player, session, pos, face, slot);
            return;
        }

        // Stop
        if (!RtsMiningValidator.isCommittedUltimineBatch(session)) {
            RtsMiningStateMachine.stopActiveMining(player, session);
        }
    }

    public static void startUltimine(ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face,
            byte toolSlot, String toolItemId, ItemStack toolPrototype, int requestedLimit, byte mode,
            boolean toolProtectionEnabled) {
        RtsUltimineProcessor.startUltimine(player, session, pos, face, toolSlot, toolItemId, toolPrototype,
                requestedLimit, mode, toolProtectionEnabled);
    }

    public static void areaMine(ServerPlayer player, RtsStorageSession session,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            byte toolSlot, String toolItemId, ItemStack toolPrototype,
            byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        RtsUltimineProcessor.areaMine(player, session, minX, maxX, minY, maxY, minZ, maxZ,
                toolSlot, toolItemId, toolPrototype, shapeType, fillType, toolProtectionEnabled);
    }

    public static void areaDestroy(ServerPlayer player, RtsStorageSession session, List<BlockPos> positions,
            byte toolSlot, String toolItemId, ItemStack toolPrototype, boolean toolProtectionEnabled) {
        RtsUltimineProcessor.areaDestroy(player, session, positions,
                toolSlot, toolItemId, toolPrototype, toolProtectionEnabled);
    }

    public static void tickActiveMining(ServerPlayer player, RtsStorageSession session) {
        RtsMiningStateMachine.tickActiveMining(player, session);
    }

    public static void stopActiveMining(ServerPlayer player, RtsStorageSession session) {
        RtsMiningStateMachine.stopActiveMining(player, session);
    }

    public static void markMiningStorageDirty(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) {
            return;
        }
        com.rtsbuilding.rtsbuilding.server.RtsStorageManager.markStorageViewDirty(player, session);
    }

    /**
     * @deprecated Only exposed for {@link RtsToolLease} javadoc reference.
     * Use {@link RtsMiningStateMachine#resetMiningState} directly.
     */
    @Deprecated
    public static void resetMiningState(RtsStorageSession session) {
        RtsMiningStateMachine.resetMiningState(session);
    }
}
