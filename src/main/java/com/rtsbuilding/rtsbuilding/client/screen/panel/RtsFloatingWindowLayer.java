package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.input.RtsPointerCapture;
import com.rtsbuilding.rtsbuilding.client.input.RtsInputResult;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyboardFocus;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 浮动窗口的唯一层级、渲染与输入路由器。
 *
 * <p>按下事件只命中鼠标下方最上层窗口。命中后，拖动和释放由同一窗口独占，
 * 不再广播给所有面板；滚轮位于窗口内时也始终阻断镜头，即使内容已经滚到边缘。
 * 该类不拥有任何玩法动作，旧面板与新面板都通过同一个布尔消费桥接进入现有
 * BuilderScreen，便于把这套规则反哺旧版本。</p>
 */
public final class RtsFloatingWindowLayer {
    private final List<RtsWindowPanel> frontToBackWindows;
    private final RtsPointerCapture<RtsWindowPanel> pointerCapture = new RtsPointerCapture<>();
    private final RtsKeyboardFocus<RtsWindowPanel> keyboardFocus = new RtsKeyboardFocus<>();

    public RtsFloatingWindowLayer(RtsWindowPanel... frontToBackWindows) {
        this.frontToBackWindows = new ArrayList<>(List.of(frontToBackWindows));
        for (int i = frontToBackWindows.length - 1; i >= 0; i--) {
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
            window.render(g, mouseX, mouseY, 0.0F);
            window.setSkipHoverDetection(false);
            /*
             * 只切换 GUI 层级，不主动结束 Minecraft 的共享 buffer。
             * 共享 buffer 的生命周期属于渲染管线；窗口层自行 endBatch 会破坏
             * Sodium/Embeddium 等渲染器对批次边界的假设。
             */
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
        return mouseClickResult(mouseX, mouseY, button).blocksFurtherInput();
    }

    public RtsInputResult mouseClickResult(double mouseX, double mouseY, int button) {
        sortBackToFront();
        int index = topmostWindowIndexAt(mouseX, mouseY, true);
        if (index < 0) {
            this.keyboardFocus.blur();
            return RtsInputResult.PASS;
        }

        RtsWindowPanel window = this.frontToBackWindows.get(index);
        /*
         * 边框/窗口矩形本身就是世界输入屏障。即使具体内容没有动作，也不能让
         * 同一次点击穿透到挖掘、放置或镜头拖动。
         */
        window.mouseClicked(mouseX, mouseY, button);
        window.markBroughtToFront();
        this.pointerCapture.capture(button, window);
        this.keyboardFocus.focus(window);
        return RtsInputResult.CAPTURE_POINTER;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.pointerCapture.owner(button)
                .map(window -> {
                    window.mouseDragged(mouseX, mouseY, button, dragX, dragY);
                    return true;
                })
                .orElse(false);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.pointerCapture.release(button)
                .map(window -> {
                    window.mouseReleased(mouseX, mouseY, button);
                    return true;
                })
                .orElse(false);
    }

    public void cancelPointerCapture() {
        this.pointerCapture.clear();
        this.keyboardFocus.blur();
    }

    public boolean hasPointerCapture(int button) {
        return this.pointerCapture.hasCapture(button);
    }

    public boolean consumeAnyBoundsDirty() {
        boolean dirty = false;
        for (RtsWindowPanel window : this.frontToBackWindows) {
            dirty = window.consumeBoundsDirty() || dirty;
        }
        return dirty;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return mouseScrollResult(mouseX, mouseY, scrollX, scrollY).blocksFurtherInput();
    }

    public RtsInputResult mouseScrollResult(double mouseX, double mouseY, double scrollX, double scrollY) {
        sortBackToFront();
        int index = topmostWindowIndexAt(mouseX, mouseY, false);
        if (index < 0) {
            return RtsInputResult.PASS;
        }
        this.frontToBackWindows.get(index).mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        return RtsInputResult.BLOCK_WORLD;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        RtsWindowPanel focused = visibleKeyboardWindow();
        if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        sortBackToFront();
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            if (window != focused && window.keyPressed(keyCode, scanCode, modifiers)) {
                this.keyboardFocus.focus(window);
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        RtsWindowPanel focused = visibleKeyboardWindow();
        return focused != null && focused.charTyped(codePoint, modifiers);
    }

    private RtsWindowPanel visibleKeyboardWindow() {
        RtsWindowPanel focused = this.keyboardFocus.owner().orElse(null);
        if (focused != null && focused.isVisibleWindow()) {
            return focused;
        }
        this.keyboardFocus.blur();
        return null;
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
