package com.rtsbuilding.rtsbuilding.client.network;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.mining.MiningModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.plugin.PluginModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.progression.ProgressionModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.workflow.WorkflowModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.network.builder.*;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraAnchorPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.message.S2CProgress;
import com.rtsbuilding.rtsbuilding.network.message.S2CStateUpdate;
import com.rtsbuilding.rtsbuilding.network.plugin.S2CRtsPluginStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;

public final class RtsClientNetworkHandlers {

    private RtsClientNetworkHandlers() {}

    private static RtsClientKernel kernel() {
        return RtsClientKernel.get();
    }

    
    
    

    public static void handleCameraAnchor(S2CRtsCameraAnchorPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            CameraModule cm = kernel().module(CameraModule.class);
            if (cm != null) cm.applyServerCameraAnchor(payload);
            
            kernel().updateRegion(payload.anchorX(), payload.anchorY(), payload.anchorZ(), payload.maxRadius());
        });
    }

    public static void handleCameraState(S2CRtsCameraStatePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            CameraModule cm = kernel().module(CameraModule.class);
            if (cm != null) cm.applyServerCameraState(payload);
            
            kernel().updateRegion(payload.anchorX(), payload.anchorY(), payload.anchorZ(), payload.maxRadius());
            
            kernel().dispatch(new StateEvent.RtsToggled(payload.enabled()));
        });
    }

    
    
    

    public static void handleStoragePage(S2CRtsStoragePagePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StorageModule sm = kernel().module(StorageModule.class);
            if (sm != null) sm.applyStoragePage(payload);
        });
    }

    public static void handleStorageDirty(S2CRtsStorageDirtyPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StorageModule sm = kernel().module(StorageModule.class);
            if (sm != null) sm.applyStorageDirty(payload);
        });
    }

    public static void handleCraftables(S2CRtsCraftablesPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StorageModule sm = kernel().module(StorageModule.class);
            if (sm != null) sm.applyCraftables(payload);
        });
    }

    public static void handleCraftFeedback(S2CRtsCraftFeedbackPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StorageModule sm = kernel().module(StorageModule.class);
            if (sm != null) sm.applyCraftFeedback(payload);
        });
    }

    
    
    

    public static void handleMineProgress(S2CRtsMineProgressPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            MiningModule mm = kernel().module(MiningModule.class);
            if (mm != null) mm.applyMineProgress(payload.pos(), payload.stage());
        });
    }

    public static void handleUltimineProgress(S2CRtsUltimineProgressPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            MiningModule mm = kernel().module(MiningModule.class);
            if (mm != null) mm.applyUltimineProgress(payload.processed(), payload.total());
        });
    }

    
    
    

    public static void handleWorkflowProgress(S2CRtsWorkflowProgressPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            WorkflowModule wm = kernel().module(WorkflowModule.class);
            if (wm != null) wm.applyWorkflowProgress(payload);
        });
    }

    public static void handleWorkflowProgressBatch(S2CRtsWorkflowProgressBatchPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            WorkflowModule wm = kernel().module(WorkflowModule.class);
            if (wm != null) {
                for (var entry : payload.entries()) {
                    wm.applyWorkflowProgress(entry);
                }
            }
        });
    }

    
    
    

    public static void handleProgressionState(S2CRtsProgressionStatePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ProgressionModule pm = kernel().module(ProgressionModule.class);
            if (pm != null) pm.applyProgressionState(payload, null);
        });
    }

    public static void handlePluginState(S2CRtsPluginStatePayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            PluginModule pm = kernel().module(PluginModule.class);
            if (pm != null) pm.applyPluginState(payload);
        });
    }

    
    
    

    public static void handleDamageFeedback(S2CRtsDamageFeedbackPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                kernel().dispatch(new StateEvent.DamageTaken(payload.amount(), false, 0)));
    }

    
    
    

    public static void handleRemoteMenuHint(S2CRtsRemoteMenuHintPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        
    }

    public static void handleQuestDetectStatus(S2CRtsQuestDetectStatusPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        
    }

    
    
    

    public static void handlePlaceAnimation(S2CRtsPlaceAnimationPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            
            com.rtsbuilding.rtsbuilding.client.render.RingBufferHolder.INSTANCE.add(
                    payload.pos(), payload.state(), System.currentTimeMillis());
        });
    }

    public static void handleBreakAnimation(S2CRtsBreakAnimationPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        
    }

    public static void handleHistorySync(S2CRtsHistorySyncPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        
    }

    public static void handleResumePlacementScan(S2CRtsResumePlacementScanPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        
    }

    public static void handleBlueprintResumeScan(S2CRtsBlueprintResumeScanPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        
    }

    public static void handleBlueprintStatus(S2CBlueprintStatusPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {

    }

    // ── Unified generic payload handlers ──

    public static void handleStateUpdate(S2CStateUpdate payload) {
        if (payload.data() == null) return;
        // TODO: dispatch based on payload.key()
        // e.g., "storage_page" → StorageModule, "progression" → ProgressionModule
    }

    public static void handleProgress(S2CProgress payload) {
        // TODO: forward to WorkflowModule
    }
}
