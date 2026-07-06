package com.rtsbuilding.rtsbuilding.client.screen.event;

/**
 * 按键释放事件。
 */
public record KeyReleaseEvent(int keyCode, int scanCode, int modifiers, boolean consumed) implements InputEvent {
    public KeyReleaseEvent(int keyCode, int scanCode, int modifiers) { this(keyCode, scanCode, modifiers, false); }
    @Override public InputEvent consume() { return new KeyReleaseEvent(keyCode, scanCode, modifiers, true); }
}
