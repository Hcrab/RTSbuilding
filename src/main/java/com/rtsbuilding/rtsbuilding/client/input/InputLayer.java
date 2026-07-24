package com.rtsbuilding.rtsbuilding.client.input;

import net.minecraft.client.Minecraft;


public interface InputLayer {

    
    default boolean isActive() {
        return true;
    }

    
    default void onTickPre(Minecraft mc) {}

    
    default void onTickPost(Minecraft mc) {}

    
    default boolean onMouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    
    default boolean onMouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    
    default boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    
    default boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    
    default boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    
    default boolean onCharTyped(char codePoint, int modifiers) {
        return false;
    }
}
