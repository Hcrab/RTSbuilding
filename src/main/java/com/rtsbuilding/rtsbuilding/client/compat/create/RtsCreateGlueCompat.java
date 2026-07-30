package com.rtsbuilding.rtsbuilding.client.compat.create;

import com.rtsbuilding.rtsbuilding.client.compat.RtsVanillaCursorHitBridge;
import com.rtsbuilding.rtsbuilding.client.screen.input.CameraInputHandler;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 让机械动力强力胶的原生两点框选流程在 BuilderScreen 中继续工作。
 *
 * <p>Create 的输入监听在任何 Screen 打开时都会提前返回，因此这里只转发“手持强力胶且
 * 点击世界区域”的使用/攻击动作。类通过反射可选加载，RTSBuilding 的发布包不会因此硬依赖
 * Create，也不会接管胶水的选区、消耗或发包逻辑。</p>
 */
public final class RtsCreateGlueCompat {
    private static final String GLUE_ITEM_CLASS =
            "com.simibubi.create.content.contraptions.glue.SuperGlueItem";
    private static final String CREATE_CLIENT_CLASS = "com.simibubi.create.CreateClient";

    private static boolean lookupAttempted;
    private static Object glueHandler;
    private static Method mouseInputMethod;

    private RtsCreateGlueCompat() {
    }

    public static boolean handleWorldClick(BuilderScreen screen, double mouseX, double mouseY, int button) {
        if (screen == null || !screen.isWorldArea(mouseX, mouseY)) {
            return false;
        }
        Minecraft minecraft = screen.getMinecraft();
        if (minecraft == null || minecraft.player == null
                || !GLUE_ITEM_CLASS.equals(minecraft.player.getMainHandItem().getItem().getClass().getName())) {
            return false;
        }

        boolean attack;
        if (CameraInputHandler.isBreakActionMouse(button)) {
            attack = true;
        } else if (CameraInputHandler.isPrimaryActionMouse(button)) {
            attack = false;
        } else {
            return false;
        }

        RtsVanillaCursorHitBridge.publish(screen);
        if (!resolveHandler()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(mouseInputMethod.invoke(glueHandler, attack));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean resolveHandler() {
        if (lookupAttempted) {
            return glueHandler != null && mouseInputMethod != null;
        }
        lookupAttempted = true;
        try {
            Class<?> createClient = Class.forName(CREATE_CLIENT_CLASS);
            Field handlerField = createClient.getField("GLUE_HANDLER");
            glueHandler = handlerField.get(null);
            mouseInputMethod = glueHandler.getClass().getMethod("onMouseInput", boolean.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            glueHandler = null;
            mouseInputMethod = null;
            return false;
        }
    }
}
