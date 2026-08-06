package com.rtsbuilding.rtsbuilding.uicore.craft;

/** 合成数量窗的选择、输入和确认动作。 */
public final class CraftQuantityAction {
    public enum Type { SELECT, MOVE, ADJUST, APPEND_DIGITS, BACKSPACE, CLEAR, CONFIRM, CANCEL }
    public final Type type;
    public final int value;
    public final String text;

    private CraftQuantityAction(Type type, int value, String text) {
        this.type = type;
        this.value = value;
        this.text = text == null ? "" : text;
    }

    public static CraftQuantityAction value(Type type, int value) {
        return new CraftQuantityAction(type, value, "");
    }

    public static CraftQuantityAction text(String text) {
        return new CraftQuantityAction(Type.APPEND_DIGITS, 0, text);
    }

    public static CraftQuantityAction simple(Type type) {
        return new CraftQuantityAction(type, 0, "");
    }
}
