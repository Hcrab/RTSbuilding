package com.rtsbuilding.rtsbuilding.uicore.blueprint;

/** 底部蓝图空间的完整玩家输入语义。 */
public final class BlueprintLibraryUiAction {
    public enum Type {
        OPEN_FOLDER, IMPORT_FILE, SYNC_CREATE, TOGGLE_CAPTURE,
        SET_QUERY, FOCUS_SEARCH, BLUR_SEARCH, SCROLL_ROWS,
        SELECT_ENTRY, SAVE_AS_ENTRY, RENAME_ENTRY, DELETE_ENTRY
    }

    public final Type type;
    public final int amount;
    public final String text;

    private BlueprintLibraryUiAction(Type type, int amount, String text) {
        if (type == null) throw new IllegalArgumentException("type");
        this.type = type;
        this.amount = amount;
        this.text = text == null ? "" : text;
    }

    public static BlueprintLibraryUiAction simple(Type type) {
        return new BlueprintLibraryUiAction(type, 0, "");
    }

    public static BlueprintLibraryUiAction amount(Type type, int amount) {
        return new BlueprintLibraryUiAction(type, amount, "");
    }

    public static BlueprintLibraryUiAction text(Type type, String text) {
        return new BlueprintLibraryUiAction(type, 0, text);
    }
}
