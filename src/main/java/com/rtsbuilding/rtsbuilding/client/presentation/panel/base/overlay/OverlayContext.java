package com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay;

public interface OverlayContext {
    int getX();
    int getY();
    int getWidth();
    int getHeight();
    int getLastMouseX();
    int getLastMouseY();
    boolean contains(int px, int py);
    boolean isDividerDragging();
}
