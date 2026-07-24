package com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public final class RtsFloatingWindowLayer {

    private final List<RtsPanel> frontToBackWindows;
    
    private boolean sortDirty;

    
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

    
    public List<RtsPanel> frontToBackWindows() {
        return this.frontToBackWindows;
    }

    

    public void renderFloatingWindows(GuiGraphics g, int mouseX, int mouseY) {
        if (this.frontToBackWindows.isEmpty()) return;

        
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

            
            RenderSystem.disableDepthTest();

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
            RtsPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;
            RtsPanel.ResizeCursor cursor = window.currentResizeCursor(mouseX, mouseY);
            if (cursor != RtsPanel.ResizeCursor.DEFAULT) {
                return cursor;
            }
            
            
            if (window.isInsideWindow(mouseX, mouseY)) {
                return RtsPanel.ResizeCursor.DEFAULT;
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

    

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        
        long[] timestamps = new long[this.frontToBackWindows.size()];
        for (int j = 0; j < this.frontToBackWindows.size(); j++) {
            timestamps[j] = this.frontToBackWindows.get(j).getLastClickTime();
        }

        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;
            int windowIdx = i;
            if (window.mouseClicked(mouseX, mouseY, button)) {
                
                
                
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
            RtsPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;
            if (window.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        for (RtsPanel window : this.frontToBackWindows) {
            if (!window.isOpen()) continue;
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
            RtsPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;
            if (window.mouseMoved(mouseX, mouseY)) {
                return;
            }
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;
            if (window.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (int i = this.frontToBackWindows.size() - 1; i >= 0; i--) {
            RtsPanel window = this.frontToBackWindows.get(i);
            if (!window.isOpen()) continue;

            
            if (window.handleWindowKeyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }

            
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
