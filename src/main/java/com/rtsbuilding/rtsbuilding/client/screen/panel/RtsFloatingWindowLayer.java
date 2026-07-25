package com.rtsbuilding.rtsbuilding.client.screen.panel;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Routes input and rendering for movable RTS windows in front-to-back order.
 *
 * <p>The layer owns window stacking, z-order rendering, and input dispatch. It
 * deliberately does not know what a window does internally, which gameplay
 * action it represents, or how persistent UI state is saved. That keeps the
 * current mainline screen behavior intact while giving future windows a single
 * place to join rendering and input handling.
 *
 * <p>Windows are rendered in ascending {@link RtsWindowPanel#getLastClickTime}
 * order — the most recently clicked window appears on top. Clicking any window
 * brings it to the front automatically.
 */
public record RtsFloatingWindowLayer(List<RtsWindowPanel> frontToBackWindows,
                                     RtsFloatingWindowInputRouter inputRouter) {

    public RtsFloatingWindowLayer(RtsWindowPanel... frontToBackWindows) {
        this(new ArrayList<>(List.of(frontToBackWindows)));
    }

    private RtsFloatingWindowLayer(List<RtsWindowPanel> windows) {
        this(windows, new RtsFloatingWindowInputRouter(windows));
        // 初始 z 排序修正：从后往前调用 markBroughtToFront，
        // 使得前部窗口（索引 0，前端）获得较大的 lastClickTime，
        // 在升序排序中后渲染（出现在顶层）。
        for (int i = frontToBackWindows.size() - 1; i >= 0; i--) {
            frontToBackWindows.get(i).markBroughtToFront();
        }
        ensureZOrder();
    }

    // ======================== Z-order Rendering ========================

    /**
     * Renders all registered windows sorted by z-order (last-click time).
     * Windows with lower click times (clicked longer ago) are rendered first,
     * so the most recently clicked window appears on top.
     */
    public void renderFloatingWindows(GuiGraphics g, int mouseX, int mouseY) {
        if (this.frontToBackWindows.isEmpty()) return;
        ensureZOrder();

        // 找出鼠标所在的最顶层窗口索引（列表按升序排列，最后一个为顶层）
        int topmostHoverIdx = -1;
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            if (window.isVisibleWindow() && window.isInsideWindow(mouseX, mouseY)) {
                topmostHoverIdx = i;
                break;
            }
        }

        for (int i = 0; i < this.frontToBackWindows.size(); i++) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            // 对鼠标在区域内但非最顶层的窗口抑制悬浮效果
            boolean shouldSuppress = topmostHoverIdx >= 0 && i != topmostHoverIdx
                    && window.isVisibleWindow()
                    && window.isInsideWindow(mouseX, mouseY);
            window.setSkipHoverDetection(shouldSuppress);
            window.render(g, mouseX, mouseY, 0.0F);
            window.setSkipHoverDetection(false);
            // 安全冲刷：RtsWindowPanel.render() 已在 scissor 内 flush 了内容，
            // 此处额外冲刷共享渲染缓冲区，确保 item 渲染这类立即提交的数据
            // 已经在 scissor 完成时被放入帧缓冲区。
            // GUI 层只提交自己积累的绘制；共享 BufferSource 的生命周期由 Minecraft 管理。
            // 主动 endBatch 会把其他模组或后续渲染阶段的批次一并结束。
            g.flush();
        }
    }

    /**
     * Renders the overlay (tooltips, etc.) only for the topmost window that
     * the mouse cursor is hovering over. Lower windows whose entire bounds
     * are covered by a higher window have their overlays suppressed, which
     * prevents e.g. a tooltip from the covered panel showing through.
     * <p>
     * The list is sorted ascending by click time (back first, front last)
     * from the preceding render pass, so we iterate in reverse to find the
     * topmost window at the cursor position first.
     */
    public void renderFloatingWindowOverlays(GuiGraphics g, int mouseX, int mouseY) {
        ensureZOrder();
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            if (window.isVisibleWindow() && window.isInsideWindow(mouseX, mouseY)) {
                window.renderOverlays(g, mouseX, mouseY);
                return;
            }
        }
    }

    public RtsWindowPanel.ResizeCursor resizeCursorAt(double mouseX, double mouseY) {
        ensureZOrder();
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel.ResizeCursor cursor = this.frontToBackWindows.get(i).currentResizeCursor(mouseX, mouseY);
            if (cursor != RtsWindowPanel.ResizeCursor.DEFAULT) {
                return cursor;
            }
        }
        return RtsWindowPanel.ResizeCursor.DEFAULT;
    }

    public boolean isMouseOverWindowOrResizableBorder(double mouseX, double mouseY) {
        ensureZOrder();
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            if (window.isVisibleWindow()
                    && (window.isInsideWindow(mouseX, mouseY) || window.isInsideResizableBorder(mouseX, mouseY))) {
                return true;
            }
        }
        return false;
    }

    // ======================== Input Routing ========================

    /**
     * Routes mouse clicks through windows in top-to-bottom (front-to-back) order.
     * The list is currently sorted ascending by click time (back first, front last)
     * because the render pass sorted it. We iterate in reverse so the topmost
     * window under the cursor is checked first.
     * When a window handles the click, it is automatically brought to front.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.inputRouter.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.inputRouter.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.inputRouter.mouseReleased(mouseX, mouseY, button);
    }

    public boolean consumeAnyBoundsDirty() {
        boolean dirty = false;
        for (RtsWindowPanel window : this.frontToBackWindows) {
            dirty = window.consumeBoundsDirty() || dirty;
        }
        return dirty;
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

    public void clearTransientInputState() {
        this.inputRouter.clearTransientState();
    }

    /**
     * 只有窗口真正置顶后才排序；静止渲染帧只做固定小列表的有序检查。
     * 这也让输入不再依赖“之前恰好先 render 过一次”的隐含前提。
     */
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
