package com.rtsbuilding.rtsbuilding.client.screen.panel.base.api;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * 统一的 RTS 面板接口。
 * <p>
 * 所有 RTS UI 面板都实现此接口，由 {@link BuilderScreen}
 * 通过 init / tick / render / 事件分发生命周期统一调度。
 */
public interface RtsPanelApi {

    /** 初始化面板，每次屏幕初始化时调用 */
    default void init(BuilderScreen screen) {}

    /** 每 tick 更新面板状态 */
    default void tick() {}

    /** 渲染面板内容 */
    default void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    /** 渲染工具提示（悬停检测后） */
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

    /** 面板/屏幕关闭时调用 */
    default void close() {}

    // --- 持久化 ---

    /**
     * 返回需要持久化的属性列表。
     * <p>默认返回空列表，子类可重写添加自定义持久化属性。</p>
     */
    default List<PersistableProperty> persistableProperties() { return List.of(); }
}
