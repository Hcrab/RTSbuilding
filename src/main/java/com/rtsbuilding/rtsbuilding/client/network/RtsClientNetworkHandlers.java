package com.rtsbuilding.rtsbuilding.client.network;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.developer.RtsDeveloperScenarioTracker;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.ClientFakeAirBlocks;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.ShapeGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
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
import com.rtsbuilding.rtsbuilding.network.culling.S2CRtsCullingStatePayload;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.S2CRtsPluginStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


@SideOnly(Side.CLIENT)
public final class RtsClientNetworkHandlers {
    private RtsClientNetworkHandlers() {
    }

    /** SimpleNetworkWrapper 的 Netty 线程不得直接修改客户端世界或 UI。 */
    private static void schedule(Runnable task) {
        Minecraft.getMinecraft().addScheduledTask(task);
    }

    public static void handleCameraState(S2CRtsCameraStatePayload payload) {
        schedule(() -> ClientRtsController.get().applyServerCameraState(payload));
    }

    public static void handleCameraAnchor(S2CRtsCameraAnchorPayload payload) {
        schedule(() -> ClientRtsController.get().applyServerCameraAnchor(payload));
    }

    public static void handleStoragePage(S2CRtsStoragePagePayload payload) {
        schedule(() -> {
            ClientRtsController.get().applyStoragePage(payload);
            RtsDeveloperScenarioTracker.getInstance().record("storage_page_received", "page=" + payload.page());
        });
    }

    public static void handleStorageDirty(S2CRtsStorageDirtyPayload payload) {
        schedule(() -> ClientRtsController.get().applyStorageDirty(payload));
    }

    public static void handleRemoteMenuHint(S2CRtsRemoteMenuHintPayload payload) {
        schedule(() -> ClientRtsController.get().applyRemoteMenuHint(payload));
    }

    public static void handleCraftables(S2CRtsCraftablesPayload payload) {
        schedule(() -> ClientRtsController.get().applyCraftables(payload));
    }

    public static void handleCraftFeedback(S2CRtsCraftFeedbackPayload payload) {
        schedule(() -> ClientRtsController.get().applyCraftFeedback(payload));
    }

    public static void handleCullingState(S2CRtsCullingStatePayload payload) {
        schedule(() -> RtsCullingClientState.applyCurrentWorldState(payload));
    }

    public static void handleDamageFeedback(S2CRtsDamageFeedbackPayload payload) {
        schedule(() -> ClientRtsController.get().applyDamageFeedback(payload));
    }

    public static void handleQuestDetectStatus(S2CRtsQuestDetectStatusPayload payload) {
        schedule(() -> ClientRtsController.get().applyQuestDetectStatus(payload));
    }

    public static void handleMineProgress(S2CRtsMineProgressPayload payload) {
        schedule(() -> ClientRtsController.get().applyMineProgress(payload));
    }

    public static void handleUltimineProgress(S2CRtsUltimineProgressPayload payload) {
        schedule(() -> ClientRtsController.get().applyUltimineProgress(payload));
    }

    public static void handleHarvestTierSkipped(
            S2CRtsHarvestTierSkippedPayload payload) {
        schedule(() -> {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.currentScreen instanceof BuilderScreen) {
                BuilderScreen builderScreen = (BuilderScreen) minecraft.currentScreen;
                builderScreen.getShapeController()
                        .removeConfirmedRangeDestroyPreviewBlocks(payload.positions());
            }
        });
    }

    public static void handlePlaceAnimation(S2CRtsPlaceAnimationPayload payload) {
        schedule(() -> {
            PlacementAnimationRenderer.confirmPlacement(payload.pos(), payload.state());
            RtsDeveloperScenarioTracker.getInstance().record(
                    "place_confirmed", "pos=" + payload.pos());
        });
    }

    public static void handleBreakAnimation(S2CRtsBreakAnimationPayload payload) {
        schedule(() -> {
            ClientFakeAirBlocks.hideUntilServerState(payload.pos(), payload.state(), payload.resultState());
            PlacementAnimationRenderer.addDestroy(payload.pos(), payload.state());
            ShapeGhostRenderer.markDestroyed(payload.pos());
            RtsDeveloperScenarioTracker.getInstance().record(
                    "break_confirmed", "pos=" + payload.pos());
        });
    }

    public static void handleBlockActionSound(S2CRtsBlockActionSoundPayload payload) {
        schedule(() -> RtsBlockActionSoundPlayer.play(payload));
    }

    public static void handleProgressionState(S2CRtsProgressionStatePayload payload) {
        schedule(() -> ClientRtsController.get().applyProgressionState(payload));
    }

    public static void handlePluginState(S2CRtsPluginStatePayload payload) {
        schedule(() -> ClientRtsController.get().applyPluginState(payload));
    }

    public static void handleHistorySync(S2CRtsHistorySyncPayload payload) {
        schedule(() -> PlacementHistoryManager.syncHistoryState(payload.undoSize()));
    }

    public static void handleWorkflowProgress(S2CRtsWorkflowProgressPayload payload) {
        schedule(() -> {
            ClientRtsController.get().applyWorkflowProgress(payload);
            RtsDeveloperScenarioTracker.getInstance().record(
                    "workflow_update_received", "completed=" + payload.completedBlocks()
                            + ";total=" + payload.totalBlocks() + ";failed=" + payload.failedBlocks());
        });
    }

    public static void handleWorkflowProgressBatch(S2CRtsWorkflowProgressBatchPayload payload) {
        schedule(() -> {
            ClientRtsController.get().applyWorkflowProgressBatch(payload);
            RtsDeveloperScenarioTracker.getInstance().record(
                    "workflow_update_received", "entries=" + payload.entries().size());
        });
    }

    public static void handleResumePlacementScan(S2CRtsResumePlacementScanPayload payload) {
        schedule(() -> {
            ClientRtsController controller = ClientRtsController.get();
            controller.applyResumePlacementScan(payload);
            // 打开重启面板
            if (Minecraft.getMinecraft().currentScreen instanceof BuilderScreen) {
                BuilderScreen bs = (BuilderScreen) Minecraft.getMinecraft().currentScreen;
                RtsResumePlacementPanel panel = bs.getResumePlacementPanel();
                panel.openWithData(payload);
            }
        });
    }

    public static void handleBlueprintResumeScan(S2CRtsBlueprintResumeScanPayload payload) {
        schedule(() -> {
            if (Minecraft.getMinecraft().currentScreen instanceof BuilderScreen) {
                BuilderScreen bs = (BuilderScreen) Minecraft.getMinecraft().currentScreen;
                RtsBlueprintResumePanel panel = bs.getBlueprintResumePanel();
                panel.openWithData(payload);
            }
        });
    }

    public static void handleBlueprintStatus(S2CBlueprintStatusPayload payload) {
        schedule(() -> BlueprintPanel.setStatus(payload.status(), payload.messageKey(), payload.detail()));
    }
}
