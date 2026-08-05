package com.rtsbuilding.rtsbuilding.client.service;

import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Method;

/** 每帧读取一次相机移动意图，不拥有累积、平滑或网络状态。 */
final class CameraInputSampler {
    private static final String BUILDER_SCREEN =
            "com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen";

    private CameraInputSampler() {}

    static Input read(Minecraft minecraft) {
        GuiScreen screen = minecraft == null ? null : minecraft.currentScreen;
        boolean builder = isBuilderScreen(screen);
        if (builder && invokeBoolean(screen, "isSearchFocused")) return Input.NONE;
        boolean w = Keyboard.isKeyDown(Keyboard.KEY_W);
        boolean s = Keyboard.isKeyDown(Keyboard.KEY_S);
        boolean a = Keyboard.isKeyDown(Keyboard.KEY_A);
        boolean d = Keyboard.isKeyDown(Keyboard.KEY_D);
        boolean up = ClientKeyMappings.CAMERA_UP.getIsKeyPressed()
                || ClientKeyMappings.CAMERA_UP_SECONDARY.getIsKeyPressed()
                || builder && invokeBoolean(screen, "isCameraUpActionHeld");
        boolean down = ClientKeyMappings.CAMERA_DOWN.getIsKeyPressed()
                || builder && invokeBoolean(screen, "isCameraDownActionHeld");
        boolean fast = minecraft != null && minecraft.gameSettings.keyBindSprint.getIsKeyPressed();
        return new Input((w ? 1.0F : 0.0F) - (s ? 1.0F : 0.0F),
                (a ? 1.0F : 0.0F) - (d ? 1.0F : 0.0F),
                (up ? 1.0F : 0.0F) - (down ? 1.0F : 0.0F), fast);
    }

    private static boolean isBuilderScreen(GuiScreen screen) {
        return screen != null && BUILDER_SCREEN.equals(screen.getClass().getName());
    }

    private static boolean invokeBoolean(Object target, String methodName) {
        if (target == null) return false;
        try {
            Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    static final class Input {
        private static final Input NONE = new Input(0.0F, 0.0F, 0.0F, false);
        private final float forward;
        private final float strafe;
        private final float vertical;
        private final boolean fast;

        Input(float forward, float strafe, float vertical, boolean fast) {
            this.forward = forward;
            this.strafe = strafe;
            this.vertical = vertical;
            this.fast = fast;
        }

        float forward() { return forward; }
        float strafe() { return strafe; }
        float vertical() { return vertical; }
        boolean fast() { return fast; }
    }
}
