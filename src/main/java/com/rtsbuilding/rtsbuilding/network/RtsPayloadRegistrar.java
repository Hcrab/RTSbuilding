package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.blueprint.network.BlueprintPayloadRegistrar;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class RtsPayloadRegistrar {
    private RtsPayloadRegistrar() {
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                C2SRtsToggleCameraPayload.TYPE,
                C2SRtsToggleCameraPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleToggle);

        registrar.playToServer(
                C2SRtsCameraMovePayload.TYPE,
                C2SRtsCameraMovePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleMove);

        registrar.playToServer(
                C2SRtsSetModePayload.TYPE,
                C2SRtsSetModePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetMode);

        registrar.playToServer(
                C2SRtsSetFunnelPayload.TYPE,
                C2SRtsSetFunnelPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetFunnel);

        registrar.playToServer(
                C2SRtsSetAutoStorePayload.TYPE,
                C2SRtsSetAutoStorePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetAutoStore);

        registrar.playToServer(
                C2SRtsSetBdNetworkPayload.TYPE,
                C2SRtsSetBdNetworkPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetBdNetwork);

        registrar.playToServer(
                C2SRtsLinkStoragePayload.TYPE,
                C2SRtsLinkStoragePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleLinkStorage);

        registrar.playToServer(
                C2SRtsRotateBlockPayload.TYPE,
                C2SRtsRotateBlockPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleRotateBlock);

        registrar.playToServer(
                C2SRtsStoreHotbarSlotPayload.TYPE,
                C2SRtsStoreHotbarSlotPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleStoreHotbarSlot);

        registrar.playToServer(
                C2SRtsSetQuickSlotPayload.TYPE,
                C2SRtsSetQuickSlotPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetQuickSlot);

        registrar.playToServer(
                C2SRtsSetGuiBindingPayload.TYPE,
                C2SRtsSetGuiBindingPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetGuiBinding);

        registrar.playToServer(
                C2SRtsOpenGuiBindingPayload.TYPE,
                C2SRtsOpenGuiBindingPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleOpenGuiBinding);

        registrar.playToServer(
                C2SRtsRequestStoragePagePayload.TYPE,
                C2SRtsRequestStoragePagePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleRequestStoragePage);

        registrar.playToServer(
                C2SRtsRequestCraftablesPayload.TYPE,
                C2SRtsRequestCraftablesPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleRequestCraftables);

        registrar.playToServer(
                C2SRtsPlacePayload.TYPE,
                C2SRtsPlacePayload.STREAM_CODEC,
                RtsNetworkHandlers::handlePlace);

        registrar.playToServer(
                C2SRtsPlaceBatchPayload.TYPE,
                C2SRtsPlaceBatchPayload.STREAM_CODEC,
                RtsNetworkHandlers::handlePlaceBatch);

        registrar.playToServer(
                C2SRtsPlaceFluidPayload.TYPE,
                C2SRtsPlaceFluidPayload.STREAM_CODEC,
                RtsNetworkHandlers::handlePlaceFluid);

        registrar.playToServer(
                C2SRtsStoreFluidPayload.TYPE,
                C2SRtsStoreFluidPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleStoreFluid);

        registrar.playToServer(
                C2SRtsInteractPayload.TYPE,
                C2SRtsInteractPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleInteract);

        registrar.playToServer(
                C2SRtsQuickDropPayload.TYPE,
                C2SRtsQuickDropPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleQuickDrop);

        registrar.playToServer(
                C2SRtsBreakPayload.TYPE,
                C2SRtsBreakPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleBreak);

        registrar.playToServer(
                C2SRtsMinePayload.TYPE,
                C2SRtsMinePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleMine);

        registrar.playToServer(
                C2SRtsUltiminePayload.TYPE,
                C2SRtsUltiminePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleUltimine);

        registrar.playToServer(
                C2SRtsFunnelTargetPayload.TYPE,
                C2SRtsFunnelTargetPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleFunnelTarget);

        registrar.playToServer(
                C2SRtsFillInventoryPayload.TYPE,
                C2SRtsFillInventoryPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleFillInventory);

        registrar.playToServer(
                C2SRtsLinkedPickupPayload.TYPE,
                C2SRtsLinkedPickupPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleLinkedPickup);

        registrar.playToServer(
                C2SRtsLinkedQuickMovePayload.TYPE,
                C2SRtsLinkedQuickMovePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleLinkedQuickMove);

        registrar.playToServer(
                C2SRtsReturnCarriedPayload.TYPE,
                C2SRtsReturnCarriedPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleReturnCarried);

        registrar.playToServer(
                C2SRtsOpenCraftTerminalPayload.TYPE,
                C2SRtsOpenCraftTerminalPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleOpenCraftTerminal);

        registrar.playToServer(
                C2SRtsImportMenuSlotPayload.TYPE,
                C2SRtsImportMenuSlotPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleImportMenuSlot);

        registrar.playToServer(
                C2SRtsCraftRefillPayload.TYPE,
                C2SRtsCraftRefillPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleCraftRefill);

        registrar.playToServer(
                C2SRtsCraftRecipePayload.TYPE,
                C2SRtsCraftRecipePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleCraftRecipe);

        registrar.playToServer(
                C2SRtsJeiTransferPayload.TYPE,
                C2SRtsJeiTransferPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleJeiTransfer);

        registrar.playToServer(
                C2SRtsQuestDetectPayload.TYPE,
                C2SRtsQuestDetectPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleQuestDetect);

        registrar.playToServer(
                C2SRtsUnlockProgressionNodePayload.TYPE,
                C2SRtsUnlockProgressionNodePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleUnlockProgressionNode);

        registrar.playToServer(
                C2SRtsSetSurvivalProgressionPayload.TYPE,
                C2SRtsSetSurvivalProgressionPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetSurvivalProgression);

        registrar.playToServer(
                C2SRtsSetProgressionCostPayload.TYPE,
                C2SRtsSetProgressionCostPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetProgressionCost);

        registrar.playToServer(
                C2SRtsSetHomePayload.TYPE,
                C2SRtsSetHomePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetHome);

        registrar.playToServer(
                C2SRtsBeginHomeSelectionPayload.TYPE,
                C2SRtsBeginHomeSelectionPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleBeginHomeSelection);

        registrar.playToServer(
                C2SRtsRequestProgressionStatePayload.TYPE,
                C2SRtsRequestProgressionStatePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleRequestProgressionState);

        registrar.playToClient(
                S2CRtsCameraStatePayload.TYPE,
                S2CRtsCameraStatePayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleCameraState);

        registrar.playToClient(
                S2CRtsStoragePagePayload.TYPE,
                S2CRtsStoragePagePayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleStoragePage);

        registrar.playToClient(
                S2CRtsRemoteMenuHintPayload.TYPE,
                S2CRtsRemoteMenuHintPayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleRemoteMenuHint);

        registrar.playToClient(
                S2CRtsCraftablesPayload.TYPE,
                S2CRtsCraftablesPayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleCraftables);

        registrar.playToClient(
                S2CRtsCraftFeedbackPayload.TYPE,
                S2CRtsCraftFeedbackPayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleCraftFeedback);

        registrar.playToClient(
                S2CRtsQuestDetectStatusPayload.TYPE,
                S2CRtsQuestDetectStatusPayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleQuestDetectStatus);

        registrar.playToClient(
                S2CRtsMineProgressPayload.TYPE,
                S2CRtsMineProgressPayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleMineProgress);

        registrar.playToClient(
                S2CRtsProgressionStatePayload.TYPE,
                S2CRtsProgressionStatePayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleProgressionState);

        BlueprintPayloadRegistrar.register(registrar);
    }
}
