package com.rtsbuilding.rtsbuilding.uicore.ultimine;

/** 连锁破坏会话的纯动作。 */
public final class UltimineUiAction {
    public enum Type { CONFIRM_PREVIEW, CANCEL, SET_LIMIT, SERVER_PROGRESS }

    public final Type type;
    public final int value;
    public final int total;

    private UltimineUiAction(Type type, int value, int total) {
        this.type = type;
        this.value = value;
        this.total = total;
    }

    public static UltimineUiAction confirmPreview() {
        return new UltimineUiAction(Type.CONFIRM_PREVIEW, 0, 0);
    }

    public static UltimineUiAction cancel() {
        return new UltimineUiAction(Type.CANCEL, 0, 0);
    }

    public static UltimineUiAction limit(int value) {
        return new UltimineUiAction(Type.SET_LIMIT, value, 0);
    }

    public static UltimineUiAction progress(int processed, int total) {
        return new UltimineUiAction(Type.SERVER_PROGRESS, processed, total);
    }
}
