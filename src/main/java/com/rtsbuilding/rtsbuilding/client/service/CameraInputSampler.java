package com.rtsbuilding.rtsbuilding.client.service;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 从 Minecraft 当前屏幕和按键映射读取一帧相机移动意图。
 *
 * <p>只采样输入并处理搜索框抑制，不累计、不平滑、不发包；因此键位兼容变更
 * 不再要求阅读完整轨道相机状态机。</p>
 */
final class CameraInputSampler {
    private CameraInputSampler() {}

    static Input read(Minecraft minecraft) {
        BuilderScreen screen = minecraft.screen instanceof BuilderScreen value ? value : null;
        if (screen != null && screen.isSearchFocused()) return Input.NONE;
        long window = minecraft.getWindow().getWindow();
        boolean w = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W);
        boolean s = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S);
        boolean a = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A);
        boolean d = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D);
        boolean up = ClientKeyMappings.CAMERA_UP.isDown() || ClientKeyMappings.CAMERA_UP_SECONDARY.isDown()
                || screen != null && screen.isCameraUpActionHeld();
        boolean down = ClientKeyMappings.CAMERA_DOWN.isDown() || screen != null && screen.isCameraDownActionHeld();
        return new Input((w ? 1.0F : 0.0F) - (s ? 1.0F : 0.0F),
                (a ? 1.0F : 0.0F) - (d ? 1.0F : 0.0F),
                (up ? 1.0F : 0.0F) - (down ? 1.0F : 0.0F), minecraft.options.keySprint.isDown());
    }

    record Input(float forward, float strafe, float vertical, boolean fast) {
        private static final Input NONE = new Input(0.0F, 0.0F, 0.0F, false);
    }
}
