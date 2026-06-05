package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.client.gui.GuiGraphics;

/**
 * RTS 面板统一接口。
 * <p>
 * 所有 RTS UI 面板实现该接口，由 {@link BuilderScreen} 统一调度
 * 的 init / tick / render / 事件分发生命周期。
 */
public interface RtsPanel {

    /** 初始化面板，每次屏幕 init() 时调用 */
    default void init(BuilderScreen screen, ClientRtsController controller) {}

    /** 每 tick 更新面板状态 */
    default void tick() {}

    /** 渲染面板内容 */
    default void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    /** 渲染 tooltip（在 hover 检测之后） */
    default void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {}

    // --- 输入事件 ---

    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }

    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }

    default boolean mouseMoved(double mouseX, double mouseY) { return false; }

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) { return false; }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    default boolean keyReleased(int keyCode, int scanCode, int modifiers) { return false; }

    default boolean charTyped(char codePoint, int modifiers) { return false; }

    /** 面板关闭/屏幕关闭时调用 */
    default void close() {}
}
