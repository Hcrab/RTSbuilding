package com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.handler;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.util.render.PanelDragPerformanceOptimizer;

import java.util.List;

/**
 * 面板拖拽逻辑处理器。
 *
 * <p>负责管理面板拖拽状态、位置更新以及对附近面板的吸附逻辑，
 * 确保拖拽时面板不会超出屏幕边界。
 *
 * <p>与 {@link RtsPanel} 双向协作：处理器读写面板的位置字段，
 * 面板的 {@code clampWindowToScreen()} 方法由处理器在每次拖拽移动后调用。
 */
public final class PanelDragHandler {

    private static final int SNAP_THRESHOLD = 6;

    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean snapEngaged;

    private final RtsPanel panel;

    public PanelDragHandler(RtsPanel panel) {
        this.panel = panel;
    }

    public boolean isDragging() {
        return this.dragging;
    }

    public void beginDrag(double mouseX, double mouseY) {
        this.dragging = true;
        this.dragOffsetX = mouseX - panel.getWindowX();
        this.dragOffsetY = mouseY - panel.getWindowY();
        this.snapEngaged = false;
        PanelDragPerformanceOptimizer.setCurrentlyDraggingPanel(panel);
    }

    /**
     * 执行拖拽移动：更新位置、限制到屏幕。
     * <p>吸附仅在拖拽结束时执行，避免拖拽过程中吸附与鼠标位置反复竞争造成抖动。
     *
     * @return true 如果面板位置发生变化
     */
    public boolean dragTo(double mouseX, double mouseY) {
        int beforeX = panel.getWindowX();
        int beforeY = panel.getWindowY();
        panel.setWindowX((int) Math.round(mouseX - this.dragOffsetX));
        panel.setWindowY((int) Math.round(mouseY - this.dragOffsetY));
        // 移除这里的条件限制，确保在拖拽过程中也能适当限制屏幕边界
        // 之前的设计是想在拖拽时不限制，但这可能导致面板移出屏幕
        panel.clampWindowToScreen();
        return beforeX != panel.getWindowX() || beforeY != panel.getWindowY();
    }

    public void endDrag() {
        if (this.dragging) {
            snapToNearbyPanel();
        }
        this.dragging = false;
        this.snapEngaged = false;
        PanelDragPerformanceOptimizer.clearDraggingPanel();
    }

    // ======================== 吸附逻辑 ========================

    private void snapToNearbyPanel() {
        if (panel.getScreen() == null) return;
        RtsFloatingWindowLayer layer = panel.getScreen().getFloatingWindowLayer();
        List<RtsPanel> panels = layer.frontToBackWindows();

        int preSnapX = panel.getWindowX();
        int preSnapY = panel.getWindowY();

        for (RtsPanel other : panels) {
            if (other == panel || !other.isOpen()) continue;

            int oL = other.getWindowX();
            int oR = other.getWindowX() + other.getWindowWidth();
            int oT = other.getWindowY();
            int oB = other.getWindowY() + other.getWindowHeight();

            boolean verticalOverlap = overlapY(panel, other) > 0;
            boolean horizontalOverlap = overlapX(panel, other) > 0;

            if (verticalOverlap) {
                int mL = panel.getWindowX();
                int mR = panel.getWindowX() + panel.getWindowWidth();
                if (Math.abs(mL - oR) < SNAP_THRESHOLD) {
                    panel.setWindowX(oR + 1);
                } else if (Math.abs(mR - oL) < SNAP_THRESHOLD) {
                    panel.setWindowX(oL - panel.getWindowWidth() - 1);
                }
            }

            if (horizontalOverlap) {
                int mT = panel.getWindowY();
                int mB = panel.getWindowY() + panel.getWindowHeight();
                if (Math.abs(mT - oB) < SNAP_THRESHOLD) {
                    panel.setWindowY(oB + 1);
                } else if (Math.abs(mB - oT) < SNAP_THRESHOLD) {
                    panel.setWindowY(oT - panel.getWindowHeight() - 1);
                }
            }
        }
        this.snapEngaged = panel.getWindowX() != preSnapX || panel.getWindowY() != preSnapY;
    }

    private static int overlapY(RtsPanel a, RtsPanel b) {
        int aTop = a.getWindowY();
        int aBot = a.getWindowY() + a.getWindowHeight();
        int bTop = b.getWindowY();
        int bBot = b.getWindowY() + b.getWindowHeight();
        return Math.max(0, Math.min(aBot, bBot) - Math.max(aTop, bTop));
    }

    private static int overlapX(RtsPanel a, RtsPanel b) {
        int aL = a.getWindowX();
        int aR = a.getWindowX() + a.getWindowWidth();
        int bL = b.getWindowX();
        int bR = b.getWindowX() + b.getWindowWidth();
        return Math.max(0, Math.min(aR, bR) - Math.max(aL, bL));
    }
}
