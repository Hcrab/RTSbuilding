package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBlockActionSoundPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBlueprintResumeScanPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBreakAnimationPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsHarvestTierSkippedPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsHistorySyncPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsPlaceAnimationPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsResumePlacementScanPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsUltimineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressBatchPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressPayload;
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

/** 客户端数据包领域到现有 UI/状态处理器的显式映射。 */
final class FabricClientPayloadSink {
    private FabricClientPayloadSink() {
    }

    static void dispatch(ClientPayloadDispatcher.Domain domain, Object payload, RtsPayloadContext context) {
        switch (domain) {
            case CAMERA -> dispatchCamera(payload, context);
            case STORAGE -> dispatchStorage(payload, context);
            case BUILDER -> dispatchBuilder(payload, context);
            case CRAFT -> dispatchCraft(payload, context);
            case PROGRESSION -> dispatchProgression(payload, context);
            case CULLING -> {
                if (payload instanceof S2CRtsCullingStatePayload value) {
                    RtsClientNetworkHandlers.handleCullingState(value, context);
                }
            }
            case PLUGIN -> {
                if (payload instanceof S2CRtsPluginStatePayload value) {
                    RtsClientNetworkHandlers.handlePluginState(value, context);
                }
            }
            case FEEDBACK -> {
                if (payload instanceof S2CRtsDamageFeedbackPayload value) {
                    RtsClientNetworkHandlers.handleDamageFeedback(value, context);
                }
            }
            case BLUEPRINT -> {
                if (payload instanceof S2CBlueprintStatusPayload value) {
                    RtsClientNetworkHandlers.handleBlueprintStatus(value, context);
                }
            }
        }
    }

    private static void dispatchCamera(Object payload, RtsPayloadContext context) {
        switch (payload) {
            case S2CRtsCameraStatePayload value -> RtsClientNetworkHandlers.handleCameraState(value, context);
            case S2CRtsCameraAnchorPayload value -> RtsClientNetworkHandlers.handleCameraAnchor(value, context);
            default -> { }
        }
    }

    private static void dispatchStorage(Object payload, RtsPayloadContext context) {
        switch (payload) {
            case S2CRtsStoragePagePayload value -> RtsClientNetworkHandlers.handleStoragePage(value, context);
            case S2CRtsStorageDirtyPayload value -> RtsClientNetworkHandlers.handleStorageDirty(value, context);
            case S2CRtsRemoteMenuHintPayload value -> RtsClientNetworkHandlers.handleRemoteMenuHint(value, context);
            default -> { }
        }
    }

    private static void dispatchBuilder(Object payload, RtsPayloadContext context) {
        switch (payload) {
            case S2CRtsMineProgressPayload value -> RtsClientNetworkHandlers.handleMineProgress(value, context);
            case S2CRtsUltimineProgressPayload value -> RtsClientNetworkHandlers.handleUltimineProgress(value, context);
            case S2CRtsHarvestTierSkippedPayload value -> RtsClientNetworkHandlers.handleHarvestTierSkipped(value, context);
            case S2CRtsPlaceAnimationPayload value -> RtsClientNetworkHandlers.handlePlaceAnimation(value, context);
            case S2CRtsBreakAnimationPayload value -> RtsClientNetworkHandlers.handleBreakAnimation(value, context);
            case S2CRtsBlockActionSoundPayload value -> RtsClientNetworkHandlers.handleBlockActionSound(value, context);
            case S2CRtsHistorySyncPayload value -> RtsClientNetworkHandlers.handleHistorySync(value, context);
            case S2CRtsWorkflowProgressPayload value -> RtsClientNetworkHandlers.handleWorkflowProgress(value, context);
            case S2CRtsWorkflowProgressBatchPayload value -> RtsClientNetworkHandlers.handleWorkflowProgressBatch(value, context);
            case S2CRtsResumePlacementScanPayload value -> RtsClientNetworkHandlers.handleResumePlacementScan(value, context);
            case S2CRtsBlueprintResumeScanPayload value -> RtsClientNetworkHandlers.handleBlueprintResumeScan(value, context);
            default -> { }
        }
    }

    private static void dispatchCraft(Object payload, RtsPayloadContext context) {
        switch (payload) {
            case S2CRtsCraftablesPayload value -> RtsClientNetworkHandlers.handleCraftables(value, context);
            case S2CRtsCraftFeedbackPayload value -> RtsClientNetworkHandlers.handleCraftFeedback(value, context);
            default -> { }
        }
    }

    private static void dispatchProgression(Object payload, RtsPayloadContext context) {
        switch (payload) {
            case S2CRtsProgressionStatePayload value -> RtsClientNetworkHandlers.handleProgressionState(value, context);
            case S2CRtsQuestDetectStatusPayload value -> RtsClientNetworkHandlers.handleQuestDetectStatus(value, context);
            default -> { }
        }
    }
}
