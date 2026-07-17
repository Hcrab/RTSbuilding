package com.rtsbuilding.rtsbuilding.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 26.1 的屏幕静态修饰键查询已移除，本类集中适配原生窗口按键状态。
 *
 * <p>它只回答当前物理按键状态，不参与 RTS 按键冲突、点击消费或拖拽状态机。
 */
public final class RtsModifierKeys {
    private RtsModifierKeys() {
    }

    public static boolean isShiftDown() {
        return isDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean isControlDown() {
        return isDown(GLFW.GLFW_KEY_LEFT_CONTROL) || isDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    public static boolean isAltDown() {
        return isDown(GLFW.GLFW_KEY_LEFT_ALT) || isDown(GLFW.GLFW_KEY_RIGHT_ALT);
    }

    private static boolean isDown(int key) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.getWindow() != null
                && InputConstants.isKeyDown(minecraft.getWindow(), key);
    }
}
