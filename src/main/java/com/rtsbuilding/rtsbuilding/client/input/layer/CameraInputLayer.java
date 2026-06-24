package com.rtsbuilding.rtsbuilding.client.input.layer;

import com.rtsbuilding.rtsbuilding.client.input.InputLayer;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import org.lwjgl.glfw.GLFW;

/**
 * 相机输入层——处理鼠标拖拽和滚轮控制 RTS 摄像机。
 *
 * <p>当 RTS 模式激活时监听：
 * <ul>
 *   <li>鼠标右键拖拽 → 前后左右平移摄像机（{@link CameraModule#queueDragMove(double, double)}）</li>
 *   <li>鼠标中键拖拽 → 旋转摄像机视角（{@link CameraModule#queueRotateDrag(double, double)}）</li>
 *   <li>鼠标滚轮 → 推拉摄像机距离（{@link CameraModule#queueScroll(double)}）</li>
 * </ul>
 *
 * <p>右键/中键为固定绑定，不暴露给按键设置界面。
 */
public final class CameraInputLayer implements InputLayer {

    private final RtsClientKernel kernel;

    public CameraInputLayer(RtsClientKernel kernel) {
        this.kernel = kernel;
    }

    @Override
    public boolean isActive() {
        CameraModule cam = kernel.module("camera");
        return cam != null && cam.getState().isEnabled();
    }

    @Override
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        CameraModule cam = kernel.module("camera");
        if (cam == null) return false;

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            cam.queueRotateDrag(dragX, dragY);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            cam.queueDragMove(dragX, dragY);
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        CameraModule cam = kernel.module("camera");
        if (cam == null) return false;

        cam.queueScroll(scrollY);
        return true;
    }
}
