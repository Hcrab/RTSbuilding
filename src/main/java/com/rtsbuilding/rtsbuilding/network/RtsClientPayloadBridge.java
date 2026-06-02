package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class RtsClientPayloadBridge {
    private RtsClientPayloadBridge() {
    }

    public static void handleCameraState(S2CRtsCameraStatePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleCameraState(payload, context);
        }
    }

    public static void handleStoragePage(S2CRtsStoragePagePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleStoragePage(payload, context);
        }
    }

    public static void handleRemoteMenuHint(S2CRtsRemoteMenuHintPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleRemoteMenuHint(payload, context);
        }
    }

    public static void handleCraftables(S2CRtsCraftablesPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleCraftables(payload, context);
        }
    }

    public static void handleCraftFeedback(S2CRtsCraftFeedbackPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleCraftFeedback(payload, context);
        }
    }

    public static void handleDamageFeedback(S2CRtsDamageFeedbackPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleDamageFeedback(payload, context);
        }
    }

    public static void handleQuestDetectStatus(S2CRtsQuestDetectStatusPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleQuestDetectStatus(payload, context);
        }
    }

    public static void handleMineProgress(S2CRtsMineProgressPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleMineProgress(payload, context);
        }
    }

    public static void handleProgressionState(S2CRtsProgressionStatePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.RtsClientNetworkHandlers.handleProgressionState(payload, context);
        }
    }
}
