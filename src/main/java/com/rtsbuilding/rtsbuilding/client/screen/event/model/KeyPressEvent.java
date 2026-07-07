package com.rtsbuilding.rtsbuilding.client.screen.event.model;

/**
 * 按键按下事件。
 */
public record KeyPressEvent(int keyCode, int scanCode, int modifiers, boolean consumed) implements InputEvent {
    public KeyPressEvent(int keyCode, int scanCode, int modifiers) { this(keyCode, scanCode, modifiers, false); }
    @Override public InputEvent consume() { return new KeyPressEvent(keyCode, scanCode, modifiers, true); }
}
