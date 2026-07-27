package com.rtsbuilding.rtsbuilding.client.presentation.event.model;

public record KeyPressEvent(int keyCode, int scanCode, int modifiers, boolean consumed) implements InputEvent {
    public KeyPressEvent(int keyCode, int scanCode, int modifiers) { this(keyCode, scanCode, modifiers, false); }
    @Override public InputEvent consume() { return new KeyPressEvent(keyCode, scanCode, modifiers, true); }
}
