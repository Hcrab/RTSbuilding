package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 统一管理所有可移动 RTS 窗口的层级、渲染顺序和输入分发。
 *
 * <p>本层只负责窗口编排，不拥有窗口内部的业务状态。列表始终按最后点击时间从后到前排列；
 * 1.12 的 GUI 使用立即绘制，因此通过独立矩阵和深度带隔离每个窗口，而不调用现代缓冲区 flush。
 */
public final class RtsFloatingWindowLayer {
    /*
     * 1.12.2 的 GUI 正交投影只有有限的可见深度。窗口仍按从后到前的顺序立即绘制，
     * 因而这里只需要很小的 Z 间距；旧值 400 会把刚置顶的窗口推到数千格之外并被裁剪。
     */
    private static final float WINDOW_BASE_Z = 32.0F;
    private static final float WINDOW_Z_STRIDE = 8.0F;
    private static final float WINDOW_MAX_Z = 384.0F;

    private final List<RtsWindowPanel> frontToBackWindows;
    private final RtsFloatingWindowInputRouter inputRouter;

    public RtsFloatingWindowLayer(RtsWindowPanel... windows) {
        this(new ArrayList<RtsWindowPanel>(Arrays.asList(windows)));
    }

    private RtsFloatingWindowLayer(List<RtsWindowPanel> windows) {
        this.frontToBackWindows = windows;
        this.inputRouter = new RtsFloatingWindowInputRouter(windows);
        for (int i = windows.size() - 1; i >= 0; i--) {
            windows.get(i).markBroughtToFront();
        }
        ensureZOrder();
    }

    /** 保留原 record 访问器形状，避免调用方额外承担版本差异。 */
    public List<RtsWindowPanel> frontToBackWindows() {
        return this.frontToBackWindows;
    }

    public RtsFloatingWindowInputRouter inputRouter() {
        return this.inputRouter;
    }

    public void renderFloatingWindows(LegacyGuiGraphics graphics, int mouseX, int mouseY) {
        if (this.frontToBackWindows.isEmpty()) return;
        ensureZOrder();

        int topmostHoverIndex = -1;
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            if (window.isVisibleWindow() && window.isInsideWindow(mouseX, mouseY)) {
                topmostHoverIndex = i;
                break;
            }
        }

        for (int i = 0; i < this.frontToBackWindows.size(); i++) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            boolean suppressHover = topmostHoverIndex >= 0 && i != topmostHoverIndex
                    && window.isVisibleWindow() && window.isInsideWindow(mouseX, mouseY);
            window.setSkipHoverDetection(suppressHover);
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(0.0F, 0.0F, windowLayerZ(i));
                window.render(graphics, mouseX, mouseY, 0.0F);
            } finally {
                GlStateManager.popMatrix();
                window.setSkipHoverDetection(false);
            }
        }
    }

    /** 只让鼠标所在的最前窗口产生提示，避免被遮挡窗口的文字穿透。 */
    public void renderFloatingWindowOverlays(LegacyGuiGraphics graphics, int mouseX, int mouseY) {
        ensureZOrder();
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            if (window.isVisibleWindow() && window.isInsideWindow(mouseX, mouseY)) {
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(0.0F, 0.0F,
                            windowLayerZ(i) + WINDOW_Z_STRIDE - 1.0F);
                    window.renderOverlays(graphics, mouseX, mouseY);
                } finally {
                    GlStateManager.popMatrix();
                }
                return;
            }
        }
    }

    private static float windowLayerZ(int index) {
        return Math.min(WINDOW_MAX_Z,
                WINDOW_BASE_Z + Math.max(0, index) * WINDOW_Z_STRIDE);
    }

    public RtsWindowPanel.ResizeCursor resizeCursorAt(double mouseX, double mouseY) {
        ensureZOrder();
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel.ResizeCursor cursor =
                    this.frontToBackWindows.get(i).currentResizeCursor(mouseX, mouseY);
            if (cursor != RtsWindowPanel.ResizeCursor.DEFAULT) return cursor;
        }
        return RtsWindowPanel.ResizeCursor.DEFAULT;
    }

    public boolean isMouseOverWindowOrResizableBorder(double mouseX, double mouseY) {
        ensureZOrder();
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            if (window.isVisibleWindow()
                    && (window.isInsideWindow(mouseX, mouseY)
                    || window.isInsideResizableBorder(mouseX, mouseY))) return true;
        }
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.inputRouter.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.inputRouter.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.inputRouter.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.inputRouter.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.inputRouter.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return this.inputRouter.charTyped(codePoint, modifiers);
    }

    public boolean consumeAnyBoundsDirty() {
        boolean dirty = false;
        for (RtsWindowPanel window : this.frontToBackWindows) {
            dirty = window.consumeBoundsDirty() || dirty;
        }
        return dirty;
    }

    public void clearTransientInputState() {
        this.inputRouter.clearTransientState();
    }

    /** 输入和渲染都主动校正层序，不依赖此前是否已经渲染过一帧。 */
    private void ensureZOrder() {
        for (int i = 1; i < this.frontToBackWindows.size(); i++) {
            if (this.frontToBackWindows.get(i - 1).getLastClickTime()
                    > this.frontToBackWindows.get(i).getLastClickTime()) {
                this.frontToBackWindows.sort(Comparator.comparingLong(RtsWindowPanel::getLastClickTime));
                return;
            }
        }
    }
}
