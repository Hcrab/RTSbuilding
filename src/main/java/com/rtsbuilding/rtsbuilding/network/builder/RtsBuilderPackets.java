package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import com.rtsbuilding.rtsbuilding.network.builder.handler.RtsInteractionHandlers;
import com.rtsbuilding.rtsbuilding.network.builder.handler.RtsMiningHandlers;
import com.rtsbuilding.rtsbuilding.network.builder.handler.RtsPlaceHandlers;

import com.rtsbuilding.rtsbuilding.forgecompat.network.ForgePayloadRegistrar;

/**
 * Registers RTS placement, mining, interaction, and quick-drop packets.
 *
 * This class groups packet registration only; payload ids, codecs, and packet
 * directions stay in the payload records.
 */
public final class RtsBuilderPackets {
    private RtsBuilderPackets() {
    }

    public static void register(ForgePayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsSetModePayload.TYPE,
                C2SRtsSetModePayload.STREAM_CODEC,
                RtsPlaceHandlers::handleSetMode);

        registrar.playToServer(
                C2SRtsRotateBlockPayload.TYPE,
                C2SRtsRotateBlockPayload.STREAM_CODEC,
                RtsPlaceHandlers::handleRotateBlock);

        registrar.playToServer(
                C2SRtsPlacePayload.TYPE,
                C2SRtsPlacePayload.STREAM_CODEC,
                RtsPlaceHandlers::handlePlace);

        registrar.playToServer(
                C2SRtsPlaceBatchPayload.TYPE,
                C2SRtsPlaceBatchPayload.STREAM_CODEC,
                RtsPlaceHandlers::handlePlaceBatch);

        registrar.playToServer(
                C2SRtsPlaceFluidPayload.TYPE,
                C2SRtsPlaceFluidPayload.STREAM_CODEC,
                RtsPlaceHandlers::handlePlaceFluid);

        registrar.playToServer(
                C2SRtsStoreFluidPayload.TYPE,
                C2SRtsStoreFluidPayload.STREAM_CODEC,
                RtsPlaceHandlers::handleStoreFluid);

        registrar.playToServer(
                C2SRtsInteractPayload.TYPE,
                C2SRtsInteractPayload.STREAM_CODEC,
                RtsInteractionHandlers::handleInteract);

        registrar.playToServer(
                C2SRtsQuickDropPayload.TYPE,
                C2SRtsQuickDropPayload.STREAM_CODEC,
                RtsInteractionHandlers::handleQuickDrop);

        registrar.playToServer(
                C2SRtsBreakPayload.TYPE,
                C2SRtsBreakPayload.STREAM_CODEC,
                RtsInteractionHandlers::handleBreak);

        registrar.playToServer(
                C2SRtsMinePayload.TYPE,
                C2SRtsMinePayload.STREAM_CODEC,
                RtsMiningHandlers::handleMine);

        registrar.playToServer(
                C2SRtsUltiminePayload.TYPE,
                C2SRtsUltiminePayload.STREAM_CODEC,
                RtsMiningHandlers::handleUltimine);

        registrar.playToServer(
                C2SRtsAreaMinePayload.TYPE,
                C2SRtsAreaMinePayload.STREAM_CODEC,
                RtsMiningHandlers::handleAreaMine);

        registrar.playToServer(
                C2SRtsAreaDestroyPayload.TYPE,
                C2SRtsAreaDestroyPayload.STREAM_CODEC,
                RtsMiningHandlers::handleAreaDestroy);

        registrar.playToServer(
                C2SRtsPauseWorkflowPayload.TYPE,
                C2SRtsPauseWorkflowPayload.STREAM_CODEC,
                RtsInteractionHandlers::handlePauseWorkflow);

        registrar.playToServer(
                C2SRtsDeleteWorkflowPayload.TYPE,
                C2SRtsDeleteWorkflowPayload.STREAM_CODEC,
                RtsInteractionHandlers::handleDeleteWorkflow);

        registrar.playToServer(
                C2SRtsSetWorkflowProtectedPayload.TYPE,
                C2SRtsSetWorkflowProtectedPayload.STREAM_CODEC,
                RtsInteractionHandlers::handleSetWorkflowProtected);

        registrar.playToServer(
                C2SRtsScanResumePlacementPayload.TYPE,
                C2SRtsScanResumePlacementPayload.STREAM_CODEC,
                RtsInteractionHandlers::handleScanResumePlacement);

        registrar.playToServer(
                C2SRtsResumePlacementActionPayload.TYPE,
                C2SRtsResumePlacementActionPayload.STREAM_CODEC,
                RtsInteractionHandlers::handleResumePlacementAction);

        registrar.playToClient(
                S2CRtsMineProgressPayload.TYPE,
                S2CRtsMineProgressPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);

        registrar.playToClient(
                S2CRtsPlaceAnimationPayload.TYPE,
                S2CRtsPlaceAnimationPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);

        registrar.playToClient(
                S2CRtsBreakAnimationPayload.TYPE,
                S2CRtsBreakAnimationPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);

        registrar.playToClient(
                S2CRtsBlockActionSoundPayload.TYPE,
                S2CRtsBlockActionSoundPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);

        registrar.playToClient(
                S2CRtsUltimineProgressPayload.TYPE,
                S2CRtsUltimineProgressPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);

        registrar.playToClient(
                S2CRtsWorkflowProgressPayload.TYPE,
                S2CRtsWorkflowProgressPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);

        registrar.playToClient(
                S2CRtsWorkflowProgressBatchPayload.TYPE,
                S2CRtsWorkflowProgressBatchPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);

        registrar.playToClient(
                S2CRtsResumePlacementScanPayload.TYPE,
                S2CRtsResumePlacementScanPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);

        // ===== Undo =====

        registrar.playToServer(
                C2SRtsUndoPayload.TYPE,
                C2SRtsUndoPayload.STREAM_CODEC,
                RtsInteractionHandlers::handleUndo);

        registrar.playToClient(
                S2CRtsHistorySyncPayload.TYPE,
                S2CRtsHistorySyncPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);
    }
}
