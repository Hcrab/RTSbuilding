package com.rtsbuilding.rtsbuilding.client;

import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;

import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class RtsClientNetworkHandlers {
    private RtsClientNetworkHandlers() {
    }

    public static void handleCameraState(S2CRtsCameraStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyServerCameraState(payload));
    }

    public static void handleStoragePage(S2CRtsStoragePagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyStoragePage(payload));
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

    public static void handleProgressionState(S2CRtsProgressionStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyProgressionState(payload));
    }
}
