package com.rtsbuilding.rtsbuilding.client.input;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * 把尚未重写的 RTS 面板调用适配到 26.1 强类型输入事件。
 *
 * <p>本类只做参数封装，不改变焦点、双击判定和事件消费规则。完成面板控件树
 * 重整后可逐步删除这些桥接方法。
 */
public final class RtsWidgetCompat {
    private RtsWidgetCompat() {
    }

    public static void render(AbstractWidget widget, GuiGraphicsExtractor graphics,
            int mouseX, int mouseY, float partialTick) {
        if (widget != null) {
            widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    public static boolean mouseClicked(GuiEventListener listener,
            double mouseX, double mouseY, int button) {
        return listener != null && listener.mouseClicked(
                new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0)), false);
    }

    public static boolean keyPressed(GuiEventListener listener,
            int keyCode, int scanCode, int modifiers) {
        return listener != null && listener.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
    }

    public static boolean charTyped(GuiEventListener listener, char codePoint) {
        return listener != null && listener.charTyped(new CharacterEvent(codePoint));
    }
}
