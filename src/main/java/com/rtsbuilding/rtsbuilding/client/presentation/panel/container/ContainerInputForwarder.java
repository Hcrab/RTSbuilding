package com.rtsbuilding.rtsbuilding.client.presentation.panel.container;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jetbrains.annotations.Nullable;


public final class ContainerInputForwarder {

    @Nullable
    private AbstractContainerScreen<?> screen;

    public ContainerInputForwarder(AbstractContainerScreen<?> screen) {
        this.screen = screen;
    }

    
    public void clear() {
        if (screen != null) {
            screen.removed();
            screen = null;
        }
    }

    
    public boolean hasScreen() {
        return screen != null;
    }

    
    @Nullable
    public AbstractContainerScreen<?> getScreen() {
        return screen;
    }

    
    public void init(int width, int height) {
        if (screen != null) {
            screen.init(net.minecraft.client.Minecraft.getInstance(), width, height);
        }
    }

    
    public void tick() {
        if (screen != null) screen.tick();
    }

    

    public void mouseClicked(double mx, double my, int button) {
        if (screen != null) screen.mouseClicked(mx, my, button);
    }

    
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (screen != null) {
            screen.mouseDragged(mx, my, button, dx, dy);
            return true;
        }
        return false;
    }

    
    public boolean mouseReleased(double mx, double my, int button) {
        if (screen != null) {
            screen.mouseReleased(mx, my, button);
            return true;
        }
        return false;
    }

    
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (screen != null) return screen.mouseScrolled(mx, my, sx, sy);
        return false;
    }

    public void mouseMoved(double mx, double my) {
        if (screen != null) screen.mouseMoved(mx, my);
    }

    

    
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (screen != null) return screen.keyPressed(keyCode, scanCode, modifiers);
        return false;
    }

    
    public boolean charTyped(char codePoint, int modifiers) {
        if (screen != null) return screen.charTyped(codePoint, modifiers);
        return false;
    }
}
