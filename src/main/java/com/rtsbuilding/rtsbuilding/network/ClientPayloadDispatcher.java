package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBreakAnimationPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsHistorySyncPayload;
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
 * Unified S2C dispatch bridge that keeps dedicated servers from loading
 * client-only handler classes.
 *
 * <p>Each domain gets one dispatch method using Java 21 pattern matching,
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
        switch (payload) {
            case S2CRtsCameraStatePayload p ->
                    com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleCameraState(p, ctx);
            default -> {}
        }
    }

    // ======================================================================
    //  Storage domain
    // ======================================================================

    public static void dispatchStorage(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        switch (payload) {
            case S2CRtsStoragePagePayload p ->
                    com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleStoragePage(p, ctx);
            case S2CRtsStorageDirtyPayload p ->
                    com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleStorageDirty(p, ctx);
            case S2CRtsRemoteMenuHintPayload p ->
                    com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleRemoteMenuHint(p, ctx);
            default -> {}
        }
    }

    // ======================================================================
    //  Builder domain
    // ======================================================================

    public static void dispatchBuilder(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        switch (payload) {
            case S2CRtsMineProgressPayload p ->
                    com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleMineProgress(p, ctx);
            case S2CRtsPlaceAnimationPayload p ->
                    com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handlePlaceAnimation(p, ctx);
            case S2CRtsBreakAnimationPayload p ->
                    com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleBreakAnimation(p, ctx);
            case S2CRtsUltimineProgressPayload p ->
                    com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleUltimineProgress(p, ctx);
            case S2CRtsHistorySyncPayload p ->
                    com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleHistorySync(p, ctx);
            default -> {}
        }
    }

    // ======================================================================
    //  Craft domain
    // ======================================================================

    public static void dispatchCraft(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        switch (payload) {
            case S2CRtsCraftablesPayload p ->
                    com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleCraftables(p, ctx);
            case S2CRtsCraftFeedbackPayload p ->
                    com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleCraftFeedback(p, ctx);
            default -> {}
        }
    }

    // ======================================================================
    //  Progression domain
    // ======================================================================

    public static void dispatchProgression(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        switch (payload) {
            case S2CRtsProgressionStatePayload p ->
                    com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleProgressionState(p, ctx);
            case S2CRtsQuestDetectStatusPayload p ->
                    com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleQuestDetectStatus(p, ctx);
            default -> {}
        }
    }

    // ======================================================================
    //  Feedback domain
    // ======================================================================

    public static void dispatchFeedback(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        switch (payload) {
            case S2CRtsDamageFeedbackPayload p ->
                    com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers.handleDamageFeedback(p, ctx);
            default -> {}
        }
    }
}
