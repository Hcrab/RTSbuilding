package com.rtsbuilding.rtsbuilding.uikit.theme;

/** RTS 浮窗文本框的共享语义色板。 */
public final class WindowTextBoxStyle {
    public static final UiColor TEXT = new UiColor(0xFFEAF2FF);
    public static final UiColor TEXT_UNEDITABLE = new UiColor(0xFF777F8B);
    public static final UiColor BACKGROUND = new UiColor(0xFF202832);
    public static final UiColor BORDER = new UiColor(0xFF3A4555);
    public static final UiColor BORDER_FOCUSED = new UiColor(0xFF6D7C90);
    public static final UiColor PLACEHOLDER = new UiColor(0xFF68778A);

    private WindowTextBoxStyle() {
    }

    public static UiColor border(boolean focused) {
        return focused ? BORDER_FOCUSED : BORDER;
    }
}
