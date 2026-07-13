package com.rtsbuilding.rtsbuilding.client.screen.layout;

import com.rtsbuilding.rtsbuilding.client.screen.event.dispatcher.EventDispatcher;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.event.model.EventResult.CONSUMED;
import static com.rtsbuilding.rtsbuilding.client.screen.event.model.EventResult.PASS;

/**
 * 面板注册表——管理 BuilderScreen 中按渲染层级编排的所有面板。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>统一管理内容面板（TopBar、LeftSidebar、RightSidebar、DownSidebar）的注册</li>
 *   <li>按层级批量执行生命周期方法（init、render）</li>
 *   <li>通过 {@link #getPanel(Class)} 提供类型安全的面板查询</li>
 *   <li>自动注册内容面板的鼠标点击事件到 {@link EventDispatcher}</li>
 *   <li>消除 BuilderScreen 中逐个面板的硬编码渲染/事件调用</li>
 * </ul>
 *
 * <p>特殊面板（{@link com.rtsbuilding.rtsbuilding.client.screen.panel.background.ScreenBackgroundPanel}、
 * {@link com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsFloatingWindowLayer}）
 * 因其多阶段渲染特性，仍由 BuilderScreen 直接管理。</p>
 */
public final class PanelRegistry {

    /** 面板条目——记录面板实例及其渲染层级 */
    private record PanelEntry(RtsPanelApi panel, RenderLayer layer) {}

    private final List<PanelEntry> entries = new ArrayList<>();

    // ======================== 注册 ========================

    /**
     * 注册一个面板到指定渲染层级。
     *
     * @param panel 面板实例
     * @param layer 渲染层级
     */
    public void register(RtsPanelApi panel, RenderLayer layer) {
        if (panel == null) {
            throw new IllegalArgumentException("不能注册 null 面板");
        }
        entries.add(new PanelEntry(panel, layer));
    }

    /**
     * 返回注册表是否为空。
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 按类型查询已注册的面板实例。
     * <p>用于替代 BuilderScreen 中散落的直接字段引用，
     * 使新增面板时无需添加新字段。</p>
     *
     * @param <T>  面板类型
     * @param type 面板 Class 对象
     * @return 匹配的面板实例，未找到时返回 null
     */
    @SuppressWarnings("unchecked")
    public <T extends RtsPanelApi> T getPanel(Class<T> type) {
        for (PanelEntry entry : entries) {
            if (type.isInstance(entry.panel())) {
                return (T) entry.panel();
            }
        }
        return null;
    }

    // ======================== 初始化 ========================

    /**
     * 为所有注册面板调用 init()。
     */
    public void initAll(BuilderScreen screen) {
        for (PanelEntry entry : entries) {
            entry.panel().init(screen);
        }
    }

    // ======================== 渲染 ========================

    /**
     * 渲染指定层级的所有注册面板。
     */
    public void renderLayer(GuiGraphics g, int mouseX, int mouseY, float partialTick, RenderLayer layer) {
        for (PanelEntry entry : entries) {
            if (entry.layer() == layer) {
                entry.panel().render(g, mouseX, mouseY, partialTick);
            }
        }
    }

