package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

/**
 * Unified S2C dispatch bridge that keeps dedicated servers from loading
 * client-only handler classes.
 *
 * <p>Each domain gets one dispatch method. Forge 1.20.1 stays on Java 17, so
 * this bridge uses plain {@code instanceof} checks instead of switch patterns.
 *
 * <p>The {@code IS_CLIENT} guard ensures {@code RtsClientNetworkHandlers} is
 * never loaded on dedicated server runtimes.
 */
public final class ClientPayloadDispatcher {
    private static final boolean IS_CLIENT = FMLEnvironment.dist == Dist.CLIENT;

    private ClientPayloadDispatcher() {
    }

    // ======================================================================
    //  Camera domain
    // ======================================================================

    public static void dispatchCamera(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        if (payload instanceof S2CRtsCameraStatePayload p) {
            RtsClientNetworkHandlers.handleCameraState(p, ctx);
        }
    }

    // ======================================================================
    //  Storage domain
    // ======================================================================

    public static void dispatchStorage(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        if (payload instanceof S2CRtsStoragePagePayload p) {
            RtsClientNetworkHandlers.handleStoragePage(p, ctx);
        } else if (payload instanceof S2CRtsStorageDirtyPayload p) {
            RtsClientNetworkHandlers.handleStorageDirty(p, ctx);
        } else if (payload instanceof S2CRtsRemoteMenuHintPayload p) {
            RtsClientNetworkHandlers.handleRemoteMenuHint(p, ctx);
        }
    }

    // ======================================================================
    //  Builder domain
    // ======================================================================

    public static void dispatchBuilder(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        if (payload instanceof S2CRtsMineProgressPayload p) {
            RtsClientNetworkHandlers.handleMineProgress(p, ctx);
        } else if (payload instanceof S2CRtsPlaceAnimationPayload p) {
            RtsClientNetworkHandlers.handlePlaceAnimation(p, ctx);
        } else if (payload instanceof S2CRtsBreakAnimationPayload p) {
            RtsClientNetworkHandlers.handleBreakAnimation(p, ctx);
        } else if (payload instanceof S2CRtsUltimineProgressPayload p) {
            RtsClientNetworkHandlers.handleUltimineProgress(p, ctx);
        } else if (payload instanceof S2CRtsHistorySyncPayload p) {
            RtsClientNetworkHandlers.handleHistorySync(p, ctx);
        }
    }

    // ======================================================================
    //  Craft domain
    // ======================================================================

    public static void dispatchCraft(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        if (payload instanceof S2CRtsCraftablesPayload p) {
            RtsClientNetworkHandlers.handleCraftables(p, ctx);
        } else if (payload instanceof S2CRtsCraftFeedbackPayload p) {
            RtsClientNetworkHandlers.handleCraftFeedback(p, ctx);
        }
    }

    // ======================================================================
    //  Progression domain
    // ======================================================================

    public static void dispatchProgression(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        if (payload instanceof S2CRtsProgressionStatePayload p) {
            RtsClientNetworkHandlers.handleProgressionState(p, ctx);
        } else if (payload instanceof S2CRtsQuestDetectStatusPayload p) {
            RtsClientNetworkHandlers.handleQuestDetectStatus(p, ctx);
        }
    }

    // ======================================================================
    //  Plugin domain
    // ======================================================================

    public static void dispatchPlugin(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        if (payload instanceof S2CRtsPluginStatePayload p) {
            RtsClientNetworkHandlers.handlePluginState(p, ctx);
        }
    }

    // ======================================================================
    //  Feedback domain
    // ======================================================================

    public static void dispatchFeedback(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        if (payload instanceof S2CRtsDamageFeedbackPayload p) {
            RtsClientNetworkHandlers.handleDamageFeedback(p, ctx);
        }
    }
}
