package com.rtsbuilding.rtsbuilding.client.screen.panel;

/**
 * RTS 窗口的单次指针交互会话。
 *
 * <p>本类只保存一次鼠标按下到释放之间的拖拽、缩放和吸附状态，并据此更新
 * {@link RtsWindowPanel} 已有的窗口边界。它不拥有窗口绘制、标题栏按钮、内容点击、
 * 边界持久化策略、网络状态或 RTS 镜头控制；这些职责仍由面板或其上层容器负责。
 * 将短生命周期输入状态独立出来，可避免基础面板再次同时承担渲染、持久化和指针状态机，
 * 同时保持既有的拖拽、八方向缩放、贴边与释放回调行为不变。</p>
 */
final class RtsWindowPointerSession {
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean resizing;
    private ResizeEdge resizeEdge = ResizeEdge.NONE;
    private int resizeStartMouseX;
    private int resizeStartMouseY;
    private int resizeStartWidth;
    private int resizeStartHeight;
    private int resizeStartWindowX;
    private int resizeStartWindowY;
    private boolean snapEngaged;

    RtsWindowPanel.ResizeCursor currentResizeCursor(RtsWindowPanel panel, int mouseX, int mouseY) {
        ResizeEdge edge = this.resizing ? this.resizeEdge : getResizeEdgeAt(panel, mouseX, mouseY);
        switch (edge) {
            case LEFT:
            case RIGHT:
                return RtsWindowPanel.ResizeCursor.RESIZE_EW;
            case TOP:
            case BOTTOM:
                return RtsWindowPanel.ResizeCursor.RESIZE_NS;
            case TOP_LEFT:
            case BOTTOM_RIGHT:
                return RtsWindowPanel.ResizeCursor.RESIZE_NWSE;
            case TOP_RIGHT:
            case BOTTOM_LEFT:
                return RtsWindowPanel.ResizeCursor.RESIZE_NESW;
            case NONE:
            default:
                return RtsWindowPanel.ResizeCursor.DEFAULT;
        }
    }

    /**
     * 尝试从窗口边框开始缩放。仅在可缩放窗口和命中边框时消费本次按下，
     * 其余点击仍交由标题栏或内容区域处理。
     */
    boolean beginResize(RtsWindowPanel panel, double mouseX, double mouseY) {
        if (!panel.resizable) {
            return false;
        }
        ResizeEdge edge = getResizeEdgeAt(panel, (int) mouseX, (int) mouseY);
        if (edge == ResizeEdge.NONE) {
            return false;
        }
        this.resizing = true;
        this.resizeEdge = edge;
        this.resizeStartMouseX = (int) mouseX;
        this.resizeStartMouseY = (int) mouseY;
        this.resizeStartWidth = panel.windowWidth;
        this.resizeStartHeight = panel.windowHeight;
        this.resizeStartWindowX = panel.windowX;
        this.resizeStartWindowY = panel.windowY;
        return true;
    }

    /** 仅在标题栏内开始移动，防止内容区点击意外进入拖拽状态。 */
    boolean beginDrag(RtsWindowPanel panel, double mouseX, double mouseY) {
        if (!panel.draggable || !panel.isInsideTitleBar(mouseX, mouseY)) {
            return false;
        }
        this.dragging = true;
        this.dragOffsetX = mouseX - panel.windowX;
        this.dragOffsetY = mouseY - panel.windowY;
        this.snapEngaged = false;
        return true;
    }

    /**
     * 推进当前拖拽或缩放会话。边界真正变化时才标记用户布局为脏，
     * 以保持原有持久化频率与面板回调语义。
     */
    boolean drag(RtsWindowPanel panel, double mouseX, double mouseY, int button) {
        if (!panel.open || button != 0) {
            return false;
        }
        if (this.resizing) {
            int beforeX = panel.windowX;
            int beforeY = panel.windowY;
            int beforeWidth = panel.windowWidth;
            int beforeHeight = panel.windowHeight;
            resizeToMouse(panel, (int) mouseX, (int) mouseY);
            if (beforeX != panel.windowX || beforeY != panel.windowY
                    || beforeWidth != panel.windowWidth || beforeHeight != panel.windowHeight) {
                panel.markUserBoundsDirty();
            }
            return true;
        }
        if (!this.dragging) {
            return false;
        }
        int beforeX = panel.windowX;
        int beforeY = panel.windowY;
        panel.windowX = (int) (mouseX - this.dragOffsetX);
        panel.windowY = (int) (mouseY - this.dragOffsetY);
        panel.clampWindowToScreen();
        this.snapEngaged = panel.snapToNearbyPanel();
        if (beforeX != panel.windowX || beforeY != panel.windowY) {
            panel.markUserBoundsDirty();
        }
        return true;
    }

