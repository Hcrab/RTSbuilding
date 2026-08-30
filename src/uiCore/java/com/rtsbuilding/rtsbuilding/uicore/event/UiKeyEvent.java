package com.rtsbuilding.rtsbuilding.uicore.event;

/** 平台无关的键盘与字符输入事件。 */
public final class UiKeyEvent {
    public enum Type {
        PRESS,
        RELEASE,
        CHAR_TYPED
    }

    private final Type type;
    private final int keyCode;
    private final int scanCode;
    private final int modifiers;
    private final char character;

    public UiKeyEvent(Type type, int keyCode, int scanCode, int modifiers, char character) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        this.type = type;
        this.keyCode = keyCode;
        this.scanCode = scanCode;
        this.modifiers = modifiers;
        this.character = character;
    }

    public Type getType() {
        return type;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public int getScanCode() {
        return scanCode;
    }

    public int getModifiers() {
        return modifiers;
    }

    public char getCharacter() {
        return character;
    }
}
