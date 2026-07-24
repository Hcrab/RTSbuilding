package com.rtsbuilding.rtsbuilding.client.presentation.event.model;


public record CharEvent(char codePoint, int modifiers, boolean consumed) implements InputEvent {
    public CharEvent(char codePoint, int modifiers) { this(codePoint, modifiers, false); }
    @Override public InputEvent consume() { return new CharEvent(codePoint, modifiers, true); }
}
