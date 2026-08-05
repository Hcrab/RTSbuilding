package com.rtsbuilding.rtsbuilding.client.input;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * 把 LWJGL 当前事件转换成 RTS 的版本无关输入对象。
 *
 * <p>本类只负责 1.12 坐标/按键适配与消费日志，不判断 overlay 业务，也不直接执行网络副作用。
 * Forge 事件入口和 GuiScreen Mixin 入口必须共用它，避免两套兼容路径逐渐产生不同语义。
 */
final class RtsRawGuiInputAdapter {
    private RtsRawGuiInputAdapter() {
    }

    static boolean routeKeyboard(GuiScreen screen, String source) {
        if (!Keyboard.getEventKeyState()) return false;
        int key = Keyboard.getEventKey() == Keyboard.KEY_NONE
                ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
        if (!RtsClientInputRouter.onScreenKeyPressed(screen, key, Keyboard.getEventCharacter())) {
            return false;
        }
        RtsbuildingMod.LOGGER.info(
                "[RTS-OVERLAY] side=C event=KEY_CONSUMED source={} screen={} key={} char={}",
                source, screenName(screen), key, (int) Keyboard.getEventCharacter());
        return true;
    }

    static boolean routeMouse(GuiScreen screen, String source) {
        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution scaled = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        double mouseX = Mouse.getEventX() * (double) scaled.getScaledWidth() / minecraft.displayWidth;
        double mouseY = scaled.getScaledHeight()
                - Mouse.getEventY() * (double) scaled.getScaledHeight() / minecraft.displayHeight - 1.0D;
        int button = Mouse.getEventButton();
        int wheel = Mouse.getEventDWheel();
        boolean consumed = false;

        if (wheel != 0) {
            RtsPointerEvent pointer = new RtsPointerEvent(screen, mouseX, mouseY, button, wheel);
            RtsClientPointerRouter.onScreenMouseScrolled(pointer);
            consumed |= pointer.isCanceled();
        }
        if (button >= 0) {
            RtsPointerEvent pointer = new RtsPointerEvent(screen, mouseX, mouseY, button, 0.0D);
            if (Mouse.getEventButtonState()) {
                RtsClientPointerRouter.onScreenMousePressed(pointer);
            } else {
                RtsClientPointerRouter.onScreenMouseReleased(pointer);
            }
            consumed |= pointer.isCanceled();
        } else if (Mouse.isButtonDown(0) || Mouse.isButtonDown(1)) {
            RtsPointerEvent pointer = new RtsPointerEvent(screen, mouseX, mouseY, -1, 0.0D);
            RtsClientPointerRouter.onScreenMouseDragged(pointer);
            consumed |= pointer.isCanceled();
        }
        if (consumed) {
            RtsbuildingMod.LOGGER.info(
                    "[RTS-OVERLAY] side=C event=POINTER_CONSUMED source={} screen={} button={} state={} wheel={} mouse={},{}",
                    source, screenName(screen), button,
                    button < 0 || Mouse.getEventButtonState(), wheel,
                    Math.round(mouseX), Math.round(mouseY));
        }
        return consumed;
    }

    private static String screenName(GuiScreen screen) {
        return screen == null ? "null" : screen.getClass().getName();
    }
}
