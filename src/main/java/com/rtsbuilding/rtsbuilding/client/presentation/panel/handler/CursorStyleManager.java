package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsPanel;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;


public final class CursorStyleManager {

    @FunctionalInterface
    public interface CursorResolver {
        
        RtsPanel.ResizeCursor resolve(int mouseX, int mouseY);
    }

    private RtsPanel.ResizeCursor currentStyle = RtsPanel.ResizeCursor.DEFAULT;
    private final CursorResolver resolver;

    
    private long resizeEwCursor;
    private long resizeNsCursor;
    private long resizeNwseCursor;
    private long resizeNeswCursor;

    public CursorStyleManager(CursorResolver resolver) {
        this.resolver = resolver;
    }

    
    public void update(int mouseX, int mouseY) {
        RtsPanel.ResizeCursor cursor = resolver.resolve(mouseX, mouseY);
        if (cursor == this.currentStyle) return;
        this.currentStyle = cursor;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        GLFW.glfwSetCursor(mc.getWindow().getWindow(), cursorHandle(cursor));
    }

    
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
