package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;

import com.rtsbuilding.rtsbuilding.client.input.RtsInputResult;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 浮动窗口的唯一层级、渲染与输入入口。
 *
 * <p>26.1 通过 Extractor 的 stratum 划分窗口提交层，而不是结束 Minecraft 的共享
 * buffer。输入完全委托给 {@link RtsFloatingWindowInputRouter}：按下、拖拽、释放、
 * 滚轮、键盘、字符和 Escape 均使用同一份 Core 捕获/焦点/模态规则。</p>
 */
public final class RtsFloatingWindowLayer {
    private final List<RtsWindowPanel> frontToBackWindows;
    private final RtsFloatingWindowInputRouter inputRouter;

    public RtsFloatingWindowLayer(RtsWindowPanel... frontToBackWindows) {
        this.frontToBackWindows = new ArrayList<>(List.of(frontToBackWindows));
        this.inputRouter = new RtsFloatingWindowInputRouter(this.frontToBackWindows);
        for (int i = frontToBackWindows.length - RtsMainlineLayout.D1; i >= 0; i--) {
            frontToBackWindows[i].markBroughtToFront();
        }
    }

    public List<RtsWindowPanel> frontToBackWindows() {
        return this.frontToBackWindows;
    }

    public void renderFloatingWindows(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        sortBackToFront();
        int topmostHoverIdx = topmostWindowIndexAt(mouseX, mouseY, false);
        for (int i = 0; i < this.frontToBackWindows.size(); i++) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            boolean shouldSuppress = topmostHoverIdx >= 0 && i != topmostHoverIdx
                    && window.isVisibleWindow()
                    && window.isInsideWindow(mouseX, mouseY);
            window.setSkipHoverDetection(shouldSuppress);
            g.pose().pushMatrix();
            try {
                window.render(g, mouseX, mouseY, 0.0F);
            } finally {
                g.pose().popMatrix();
                window.setSkipHoverDetection(false);
            }
            // Extractor 的独立 stratum 代替旧版手工 buffer flush，避免窗口与底栏
            // 物品、数量文本在同一批次互相穿透。
            g.nextStratum();
        }
    }

    public void renderFloatingWindowOverlays(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        sortBackToFront();
        int index = topmostWindowIndexAt(mouseX, mouseY, false);
        if (index >= 0) {
            this.frontToBackWindows.get(index).renderOverlays(g, mouseX, mouseY);
        }
    }

    public RtsWindowPanel.ResizeCursor resizeCursorAt(double mouseX, double mouseY) {
        sortBackToFront();
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel.ResizeCursor cursor =
                    this.frontToBackWindows.get(i).currentResizeCursor(mouseX, mouseY);
            if (cursor != RtsWindowPanel.ResizeCursor.DEFAULT) {
                return cursor;
            }
        }
        return RtsWindowPanel.ResizeCursor.DEFAULT;
    }

    public boolean isMouseOverWindowOrResizableBorder(double mouseX, double mouseY) {
        return topmostWindowIndexAt(mouseX, mouseY, true) >= 0;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.inputRouter.mouseClicked(mouseX, mouseY, button);
    }

    /** 保留旧 boolean/result 桥接，避免未迁移调用者重新实现输入策略。 */
    public RtsInputResult mouseClickResult(double mouseX, double mouseY, int button) {
        return mouseClicked(mouseX, mouseY, button)
                ? RtsInputResult.CAPTURE_POINTER : RtsInputResult.PASS;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        return this.inputRouter.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.inputRouter.mouseReleased(mouseX, mouseY, button);
    }

    /** 旧生命周期入口委托给 Core 瞬时状态清理。 */
    public void cancelPointerCapture() {
        clearTransientInputState();
    }

    public void clearTransientInputState() {
        this.inputRouter.clearTransientState();
    }

    public boolean hasPointerCapture(int button) {
        return this.inputRouter.hasPointerCapture(button);
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

    public RtsInputResult mouseScrollResult(double mouseX, double mouseY,
                                             double scrollX, double scrollY) {
        return mouseScrolled(mouseX, mouseY, scrollX, scrollY)
                ? RtsInputResult.BLOCK_WORLD : RtsInputResult.PASS;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.inputRouter.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return this.inputRouter.charTyped(codePoint, modifiers);
    }

    private void sortBackToFront() {
        this.frontToBackWindows.sort(Comparator.comparingLong(RtsWindowPanel::getLastClickTime));
    }

    private int topmostWindowIndexAt(double mouseX, double mouseY, boolean includeResizeBorder) {
        sortBackToFront();
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            if (!window.isVisibleWindow()) {
                continue;
            }
            if (window.isInsideWindow(mouseX, mouseY)
                    || includeResizeBorder && window.isInsideResizableBorder(mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }
}
