package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.network.builder.*;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraAnchorPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.culling.S2CRtsCullingStatePayload;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.S2CRtsPluginStatePayload;
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
                    RtsClientNetworkHandlers.handleCameraState(p, ctx);
            case S2CRtsCameraAnchorPayload p ->
                    RtsClientNetworkHandlers.handleCameraAnchor(p, ctx);
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
                    RtsClientNetworkHandlers.handleStoragePage(p, ctx);
            case S2CRtsStorageDirtyPayload p ->
                    RtsClientNetworkHandlers.handleStorageDirty(p, ctx);
            case S2CRtsRemoteMenuHintPayload p ->
                    RtsClientNetworkHandlers.handleRemoteMenuHint(p, ctx);
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
                    RtsClientNetworkHandlers.handleMineProgress(p, ctx);
            case S2CRtsUltimineProgressPayload p ->
                    RtsClientNetworkHandlers.handleUltimineProgress(p, ctx);
            case S2CRtsHarvestTierSkippedPayload p ->
                    RtsClientNetworkHandlers.handleHarvestTierSkipped(p, ctx);
            case S2CRtsPlaceAnimationPayload p ->
                    RtsClientNetworkHandlers.handlePlaceAnimation(p, ctx);
            case S2CRtsBreakAnimationPayload p ->
                    RtsClientNetworkHandlers.handleBreakAnimation(p, ctx);
            case S2CRtsBlockActionSoundPayload p ->
                    RtsClientNetworkHandlers.handleBlockActionSound(p, ctx);
            case S2CRtsHistorySyncPayload p ->
                    RtsClientNetworkHandlers.handleHistorySync(p, ctx);
            case S2CRtsWorkflowProgressPayload p ->
                    RtsClientNetworkHandlers.handleWorkflowProgress(p, ctx);
            case S2CRtsWorkflowProgressBatchPayload p ->
                    RtsClientNetworkHandlers.handleWorkflowProgressBatch(p, ctx);
            case S2CRtsResumePlacementScanPayload p ->
                    RtsClientNetworkHandlers.handleResumePlacementScan(p, ctx);
            case S2CRtsBlueprintResumeScanPayload p ->
                    RtsClientNetworkHandlers.handleBlueprintResumeScan(p, ctx);
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
                    RtsClientNetworkHandlers.handleCraftables(p, ctx);
            case S2CRtsCraftFeedbackPayload p ->
                    RtsClientNetworkHandlers.handleCraftFeedback(p, ctx);
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
                    RtsClientNetworkHandlers.handleProgressionState(p, ctx);
            case S2CRtsQuestDetectStatusPayload p ->
                    RtsClientNetworkHandlers.handleQuestDetectStatus(p, ctx);
            default -> {}
        }
    }

    // ======================================================================
    //  Range-culling domain
    // ======================================================================

    public static void dispatchCulling(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        if (payload instanceof S2CRtsCullingStatePayload p) {
            RtsClientNetworkHandlers.handleCullingState(p, ctx);
        }
    }

    // ======================================================================
    //  Plugin domain
    // ======================================================================

    public static void dispatchPlugin(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        switch (payload) {
            case S2CRtsPluginStatePayload p ->
                    RtsClientNetworkHandlers.handlePluginState(p, ctx);
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
                    RtsClientNetworkHandlers.handleDamageFeedback(p, ctx);
            default -> {}
        }
    }

    // ======================================================================
    //  Blueprint domain
    // ======================================================================

    public static void dispatchBlueprintStatus(Object payload, IPayloadContext ctx) {
        if (!IS_CLIENT) return;
        switch (payload) {
            case S2CBlueprintStatusPayload p ->
                    RtsClientNetworkHandlers.handleBlueprintStatus(p, ctx);
            default -> {}
        }
    }
}
