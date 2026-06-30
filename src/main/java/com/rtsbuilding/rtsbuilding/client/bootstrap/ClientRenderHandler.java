package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 客户端渲染帧处理器——驱动 {@link RtsClientKernel#onRenderFrame(float, PoseStack)} 与渲染管线。
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class ClientRenderHandler {

    private ClientRenderHandler() {}

    /**
     * 渲染帧前处理——在每帧 {@code Camera.setup()} 之前调用，
     * 使轨道模式的摄像机能在帧率级更新位置，实现平滑圆周运动。
     */
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

        // 允许渲染条件：摄像机开启 或 已收到区域锚点信息（边界独立于摄像机）
        CameraModule cam = kernel.module(CameraModule.class);
        boolean cameraEnabled = cam != null && cam.getState().isEnabled();
        if (!cameraEnabled && !kernel.isRegionValid()) return;

        // 和旧版一样：使用 event 的 PoseStack + translate(-camPos) 转换到世界坐标空间
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        // onRenderFrame 内部已自动调用 reset()，无需在此额外调用
        kernel.onRenderFrame(event.getPartialTick().getGameTimeDeltaPartialTick(false), poseStack);

        poseStack.popPose();
    }
}
