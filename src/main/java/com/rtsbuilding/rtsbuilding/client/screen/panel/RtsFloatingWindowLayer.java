package com.rtsbuilding.rtsbuilding.client.screen.panel;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Front-to-back router for movable RTS windows.
 *
 * <p>The layer owns stacking and dispatch order only. Individual panels remain
 * responsible for their own gameplay state, which keeps the Forge port close to
 * the existing screen flow while giving Quick Build, Ultimine, settings, and
 * guide panels one consistent input path.
 */
public record RtsFloatingWindowLayer(List<RtsWindowPanel> frontToBackWindows) {

    public RtsFloatingWindowLayer(RtsWindowPanel... windows) {
        this(new ArrayList<>(List.of(windows)));
        for (int i = windows.length - 1; i >= 0; i--) {
            windows[i].markBroughtToFront();
        }
    }

    public void renderFloatingWindows(GuiGraphics g, int mouseX, int mouseY) {
        this.frontToBackWindows.sort(Comparator.comparingLong(RtsWindowPanel::getLastClickTime));
        int topmostHover = -1;
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            if (window.isVisibleWindow() && window.isInsideWindow(mouseX, mouseY)) {
                topmostHover = i;
                break;
            }
        }
        for (int i = 0; i < this.frontToBackWindows.size(); i++) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            boolean suppressHover = topmostHover >= 0 && i != topmostHover
                    && window.isVisibleWindow()
                    && window.isInsideWindow(mouseX, mouseY);
            window.setSkipHoverDetection(suppressHover);
            window.render(g, mouseX, mouseY, 0.0F);
            window.setSkipHoverDetection(false);
            g.flush();
        }
    }

    public void renderFloatingWindowOverlays(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            if (window.isVisibleWindow() && window.isInsideWindow(mouseX, mouseY)) {
                window.renderOverlays(g, mouseX, mouseY);
                return;
            }
        }
    }

    public RtsWindowPanel.ResizeCursor resizeCursorAt(double mouseX, double mouseY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel.ResizeCursor cursor = this.frontToBackWindows.get(i).currentResizeCursor(mouseX, mouseY);
            if (cursor != RtsWindowPanel.ResizeCursor.DEFAULT) {
                return cursor;
            }
        }
        return RtsWindowPanel.ResizeCursor.DEFAULT;
    }

    public boolean isMouseOverWindowOrResizableBorder(double mouseX, double mouseY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsWindowPanel window = this.frontToBackWindows.get(i);
            if (window.isVisibleWindow()
                    && (window.isInsideWindow(mouseX, mouseY) || window.isInsideResizableBorder(mouseX, mouseY))) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            if (this.frontToBackWindows.get(i).mouseClicked(mouseX, mouseY, button)) {
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

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            if (this.frontToBackWindows.get(i).keyPressed(keyCode, scanCode, modifiers)) {
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
