package com.rtsbuilding.rtsbuilding.client.screen.panel.handler;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.RtsPanel;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 光标样式管理器——统一管理 BuilderScreen 的缩放光标创建、缓存和更新。
 *
 * <p>封装 GLFW 光标句柄的懒加载缓存和每帧样式切换逻辑，
 * 替代 BuilderScreen 中散落的 4 个光标句柄字段 + 3 个光标方法。</p>
 *
 * <p>通过 {@link CursorResolver} 函数式接口注入决策逻辑，
 * 本类仅负责 GLFW 资源的生命周期管理，不涉及具体的光标判定规则。</p>
 */
public final class CursorStyleManager {

    @FunctionalInterface
    public interface CursorResolver {
        /** 根据鼠标位置返回应显示的光标样式。 */
        RtsPanel.ResizeCursor resolve(int mouseX, int mouseY);
    }

    private RtsPanel.ResizeCursor currentStyle = RtsPanel.ResizeCursor.DEFAULT;
    private final CursorResolver resolver;

    // GLFW 标准光标句柄（懒加载缓存，创建后永不复用）
    private long resizeEwCursor;
    private long resizeNsCursor;
    private long resizeNwseCursor;
    private long resizeNeswCursor;

    public CursorStyleManager(CursorResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * 每帧更新光标样式（在 render 末尾调用）。
     * <p>仅在光标样式发生变化时更新 GLFW 光标，避免不必要的系统调用。</p>
     */
    public void update(int mouseX, int mouseY) {
        RtsPanel.ResizeCursor cursor = resolver.resolve(mouseX, mouseY);
        if (cursor == this.currentStyle) return;
        this.currentStyle = cursor;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        GLFW.glfwSetCursor(mc.getWindow().getWindow(), cursorHandle(cursor));
    }

    /** 恢复默认光标（在屏幕关闭时调用）。 */
    public void restoreDefault() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        GLFW.glfwSetCursor(mc.getWindow().getWindow(), 0L);
        this.currentStyle = RtsPanel.ResizeCursor.DEFAULT;
    }

    private long cursorHandle(RtsPanel.ResizeCursor cursor) {
        return switch (cursor) {
            case RESIZE_EW -> {
                if (this.resizeEwCursor == 0L) {
                    this.resizeEwCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_EW_CURSOR);
                }
                yield this.resizeEwCursor;
            }
            case RESIZE_NS -> {
                if (this.resizeNsCursor == 0L) {
                    this.resizeNsCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NS_CURSOR);
                }
                yield this.resizeNsCursor;
            }
            case RESIZE_NWSE -> {
                if (this.resizeNwseCursor == 0L) {
                    this.resizeNwseCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR);
                }
                yield this.resizeNwseCursor;
            }
            case RESIZE_NESW -> {
                if (this.resizeNeswCursor == 0L) {
                    this.resizeNeswCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NESW_CURSOR);
                }
                yield this.resizeNeswCursor;
            }
            case DEFAULT -> 0L;
        };
    }
}
