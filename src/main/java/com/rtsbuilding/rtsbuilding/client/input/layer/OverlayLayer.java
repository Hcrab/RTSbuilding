package com.rtsbuilding.rtsbuilding.client.input.layer;

import com.rtsbuilding.rtsbuilding.client.input.InputLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;


public final class OverlayLayer implements InputLayer {

    private boolean overlaySearchFocused;
    private String overlaySearchDraft = "";

    @Override
    public boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return false;
        return mc.screen instanceof AbstractContainerScreen;
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen)) return false;

        
        
        return false; 
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && overlaySearchFocused) {
            overlaySearchFocused = false;
            overlaySearchDraft = "";
            return true;
        }
        return false;
    }

    public String getOverlaySearchDraft() {
        return overlaySearchDraft;
    }
}
