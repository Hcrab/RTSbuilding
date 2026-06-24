package com.rtsbuilding.rtsbuilding.client.input.layer;

import com.rtsbuilding.rtsbuilding.client.input.InputLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * 覆盖层输入层——处理容器界面上的 RTS 存储覆盖层。
 * 仅在 AbstractContainerScreen 打开且 RTS 存储可用时活跃。
 */
public final class OverlayLayer implements InputLayer {

    private boolean overlaySearchFocused;
    private String overlaySearchDraft = "";

    @Override
    public boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return false;
        return mc.screen instanceof AbstractContainerScreen;
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen)) return false;

        // 简单的覆盖层交互：阻止点击穿透到下层界面
        // TODO: 完整覆盖层渲染与交互（迁移自旧 RtsClientInputGate）
        return false; // 暂时不消费事件
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && overlaySearchFocused) {
            overlaySearchFocused = false;
            overlaySearchDraft = "";
            return true;
        }
        return false;
    }

    public String getOverlaySearchDraft() {
        return overlaySearchDraft;
    }
}
