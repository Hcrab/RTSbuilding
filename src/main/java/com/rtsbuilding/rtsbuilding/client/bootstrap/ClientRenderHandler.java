package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 客户端渲染帧处理器——驱动 {@link RtsClientKernel#onRenderFrame(float, PoseStack)} 与渲染管线。
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class ClientRenderHandler {

    private ClientRenderHandler() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        RtsClientKernel kernel = RtsClientKernel.get();
        if (!kernel.isInitialized()) return;

        // 允许渲染条件：摄像机开启 或 已收到区域锚点信息（边界独立于摄像机）
        CameraModule cam = kernel.module("camera");
        boolean cameraEnabled = cam != null && cam.getState().isEnabled();
        if (!cameraEnabled && !kernel.isRegionValid()) return;

        // 和旧版一样：使用 event 的 PoseStack + translate(-camPos) 转换到世界坐标空间
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        kernel.renderPipeline().reset();
        kernel.onRenderFrame(event.getPartialTick().getGameTimeDeltaPartialTick(false), poseStack);

        poseStack.popPose();
    }
}