    /**
     * 结束鼠标会话。无论指针是否仍在窗口上方都会清理临时状态；若本次改变过边界，
     * 则保留原有的释放回调，供面板完成最终持久化或派生处理。
     */
    boolean release(RtsWindowPanel panel, double mouseX, double mouseY, int button) {
        if (!panel.open) {
            cancel();
            return false;
        }
        if (button == 0) {
            boolean boundsChanged = this.dragging || this.resizing;
            cancel();
            if (boundsChanged) {
                panel.onBoundsChanged();
            }
        }
        return panel.isInsideWindow(mouseX, mouseY);
    }

    /** 面板关闭、切换或异常失焦时丢弃未完成的鼠标会话，不改写已保存的边界。 */
    void cancel() {
        this.dragging = false;
        this.resizing = false;
        this.resizeEdge = ResizeEdge.NONE;
        this.snapEngaged = false;
    }

    private ResizeEdge getResizeEdgeAt(RtsWindowPanel panel, int mouseX, int mouseY) {
        int border = panel.getResizeBorderWidth();
        boolean left = mouseX >= panel.windowX - border && mouseX < panel.windowX + border;
        boolean right = mouseX >= panel.windowX + panel.windowWidth - border
                && mouseX < panel.windowX + panel.windowWidth + border;
        boolean top = mouseY >= panel.windowY - border && mouseY < panel.windowY + border;
        boolean bottom = mouseY >= panel.windowY + panel.windowHeight - border
                && mouseY < panel.windowY + panel.windowHeight + border;
        if (top && left) return ResizeEdge.TOP_LEFT;
        if (top && right) return ResizeEdge.TOP_RIGHT;
        if (bottom && left) return ResizeEdge.BOTTOM_LEFT;
        if (bottom && right) return ResizeEdge.BOTTOM_RIGHT;
        if (left) return ResizeEdge.LEFT;
        if (right) return ResizeEdge.RIGHT;
        if (top) return ResizeEdge.TOP;
        if (bottom) return ResizeEdge.BOTTOM;
        return ResizeEdge.NONE;
    }

    private void resizeToMouse(RtsWindowPanel panel, int mouseX, int mouseY) {
        int dx = mouseX - this.resizeStartMouseX;
        int dy = mouseY - this.resizeStartMouseY;
        switch (this.resizeEdge) {
            case RIGHT:
                panel.windowWidth = this.resizeStartWidth + dx;
                break;
            case BOTTOM:
                panel.windowHeight = this.resizeStartHeight + dy;
                break;
            case LEFT:
                adjustLeftEdge(panel, dx);
                break;
            case TOP:
                adjustTopEdge(panel, dy);
                break;
            case TOP_LEFT:
                adjustLeftEdge(panel, dx);
                adjustTopEdge(panel, dy);
                break;
            case TOP_RIGHT:
                panel.windowWidth = this.resizeStartWidth + dx;
                adjustTopEdge(panel, dy);
                break;
            case BOTTOM_LEFT:
                adjustLeftEdge(panel, dx);
                panel.windowHeight = this.resizeStartHeight + dy;
                break;
            case BOTTOM_RIGHT:
                panel.windowWidth = this.resizeStartWidth + dx;
                panel.windowHeight = this.resizeStartHeight + dy;
                break;
            case NONE:
            default:
                break;
        }
        panel.clampWindowSize();
        panel.clampWindowToScreen();
    }

    private void adjustLeftEdge(RtsWindowPanel panel, int dx) {
        int maxRight = this.resizeStartWindowX + this.resizeStartWidth;
        panel.windowWidth = this.resizeStartWidth - dx;
        panel.clampWindowSize();
        panel.windowX = maxRight - panel.windowWidth;
    }

    private void adjustTopEdge(RtsWindowPanel panel, int dy) {
        int maxBottom = this.resizeStartWindowY + this.resizeStartHeight;
        panel.windowHeight = this.resizeStartHeight - dy;
        panel.clampWindowSize();
        panel.windowY = maxBottom - panel.windowHeight;
    }

    /**
     * 仅供会话内部使用的边框方向。它不是面板或子类的公开布局协议，
     * 因此不暴露到 {@link RtsWindowPanel} 的继承接口。
     */
    private enum ResizeEdge {
        NONE,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }
}
