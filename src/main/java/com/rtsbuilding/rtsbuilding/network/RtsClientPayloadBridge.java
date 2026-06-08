package com.rtsbuilding.rtsbuilding.network;


import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBreakAnimationPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsPlaceAnimationPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsUltimineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Common-side S2C bridge that keeps dedicated servers from loading client-only
 * handler classes.
 *
 * Payload registration happens from common mod code, including on Linux
 * dedicated servers. Keep the direct client handler calls behind this dist
 * check unless the whole registration path becomes client-only.
 */
public final class RtsClientPayloadBridge {
    private RtsClientPayloadBridge() {
    }

    public static void handleCameraState(S2CRtsCameraStatePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleCameraState(payload, context);
        }
    }

    public static void handleStoragePage(S2CRtsStoragePagePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleStoragePage(payload, context);
        }
    }

    public static void handleStorageDirty(S2CRtsStorageDirtyPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleStorageDirty(payload, context);
        }
    }

    public static void handleRemoteMenuHint(S2CRtsRemoteMenuHintPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleRemoteMenuHint(payload, context);
        }
    }

    public static void handleCraftables(S2CRtsCraftablesPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleCraftables(payload, context);
        }
    }

    public static void handleCraftFeedback(S2CRtsCraftFeedbackPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleCraftFeedback(payload, context);
        }
    }

    public static void handleDamageFeedback(S2CRtsDamageFeedbackPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleDamageFeedback(payload, context);
        }
    }

    public static void handleQuestDetectStatus(S2CRtsQuestDetectStatusPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleQuestDetectStatus(payload, context);
        }
    }

    public static void handleMineProgress(S2CRtsMineProgressPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleMineProgress(payload, context);
        }
    }

    public static void handlePlaceAnimation(S2CRtsPlaceAnimationPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handlePlaceAnimation(payload, context);
        }
    }

    public static void handleBreakAnimation(S2CRtsBreakAnimationPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleBreakAnimation(payload, context);
        }
    }

    public static void handleUltimineProgress(S2CRtsUltimineProgressPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleUltimineProgress(payload, context);
        }
    }

    public static void handleProgressionState(S2CRtsProgressionStatePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleProgressionState(payload, context);
        }
    }
}
