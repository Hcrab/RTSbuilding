package com.rtsbuilding.rtsbuilding.client.network;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.developer.RtsDeveloperScenarioTracker;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.ClientFakeAirBlocks;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.ShapeGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.handler.PlacementHistoryManager;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsBlueprintResumePanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsResumePlacementPanel;
import com.rtsbuilding.rtsbuilding.client.sound.RtsBlockActionSoundPlayer;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.network.builder.*;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraAnchorPayload;
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
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class RtsClientNetworkHandlers {
    private RtsClientNetworkHandlers() {
    }

    public static void handleCameraState(S2CRtsCameraStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyServerCameraState(payload));
    }

    public static void handleCameraAnchor(S2CRtsCameraAnchorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyServerCameraAnchor(payload));
    }

    public static void handleStoragePage(S2CRtsStoragePagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientRtsController.get().applyStoragePage(payload);
            RtsDeveloperScenarioTracker.getInstance().record("storage_page_received", "page=" + payload.page());
        });
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

    public static void handleUltimineProgress(S2CRtsUltimineProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRtsController.get().applyUltimineProgress(payload));
    }

    public static void handleHarvestTierSkipped(
            S2CRtsHarvestTierSkippedPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof BuilderScreen builderScreen) {
                builderScreen.getShapeController()
                        .removeConfirmedRangeDestroyPreviewBlocks(payload.positions());
            }
        });
    }

    public static void handlePlaceAnimation(S2CRtsPlaceAnimationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            PlacementAnimationRenderer.confirmPlacement(payload.pos(), payload.state());
            RtsDeveloperScenarioTracker.getInstance().record(
                    "place_confirmed", "pos=" + payload.pos().toShortString());
        });
    }

    public static void handleBreakAnimation(S2CRtsBreakAnimationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientFakeAirBlocks.hideUntilServerState(payload.pos(), payload.state(), payload.resultState());
            PlacementAnimationRenderer.addDestroy(payload.pos(), payload.state());
            ShapeGhostRenderer.markDestroyed(payload.pos());
            RtsDeveloperScenarioTracker.getInstance().record(
                    "break_confirmed", "pos=" + payload.pos().toShortString());
        });
    }

    public static void handleBlockActionSound(S2CRtsBlockActionSoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RtsBlockActionSoundPlayer.play(payload));
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

    public static void handleWorkflowProgress(S2CRtsWorkflowProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientRtsController.get().applyWorkflowProgress(payload);
            RtsDeveloperScenarioTracker.getInstance().record(
                    "workflow_update_received", "completed=" + payload.completedBlocks()
                            + ";total=" + payload.totalBlocks() + ";failed=" + payload.failedBlocks());
        });
    }

    public static void handleWorkflowProgressBatch(S2CRtsWorkflowProgressBatchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientRtsController.get().applyWorkflowProgressBatch(payload);
            RtsDeveloperScenarioTracker.getInstance().record(
                    "workflow_update_received", "entries=" + payload.entries().size());
        });
    }

    public static void handleResumePlacementScan(S2CRtsResumePlacementScanPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientRtsController controller = ClientRtsController.get();
            controller.applyResumePlacementScan(payload);
            // 打开重启面板
            if (Minecraft.getInstance().screen instanceof BuilderScreen bs) {
                RtsResumePlacementPanel panel = bs.getResumePlacementPanel();
                panel.openWithData(payload);
            }
        });
    }

    public static void handleBlueprintResumeScan(S2CRtsBlueprintResumeScanPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof BuilderScreen bs) {
                RtsBlueprintResumePanel panel = bs.getBlueprintResumePanel();
                panel.openWithData(payload);
            }
        });
    }

    public static void handleBlueprintStatus(S2CBlueprintStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BlueprintPanel.setStatus(payload.status(), payload.messageKey(), payload.detail()));
    }
}
