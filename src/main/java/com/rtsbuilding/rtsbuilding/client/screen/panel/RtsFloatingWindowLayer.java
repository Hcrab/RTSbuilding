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
public record RtsFloatingWindowLayer(List<RtsWindowPanel> frontToBackWindows) {

    public RtsFloatingWindowLayer(RtsWindowPanel... frontToBackWindows) {
        this(new ArrayList<>(List.of(frontToBackWindows)));
        // 初始 z 排序修正：从后往前调用 markBroughtToFront，
        // 使得前部窗口（索引 0，前端）获得较大的 lastClickTime，
        // 在升序排序中后渲染（出现在顶层）。
        for (int i = frontToBackWindows.length - 1; i >= 0; i--) {
            frontToBackWindows[i].markBroughtToFront();
        }
    }

    // ======================== Z-order Rendering ========================

    /**
     * Renders all registered windows sorted by z-order (last-click time).
     * Windows with lower click times (clicked longer ago) are rendered first,
     * so the most recently clicked window appears on top.
     */
    public void renderFloatingWindows(GuiGraphics g, int mouseX, int mouseY) {
        if (this.frontToBackWindows.isEmpty()) return;
        this.frontToBackWindows.sort(Comparator.comparingLong(RtsWindowPanel::getLastClickTime));

        // 找出鼠标所在的最顶层窗口索引（列表按升序排列，最后一个为顶层）
        int topmostHoverIdx = -1;
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            if (this.frontToBackWindows.get(i).isInsideWindow(mouseX, mouseY)) {
                topmostHoverIdx = i;
                break;
            }
        }

        for (int i = 0; i < this.frontToBackWindows.size(); i++) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            // 对鼠标在区域内但非最顶层的窗口抑制悬浮效果
            boolean shouldSuppress = topmostHoverIdx >= 0 && i != topmostHoverIdx
                    && window.isInsideWindow(mouseX, mouseY);
            window.setSkipHoverDetection(shouldSuppress);
            window.render(g, mouseX, mouseY, 0.0F);
            window.setSkipHoverDetection(false);
            // 强制刷新 GuiGraphics 内部缓冲区，将所有 RenderType（含 gui 和 guiText）
            // 的待处理顶点刷新到帧缓冲，确保当前窗口的内容已实际渲染到屏幕。
            // 必须为每个窗口（包含最后一个）都 flush()，否则最后一个窗口的
            // guiText 会与后续绘制混入同一 batch，因 Minecraft RenderType 执行顺序
            // （gui 先 -> guiText 后）导致文字穿透到所有后续面板之上。
            // 使用 g.flush() 而非 Minecraft.renderBuffers().bufferSource().endBatch()，
            // 因为 GuiGraphics 有自己的内部缓冲区，仅 endBatch() 无法刷新。
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
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            if (window.isInsideWindow(mouseX, mouseY)) {
                window.renderOverlays(g, mouseX, mouseY);
                return;
            }
        }
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
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            if (window.mouseClicked(mouseX, mouseY, button)) {
                window.markBroughtToFront();
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            if (this.frontToBackWindows.get(i).mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        for (RtsWindowPanel window : this.frontToBackWindows) {
            handled = window.mouseReleased(mouseX, mouseY, button) || handled;
        }
        return handled;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            if (this.frontToBackWindows.get(i).mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }
}
