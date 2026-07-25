package com.rtsbuilding.rtsbuilding.client.camera;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;

/**
 * 在 GameRenderer 开始一帧渲染之前推进视觉镜头。
 *
 * <p>Forge 1.20.1 没有与 NeoForge {@code RenderFrameEvent.Pre} 等价的事件，
 * 因此由 {@code GameRendererMixin} 在帧入口调用。这里不监听世界渲染阶段，避免在同一帧
 * 已经使用旧镜头完成部分渲染后才更新视觉状态。</p>
 */
public final class RtsCameraRenderSync {
    private RtsCameraRenderSync() {
    }

    public static void beforeRenderFrame() {
        ClientRtsController.get().syncVisualCameraFrame();
    }
}
