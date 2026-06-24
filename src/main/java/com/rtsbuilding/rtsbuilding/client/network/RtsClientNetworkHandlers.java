package com.rtsbuilding.rtsbuilding.client.network;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.module.mining.MiningModule;
import com.rtsbuilding.rtsbuilding.client.module.plugin.PluginModule;
import com.rtsbuilding.rtsbuilding.client.module.progression.ProgressionModule;
import com.rtsbuilding.rtsbuilding.client.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.module.workflow.WorkflowModule;
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

/**
 * 网络包处理器——服务端→客户端数据包分发到各 Feature Module。
 */
public final class RtsClientNetworkHandlers {

    private RtsClientNetworkHandlers() {}

    private static RtsClientKernel kernel() {
        return RtsClientKernel.get();
    }

    // ======================================================================
    //  Camera
    // ======================================================================

    public static void handleCameraAnchor(S2CRtsCameraAnchorPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            CameraModule cm = kernel().module("camera");
            if (cm != null) cm.applyServerCameraAnchor(payload);
            // 同步更新内核区域信息（独立于摄像机模块）
            kernel().updateRegion(payload.anchorX(), payload.anchorY(), payload.anchorZ(), payload.maxRadius());
        });
    }

    public static void handleCameraState(S2CRtsCameraStatePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            CameraModule cm = kernel().module("camera");
            if (cm != null) cm.applyServerCameraState(payload);
            // 同步更新内核区域信息（独立于摄像机模块）
            kernel().updateRegion(payload.anchorX(), payload.anchorY(), payload.anchorZ(), payload.maxRadius());
        });
    }

    // ======================================================================
    //  Storage
    // ======================================================================

    public static void handleStoragePage(S2CRtsStoragePagePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StorageModule sm = kernel().module("storage");
            if (sm != null) sm.applyStoragePage(payload);
        });
    }

    public static void handleStorageDirty(S2CRtsStorageDirtyPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StorageModule sm = kernel().module("storage");
            if (sm != null) sm.applyStorageDirty(payload);
        });
    }

    public static void handleCraftables(S2CRtsCraftablesPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StorageModule sm = kernel().module("storage");
            if (sm != null) sm.applyCraftables(payload);
        });
    }

    public static void handleCraftFeedback(S2CRtsCraftFeedbackPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StorageModule sm = kernel().module("storage");
            if (sm != null) sm.applyCraftFeedback(payload);
        });
    }

    // ======================================================================
    //  Mining
    // ======================================================================

    public static void handleMineProgress(S2CRtsMineProgressPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            MiningModule mm = kernel().module("mining");
            if (mm != null) mm.applyMineProgress(payload.pos(), payload.stage());
        });
    }

    public static void handleUltimineProgress(S2CRtsUltimineProgressPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            MiningModule mm = kernel().module("mining");
            if (mm != null) mm.applyUltimineProgress(payload.processed(), payload.total());
        });
    }

    // ======================================================================
    //  Workflow
    // ======================================================================

    public static void handleWorkflowProgress(S2CRtsWorkflowProgressPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            WorkflowModule wm = kernel().module("workflow");
            if (wm != null) wm.applyWorkflowProgress(payload);
        });
    }

    public static void handleWorkflowProgressBatch(S2CRtsWorkflowProgressBatchPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            WorkflowModule wm = kernel().module("workflow");
            if (wm != null) {
                for (var entry : payload.entries()) {
                    wm.applyWorkflowProgress(entry);
                }
            }
        });
    }

    // ======================================================================
    //  Progression & Plugin
    // ======================================================================

    public static void handleProgressionState(S2CRtsProgressionStatePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ProgressionModule pm = kernel().module("progression");
            if (pm != null) pm.applyProgressionState(payload, null);
        });
    }

    public static void handlePluginState(S2CRtsPluginStatePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            PluginModule pm = kernel().module("plugin");
            if (pm != null) pm.applyPluginState(payload);
        });
    }

    // ======================================================================
    //  Feedback
    // ======================================================================

    public static void handleDamageFeedback(S2CRtsDamageFeedbackPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                kernel().dispatch(new StateEvent.DamageTaken(payload.amount(), false, 0)));
    }

    // ======================================================================
    //  Others (pass-through to kernel events)
    // ======================================================================

    public static void handleRemoteMenuHint(S2CRtsRemoteMenuHintPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        // TODO: 远程菜单兼容
    }

    public static void handleQuestDetectStatus(S2CRtsQuestDetectStatusPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        // TODO: 任务检测逻辑
    }

    // ======================================================================
    //  Animation & History (stubs — delegated to old renderers)
    // ======================================================================

    public static void handlePlaceAnimation(S2CRtsPlaceAnimationPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 放置动画由 GhostRingBuffer 处理
            com.rtsbuilding.rtsbuilding.client.render.RingBufferHolder.INSTANCE.add(
                    payload.pos(), payload.state(), System.currentTimeMillis());
        });
    }

    public static void handleBreakAnimation(S2CRtsBreakAnimationPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        // 破坏动画暂不处理
    }

    public static void handleHistorySync(S2CRtsHistorySyncPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        // 撤销历史暂不处理
    }

    public static void handleResumePlacementScan(S2CRtsResumePlacementScanPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        // 恢复放置暂不处理
    }

    public static void handleBlueprintResumeScan(S2CRtsBlueprintResumeScanPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        // 蓝图恢复暂不处理
    }

    public static void handleBlueprintStatus(S2CBlueprintStatusPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        // 蓝图状态暂不处理
    }
}
