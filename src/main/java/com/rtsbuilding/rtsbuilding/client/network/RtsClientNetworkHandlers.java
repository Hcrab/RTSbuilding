package com.rtsbuilding.rtsbuilding.client.network;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.ClientFakeAirBlocks;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.ShapeGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.handler.PlacementHistoryManager;
import com.rtsbuilding.rtsbuilding.network.builder.*;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.S2CRtsPluginStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

public final class RtsClientNetworkHandlers {
    private RtsClientNetworkHandlers() {
    }

    public static void handleCameraState(S2CRtsCameraStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyServerCameraState(payload));
    }

    public static void handleStoragePage(S2CRtsStoragePagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyStoragePage(payload));
    }

    public static void handleStorageDirty(S2CRtsStorageDirtyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyStorageDirty(payload));
    }

    public static void handleRemoteMenuHint(S2CRtsRemoteMenuHintPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyRemoteMenuHint(payload));
    }

    public static void handleCraftables(S2CRtsCraftablesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyCraftables(payload));
    }

    public static void handleCraftFeedback(S2CRtsCraftFeedbackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyCraftFeedback(payload));
    }

    public static void handleDamageFeedback(S2CRtsDamageFeedbackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyDamageFeedback(payload));
    }

    public static void handleQuestDetectStatus(S2CRtsQuestDetectStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyQuestDetectStatus(payload));
    }

    public static void handleMineProgress(S2CRtsMineProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyMineProgress(payload));
    }

    public static void handlePlaceAnimation(S2CRtsPlaceAnimationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            PlacementAnimationRenderer.confirmPlacement(payload.pos(), payload.state());
        });
    }

    public static void handleBreakAnimation(S2CRtsBreakAnimationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientFakeAirBlocks.hideUntilServerState(payload.pos(), payload.state(), payload.resultState());
            PlacementAnimationRenderer.addDestroy(payload.pos(), payload.state());
            ShapeGhostRenderer.markDestroyed(payload.pos());
        });
    }

    public static void handleUltimineProgress(S2CRtsUltimineProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyUltimineProgress(payload));
    }

    public static void handleProgressionState(S2CRtsProgressionStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyProgressionState(payload));
    }

    public static void handlePluginState(S2CRtsPluginStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyPluginState(payload));
    }

    public static void handleHistorySync(S2CRtsHistorySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PlacementHistoryManager.syncHistoryState(payload.undoSize()));
    }
}
