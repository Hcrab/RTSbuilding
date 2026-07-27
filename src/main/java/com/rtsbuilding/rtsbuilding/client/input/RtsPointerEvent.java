package com.rtsbuilding.rtsbuilding.client.input;

import net.minecraft.client.gui.GuiScreen;

/** Forge 1.12 LWJGL 鼠标事件的不可变快照，并携带是否已被 RTS 消费。 */
final class RtsPointerEvent {
    private final GuiScreen screen;
    private final double mouseX;
    private final double mouseY;
    private final int button;
    private final double scrollDeltaY;
    private boolean canceled;

    RtsPointerEvent(GuiScreen screen, double mouseX, double mouseY, int button, double scrollDeltaY) {
        this.screen = screen;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.button = button;
        this.scrollDeltaY = scrollDeltaY;
    }

    GuiScreen getScreen() { return screen; }
    double getMouseX() { return mouseX; }
    double getMouseY() { return mouseY; }
    int getButton() { return button; }
    double getScrollDeltaY() { return scrollDeltaY; }
    void setCanceled(boolean canceled) { this.canceled = canceled; }
    boolean isCanceled() { return canceled; }
}
