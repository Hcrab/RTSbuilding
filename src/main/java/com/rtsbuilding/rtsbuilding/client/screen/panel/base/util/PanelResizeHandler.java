package com.rtsbuilding.rtsbuilding.client.screen.panel.base.util;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel;

/**
 * 面板缩放逻辑处理器。
 *
 * <p>负责管理面板缩放状态、边缘检测后的尺寸计算，
 * 以及对边锚定逻辑（左边缘缩放时右边缘不动，上边缘缩放时下边缘不动等），
 * 并确保面板不会超出屏幕边界。
 *
 * <p>与 {@link RtsPanel} 双向协作：处理器读写面板的窗口矩形字段，
 * 面板的 {@code clampWindowSize()} 和 {@code clampWindowToScreen()} 方法
 * 由处理器在每次缩放计算后调用。
 */
public final class PanelResizeHandler {

    private boolean resizing;
    private ResizeEdge resizeEdge = ResizeEdge.NONE;
    private int resizeStartMouseX;
    private int resizeStartMouseY;
    private int resizeStartWidth;
    private int resizeStartHeight;
    private int resizeStartWindowX;
    private int resizeStartWindowY;

    private final RtsPanel panel;

    public PanelResizeHandler(RtsPanel panel) {
        this.panel = panel;
    }

    public boolean isResizing() {
        return this.resizing;
    }

    public ResizeEdge getResizeEdge() {
        return this.resizeEdge;
    }

    public void beginResize(ResizeEdge edge, double mouseX, double mouseY) {
        this.resizing = true;
        this.resizeEdge = edge;
        this.resizeStartMouseX = (int) mouseX;
        this.resizeStartMouseY = (int) mouseY;
        this.resizeStartWidth = panel.getWindowWidth();
        this.resizeStartHeight = panel.getWindowHeight();
        this.resizeStartWindowX = panel.getWindowX();
        this.resizeStartWindowY = panel.getWindowY();
    }

    public void resizeToMouse(int mouseX, int mouseY) {
        int dx = mouseX - this.resizeStartMouseX;
        int dy = mouseY - this.resizeStartMouseY;
        switch (this.resizeEdge) {
            case RIGHT -> panel.setWindowWidth(this.resizeStartWidth + dx);
            case BOTTOM -> panel.setWindowHeight(this.resizeStartHeight + dy);
            case LEFT -> adjustLeftEdge(dx);
            case TOP -> adjustTopEdge(dy);
            case TOP_LEFT -> { adjustLeftEdge(dx); adjustTopEdge(dy); }
            case TOP_RIGHT -> { panel.setWindowWidth(this.resizeStartWidth + dx); adjustTopEdge(dy); }
            case BOTTOM_LEFT -> { adjustLeftEdge(dx); panel.setWindowHeight(this.resizeStartHeight + dy); }
            case BOTTOM_RIGHT -> { panel.setWindowWidth(this.resizeStartWidth + dx); panel.setWindowHeight(this.resizeStartHeight + dy); }
            case NONE -> {}
        }
        // 仅限制最小尺寸，移除最大尺寸限制
        panel.setWindowWidth(Math.max(panel.getMinWindowWidth(), panel.getWindowWidth()));
        panel.setWindowHeight(Math.max(panel.getMinWindowHeight(), panel.getWindowHeight()));
        // 缩放过程中不执行位置限制，避免对边被意外推移
        // 左/上边缘缩放时：clampWindowToScreen 将 windowX/windowY 回推后，
        // 宽度/高度已偏离锚定的对边（右/下边缘），需要重新锚定
        if (panel.getScreen() != null) {
            if (this.resizeEdge == ResizeEdge.LEFT
                    || this.resizeEdge == ResizeEdge.TOP_LEFT
                    || this.resizeEdge == ResizeEdge.BOTTOM_LEFT) {
                int anchoredRight = this.resizeStartWindowX + this.resizeStartWidth;
                panel.setWindowX(anchoredRight - panel.getWindowWidth());
            }
            if (this.resizeEdge == ResizeEdge.TOP
                    || this.resizeEdge == ResizeEdge.TOP_LEFT
                    || this.resizeEdge == ResizeEdge.TOP_RIGHT) {
                int anchoredBottom = this.resizeStartWindowY + this.resizeStartHeight;
                panel.setWindowY(anchoredBottom - panel.getWindowHeight());
            }
        }
    }

    public void endResize() {
        this.resizing = false;
        this.resizeEdge = ResizeEdge.NONE;
    }

    private void adjustLeftEdge(int dx) {
        int newWidth = this.resizeStartWidth - dx;
        panel.setWindowWidth(Math.max(newWidth, panel.getMinWindowWidth()));
        panel.setWindowX(this.resizeStartWindowX + this.resizeStartWidth - panel.getWindowWidth());
    }

    private void adjustTopEdge(int dy) {
        int newHeight = this.resizeStartHeight - dy;
        panel.setWindowHeight(Math.max(newHeight, panel.getMinWindowHeight()));
        panel.setWindowY(this.resizeStartWindowY + this.resizeStartHeight - panel.getWindowHeight());
    }
}
