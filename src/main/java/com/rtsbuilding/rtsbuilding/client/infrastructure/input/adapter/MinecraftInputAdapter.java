package com.rtsbuilding.rtsbuilding.client.infrastructure.input.adapter;

import com.rtsbuilding.rtsbuilding.client.application.port.InputPort;

public final class MinecraftInputAdapter implements InputPort {
    @Override
    public void onMouseClick(double mouseX, double mouseY, int button) {}

    @Override
    public void onMouseRelease(double mouseX, double mouseY, int button) {}

    @Override
    public void onMouseScroll(double delta) {}

    @Override
    public void onKeyPress(int keyCode, int scanCode, int modifiers) {}

    @Override
    public void onKeyRelease(int keyCode, int scanCode, int modifiers) {}
}