    /**
     * 渲染指定层级的所有注册面板的覆盖层（Overlay）。
     */
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY, RenderLayer layer) {
        for (PanelEntry entry : entries) {
            if (entry.layer() == layer) {
                entry.panel().renderOverlays(g, mouseX, mouseY);
            }
        }
    }

    /**
     * 渲染内容面板层（CONTENT_PANELS），并在需要时包裹悬浮抑制逻辑。
     *
     * @param mouseOverFloating 鼠标是否在浮动窗口上（用于悬浮抑制）
     */
    public void renderContentPanels(GuiGraphics g, int mouseX, int mouseY, float partialTick,
                                     boolean mouseOverFloating) {
        if (mouseOverFloating) {
            HoverStateManager.floatingWindowSuppression().setSuppressed(true);
        }
        try {
            for (PanelEntry entry : entries) {
                if (entry.layer() == RenderLayer.CONTENT_PANELS) {
                    entry.panel().render(g, mouseX, mouseY, partialTick);
                }
            }
        } finally {
            if (mouseOverFloating) {
                HoverStateManager.floatingWindowSuppression().setSuppressed(false);
            }
        }
    }

    // ======================== 输入事件 ========================

    /**
     * 注册内容面板的鼠标点击事件到 EventDispatcher，在 {@link EventDispatcher#P_UI_PANEL} 优先级上
     * 依次询问各面板是否消费此事件。
     * <p>替代 {@code BuilderScreen.registerEventHandlers()} 中 4 行硬编码面板点击检测。</p>
     *
     * @param dispatcher 事件分发器实例
     */
    public void registerContentPanelMouseClick(EventDispatcher dispatcher) {
        dispatcher.onMouseClick(event -> {
            for (PanelEntry entry : entries) {
                if (entry.layer() == RenderLayer.CONTENT_PANELS
                        && entry.panel().mouseClicked(event.x(), event.y(), event.button())) {
                    return CONSUMED;
                }
            }
            return PASS;
        }, EventDispatcher.P_UI_PANEL);
    }

    /**
     * 注册内容面板的鼠标释放事件到 EventDispatcher。
     * <p>释放事件不阻止后续分发（多面板同时释放）。</p>
     *
     * @param dispatcher 事件分发器实例
     */
    public void registerContentPanelMouseRelease(EventDispatcher dispatcher) {
        dispatcher.onMouseRelease(event -> {
            for (PanelEntry entry : entries) {
                if (entry.layer() == RenderLayer.CONTENT_PANELS) {
                    entry.panel().mouseReleased(event.x(), event.y(), event.button());
                }
            }
            return PASS; // release 不透传 CONSUMED
        }, EventDispatcher.P_UI_PANEL);
    }

    /**
     * 注册内容面板的鼠标拖拽事件到 EventDispatcher。
     *
     * @param dispatcher 事件分发器实例
     */
    public void registerContentPanelMouseDrag(EventDispatcher dispatcher) {
        dispatcher.onMouseDrag(event -> {
            // 跳过因 glfwSetCursorPos 光标环绕导致的大幅跳变 delta
            double clampedDx = Math.abs(event.dx()) > 200 ? 0 : event.dx();
            double clampedDy = Math.abs(event.dy()) > 200 ? 0 : event.dy();
            for (PanelEntry entry : entries) {
                if (entry.layer() == RenderLayer.CONTENT_PANELS) {
                    if (entry.panel().mouseDragged(event.x(), event.y(), event.button(),
                            clampedDx, clampedDy)) {
                        return CONSUMED;
                    }
                }
            }
            return PASS;
        }, EventDispatcher.P_UI_PANEL);
    }

    /**
     * 注册内容面板的鼠标滚轮事件到 EventDispatcher。
     *
     * @param dispatcher 事件分发器实例
     */
    public void registerContentPanelMouseScroll(EventDispatcher dispatcher) {
        dispatcher.onMouseScroll(event -> {
            for (PanelEntry entry : entries) {
                if (entry.layer() == RenderLayer.CONTENT_PANELS) {
                    if (entry.panel().mouseScrolled(event.x(), event.y(), event.scrollX(), event.scrollY())) {
                        return CONSUMED;
                    }
                }
            }
            return PASS;
        }, EventDispatcher.P_UI_PANEL);
    }

    /**
     * 注册内容面板的按键按下事件到 EventDispatcher。
     *
     * @param dispatcher 事件分发器实例
     */
    public void registerContentPanelKeyPress(EventDispatcher dispatcher) {
        dispatcher.onKeyPress(event -> {
            for (PanelEntry entry : entries) {
                if (entry.layer() == RenderLayer.CONTENT_PANELS) {
                    if (entry.panel().keyPressed(event.keyCode(), event.scanCode(), event.modifiers())) {
                        return CONSUMED;
                    }
                }
            }
            return PASS;
        }, EventDispatcher.P_UI_PANEL);
    }

    /**
     * 注册内容面板的字符输入事件到 EventDispatcher。
     *
     * @param dispatcher 事件分发器实例
     */
    public void registerContentPanelCharTyped(EventDispatcher dispatcher) {
        dispatcher.onChar(event -> {
            for (PanelEntry entry : entries) {
                if (entry.layer() == RenderLayer.CONTENT_PANELS) {
                    if (entry.panel().charTyped(event.codePoint(), event.modifiers())) {
                        return CONSUMED;
                    }
                }
            }
            return PASS;
        }, EventDispatcher.P_UI_PANEL);
    }
}
