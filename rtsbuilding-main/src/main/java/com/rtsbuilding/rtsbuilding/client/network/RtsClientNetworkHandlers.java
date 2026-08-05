package com.rtsbuilding.rtsbuilding.client.network;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.mining.MiningModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.workflow.WorkflowModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.common.entity.RtsDroneEntity;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.network.builder.*;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraAnchorPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsDroneAnimPayload;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsCarriedSyncPayload;
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
            long perfT0 = System.nanoTime();
            CameraModule cm = kernel().module(CameraModule.class);
            if (cm != null) cm.applyServerCameraState(payload);
            long perfT1 = System.nanoTime();
            kernel().updateRegion(payload.anchorX(), payload.anchorY(), payload.anchorZ(), payload.maxRadius());
            kernel().dispatch(new StateEvent.RtsToggled(payload.enabled()));
            long perfT2 = System.nanoTime();

            long perfEnableMs = (perfT1 - perfT0) / 1_000_000L;
            long perfDispatchMs = (perfT2 - perfT1) / 1_000_000L;
            if (perfEnableMs >= 30L || perfDispatchMs >= 30L) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                net.minecraft.world.entity.player.Player p = mc.player;
                com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER.info(
                        "RTS-PERF: handleCameraState enableCamera={} ms, dispatch(screen)={} ms (enabled={}, player={})",
                        perfEnableMs, perfDispatchMs, payload.enabled(), p == null ? "?" : p.getName().getString());
            }
        });
    }

    /**
     * 处理无人机动画同步包：把服务端每 tick 下发的动画状态写入无人机的
     * prev/current 插值缓存，供渲染层 partialTick 插值（消除动画跳变/卡顿）。
     */
    public static void handleDroneAnim(S2CRtsDroneAnimPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;
            net.minecraft.world.entity.Entity e = mc.level.getEntity(payload.entityId());
            if (e instanceof RtsDroneEntity drone) {
                drone.receiveAnimState(payload.x(), payload.y(), payload.z(),
                        payload.yawDeg(), payload.pitchDeg(), payload.tiltX(), payload.tiltZ());
            }
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
                wm.resetProgress();
                for (var entry : payload.entries()) {
                    wm.applyWorkflowProgress(entry);
                }
            }
        });
    }

    
    
    

    public static void handleDamageFeedback(S2CRtsDamageFeedbackPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            float health = mc.player != null ? mc.player.getHealth() : 0.0F;
            kernel().dispatch(new StateEvent.DamageTaken(payload.amount(), payload.lowHealth(), health));
        });
    }

    
    
    

    /**
     * Mirrors the authoritative server carried stack into the client container menu.
     * Called after linked-storage pickup/return so the container overlay and drag
     * interactions render the correct carried item.
     */
    public static void handleCarriedSync(S2CRtsCarriedSyncPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu != null) {
                mc.player.containerMenu.setCarried(payload.stack());
            }
        });
    }

    public static void handlePlaceAnimation(S2CRtsPlaceAnimationPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            
            com.rtsbuilding.rtsbuilding.client.render.RingBufferHolder.INSTANCE.add(
                    payload.pos(), payload.state(), System.currentTimeMillis());
        });
    }

    public static void handleBreakAnimation(S2CRtsBreakAnimationPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;
            // 清除该位置残留裂纹，并触发方块破坏粒子反馈（2001 = 破坏粒子事件）
            mc.level.destroyBlockProgress(0x525453, payload.pos(), -1);
            mc.level.levelEvent(2001, payload.pos(),
                    net.minecraft.world.level.block.Block.getId(payload.state()));
        });
    }

    public static void handleHistorySync(S2CRtsHistorySyncPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                kernel().dispatch(new StateEvent.Custom("history_sync", payload.undoSize())));
    }

    public static void handleBlueprintStatus(S2CBlueprintStatusPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            String messageKey = payload.messageKey();
            if (messageKey == null || messageKey.isBlank()) return;
            net.minecraft.network.chat.Component message = net.minecraft.network.chat.Component.translatable(messageKey);
            String detail = payload.detail();
            if (detail != null && !detail.isBlank()) {
                message = message.copy().append(" ").append(net.minecraft.network.chat.Component.literal(detail));
            }
            String prefix = switch (payload.status()) {
                case S2CBlueprintStatusPayload.SUCCESS -> "§a[蓝图] ";
                case S2CBlueprintStatusPayload.ERROR -> "§c[蓝图] ";
                default -> "§7[蓝图] ";
            };
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(prefix).copy().append(message), true);
        });
    }
}
