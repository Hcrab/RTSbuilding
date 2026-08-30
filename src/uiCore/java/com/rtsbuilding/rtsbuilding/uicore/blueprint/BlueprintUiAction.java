package com.rtsbuilding.rtsbuilding.uicore.blueprint;

/** BlueprintWindowPanel 全部玩家操作的纯 Java 表达；平台适配层负责执行世界/文件/网络副作用。 */
public final class BlueprintUiAction {
    public enum Type {
        SELECT_PREVIOUS, SELECT_NEXT, OPEN_MATERIALS, CLOSE_MATERIALS, SCROLL_MATERIALS,
        ACCEPT_CAPTURE_POINT, MOVE_CAPTURE, RESIZE_CAPTURE, SET_CAPTURE_SIZE, SAVE_CAPTURE, CANCEL_CAPTURE,
        PIN_PREVIEW, SET_ANCHOR, NUDGE_ANCHOR, NUDGE_ANCHOR_RELATIVE,
        ROTATE_Y, ROTATE_X, ROTATE_Z, RESET_ROTATION,
        BUILD, CLEAR, OPEN_NAME_CAPTURE, OPEN_NAME_RENAME, SET_NAME_DRAFT, APPEND_NAME_CHAR,
        BACKSPACE_NAME, CONFIRM_NAME, CANCEL_NAME
    }

    public final Type type;
    public final int x;
    public final int y;
    public final int z;
    public final String text;

    private BlueprintUiAction(Type type, int x, int y, int z, String text) {
        if (type == null) throw new IllegalArgumentException("type");
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.text = text == null ? "" : text;
    }

    public static BlueprintUiAction simple(Type type) {
        return new BlueprintUiAction(type, 0, 0, 0, "");
    }

    public static BlueprintUiAction vector(Type type, int x, int y, int z) {
        return new BlueprintUiAction(type, x, y, z, "");
    }

    public static BlueprintUiAction text(Type type, String text) {
        return new BlueprintUiAction(type, 0, 0, 0, text);
    }
}
