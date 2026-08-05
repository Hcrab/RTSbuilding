package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class ClientRenderHandler {

    private ClientRenderHandler() {}

    
    @SubscribeEvent
    public static void onRenderFramePre(RenderFrameEvent.Pre event) {
        RtsClientKernel kernel = RtsClientKernel.get();
        if (!kernel.isInitialized()) return;
        CameraModule cam = kernel.module(CameraModule.class);
        if (cam != null) {
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            cam.onRenderFrame(partialTick);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        RtsClientKernel kernel = RtsClientKernel.get();
        if (!kernel.isInitialized()) return;

        
        CameraModule cam = kernel.module(CameraModule.class);
        boolean cameraEnabled = cam != null && cam.getState().isEnabled();
        if (!cameraEnabled && !kernel.isRegionValid()) return;

        
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        
        kernel.onRenderFrame(event.getPartialTick().getGameTimeDeltaPartialTick(false), poseStack);

        poseStack.popPose();
    }
}
