package com.rtsbuilding.rtsbuilding.client.camera;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsCameraRenderSync {
    private RtsCameraRenderSync() {
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        // 必须在 GameRenderer 使用镜头之前推进本帧姿态，避免画面总落后一帧。
        ClientRtsController.get().syncVisualCameraFrame();
    }
}

