package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class RtsClientPayloadBridge {
    private RtsClientPayloadBridge() {
    }

    public static void handleCameraState(S2CRtsCameraStatePayload payload, IPayloadContext context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleCameraState(payload, context));
    }

    public static void handleStoragePage(S2CRtsStoragePagePayload payload, IPayloadContext context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleStoragePage(payload, context));
    }

    public static void handleRemoteMenuHint(S2CRtsRemoteMenuHintPayload payload, IPayloadContext context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleRemoteMenuHint(payload, context));
    }

    public static void handleCraftables(S2CRtsCraftablesPayload payload, IPayloadContext context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleCraftables(payload, context));
    }

    public static void handleCraftFeedback(S2CRtsCraftFeedbackPayload payload, IPayloadContext context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleCraftFeedback(payload, context));
    }

    public static void handleDamageFeedback(S2CRtsDamageFeedbackPayload payload, IPayloadContext context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleDamageFeedback(payload, context));
    }

    public static void handleQuestDetectStatus(S2CRtsQuestDetectStatusPayload payload, IPayloadContext context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleQuestDetectStatus(payload, context));
    }

    public static void handleMineProgress(S2CRtsMineProgressPayload payload, IPayloadContext context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleMineProgress(payload, context));
    }

    public static void handleUltimineProgress(S2CRtsUltimineProgressPayload payload, IPayloadContext context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleUltimineProgress(payload, context));
    }

    public static void handleProgressionState(S2CRtsProgressionStatePayload payload, IPayloadContext context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleProgressionState(payload, context));
    }
}
