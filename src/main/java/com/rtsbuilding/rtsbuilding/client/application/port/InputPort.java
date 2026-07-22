package com.rtsbuilding.rtsbuilding.client.application.port;

public interface InputPort {
    void onMouseClick(double mouseX, double mouseY, int button);
    void onMouseRelease(double mouseX, double mouseY, int button);
    void onMouseScroll(double delta);
    void onKeyPress(int keyCode, int scanCode, int modifiers);
    void onKeyRelease(int keyCode, int scanCode, int modifiers);
}
