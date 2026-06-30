package com.rtsbuilding.rtsbuilding.client.screen.panel.base;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 以前后顺序路由可移动 RTS 窗口的输入和渲染。
 *
 * <p>该层负责窗口堆叠、z 顺序渲染和输入分发。它
 * 刻意不关心窗口内部做什么、代表什么游戏操作、
 * 或持久化 UI 状态如何保存。这使当前主屏幕行为保持不变，
 * 同时为未来的窗口提供统一的渲染和输入接入点。
 *
 * <p>窗口按 {@link RtsPanel#getLastClickTime} 升序渲染——
 * 最近点击的窗口显示在最上层。点击任何窗口会自动将其置顶。
 * 排序采用懒策略：仅当有点击发生时标记脏，渲染时才触发实际排序。
 */
public final class RtsFloatingWindowLayer {

    private final List<RtsPanel> frontToBackWindows;
    /** 是否需要重新排序（点击窗口时标记，渲染时消费） */
    private boolean sortDirty;

    /** 标记排序为脏，下次渲染时自动重新排序。
     * 在程序化打开/关闭面板后调用，确保新面板处于正确 z 顺序。 */
    public void markSortDirty() {
        this.sortDirty = true;
    }

    public RtsFloatingWindowLayer(RtsPanel... frontToBackWindows) {
        this.frontToBackWindows = new ArrayList<>(List.of(frontToBackWindows));
        this.sortDirty = true;
        for (int i = frontToBackWindows.length - 1; i >= 0; i--) {
            frontToBackWindows[i].markBroughtToFront();
        }
    }

    /** 返回底层窗口列表，供 BuilderScreen 在 init() 中注册窗口。 */
    public List<RtsPanel> frontToBackWindows() {
        return this.frontToBackWindows;
    }

    // ======================== Z 顺序渲染 ========================

    public void renderFloatingWindows(GuiGraphics g, int mouseX, int mouseY) {
        if (this.frontToBackWindows.isEmpty()) return;

        // 仅在有点击发生后重新排序，避免每帧 O(n log n)
        if (this.sortDirty) {
            this.frontToBackWindows.sort(Comparator.comparingLong(RtsPanel::getLastClickTime));
            this.sortDirty = false;
        }

        int topmostHoverIdx = -1;
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsPanel window = this.frontToBackWindows.get(i);
            if (window.isOpen() && window.isInsideWindow(mouseX, mouseY)) {
                topmostHoverIdx = i;
                break;
            }
        }

        for (int i = 0; i < this.frontToBackWindows.size(); i++) {
            RtsPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;
            boolean shouldSuppress = topmostHoverIdx >= 0 && i != topmostHoverIdx
                    && window.isInsideWindow(mouseX, mouseY);
            window.setSkipHoverDetection(shouldSuppress);
            try {
                window.render(g, mouseX, mouseY, 0.0F);
            } finally {
                window.setSkipHoverDetection(false);
            }
        }
    }

    public void renderFloatingWindowOverlays(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsPanel window = this.frontToBackWindows.get(i);
            if (window.isOpen() && window.isInsideWindow(mouseX, mouseY)) {
                window.renderOverlays(g, mouseX, mouseY);
                return;
            }
        }
    }

    public RtsPanel.ResizeCursor resizeCursorAt(double mouseX, double mouseY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsPanel.ResizeCursor cursor = this.frontToBackWindows.get(i).currentResizeCursor(mouseX, mouseY);
            if (cursor != RtsPanel.ResizeCursor.DEFAULT) {
                return cursor;
            }
        }
        return RtsPanel.ResizeCursor.DEFAULT;
    }

    public boolean isMouseOverWindowOrResizableBorder(double mouseX, double mouseY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsPanel window = this.frontToBackWindows.get(i);
            if (window.isOpen()
                    && (window.isInsideWindow(mouseX, mouseY) || window.isInsideResizableBorder(mouseX, mouseY))) {
                return true;
            }
        }
        return false;
    }

    // ======================== 输入路由 ========================

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 快照所有面板的时间戳，用于检测点击处理过程中是否有面板被程序化打开
        long[] timestamps = new long[this.frontToBackWindows.size()];
        for (int j = 0; j < this.frontToBackWindows.size(); j++) {
            timestamps[j] = this.frontToBackWindows.get(j).getLastClickTime();
        }

        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsPanel window = this.frontToBackWindows.get(i);
            int windowIdx = i;
            if (window.mouseClicked(mouseX, mouseY, button)) {
                // 检查在被点击面板处理点击的过程中，是否有其他面板被程序化打开（时间戳被更新）。
                // 例如设置面板（GearMenuPanel）点击调色盘按钮打开了调色盘面板（ColorPickerPanel），
                // 此时调色盘面板的时间戳是更新的，我们不应当再把设置面板置顶。
                boolean otherPanelBroughtToFront = false;
                for (int j = 0; j < this.frontToBackWindows.size(); j++) {
                    if (j != windowIdx && this.frontToBackWindows.get(j).getLastClickTime() > timestamps[j]) {
                        otherPanelBroughtToFront = true;
                        break;
                    }
                }
                if (!otherPanelBroughtToFront) {
                    window.markBroughtToFront();
                }
                this.sortDirty = true;
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
        for (RtsPanel window : this.frontToBackWindows) {
            handled = window.mouseReleased(mouseX, mouseY, button) || handled;
        }
        return handled;
    }

    public boolean consumeAnyBoundsDirty() {
        boolean dirty = false;
        for (RtsPanel window : this.frontToBackWindows) {
            dirty = window.consumeBoundsDirty() || dirty;
        }
        return dirty;
    }

    public void mouseMoved(double mouseX, double mouseY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            if (this.frontToBackWindows.get(i).mouseMoved(mouseX, mouseY)) {
                return;
            }
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            if (this.frontToBackWindows.get(i).mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;

            // 子类优先级更高——先问子类要不要吃这个事件（如搜索框 ESC 清空）
            if (window.handleWindowKeyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }

            // ESC 关闭（setOpen(false) 内部会递归关闭所有子面板）
            if (keyCode == GLFW.GLFW_KEY_ESCAPE && window.closable) {
                window.setOpen(false);
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            if (this.frontToBackWindows.get(i).charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }
}
