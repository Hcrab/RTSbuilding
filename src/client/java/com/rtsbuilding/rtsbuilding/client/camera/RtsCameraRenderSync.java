package com.rtsbuilding.rtsbuilding.client.camera;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;

public final class RtsCameraRenderSync {
    private RtsCameraRenderSync() {
    }

    public static void syncBeforeRender() {
        // 必须在 GameRenderer 使用镜头之前推进本帧姿态，避免画面总落后一帧。
        ClientRtsController.get().syncVisualCameraFrame();
    }
}
