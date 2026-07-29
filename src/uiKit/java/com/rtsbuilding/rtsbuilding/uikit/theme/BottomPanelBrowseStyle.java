package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底栏搜索与分页工具条的共享语义色板。
 */
public final class BottomPanelBrowseStyle {
    public static final UiColor SEARCH_IDLE_BACKGROUND = new UiColor(0xAA1A222C);
    public static final UiColor SEARCH_FOCUSED_BACKGROUND = new UiColor(0xCC09111B);
    public static final UiColor SEARCH_BORDER_LIGHT = new UiColor(0xFF5C6F84);
    public static final UiColor SEARCH_BORDER_DARK = new UiColor(0xFF0C1015);
    public static final UiColor CLEAR_IDLE_BACKGROUND = new UiColor(0xAA2A313B);
    public static final UiColor CLEAR_FOCUSED_BACKGROUND = new UiColor(0xAA3B4755);
    public static final UiColor CLEAR_BORDER_LIGHT = new UiColor(0xFF637283);
    public static final UiColor CLEAR_BORDER_DARK = new UiColor(0xFF101318);
    public static final UiColor PAGE_BUTTON_BACKGROUND = new UiColor(0xAA2A2A2A);
    public static final UiColor TEXT = new UiColor(0xFFFFFFFF);
    public static final UiColor MUTED_TEXT = new UiColor(0xFF99A6B5);
    public static final UiColor PLACEHOLDER_TEXT = new UiColor(0xFF73859A);

    private BottomPanelBrowseStyle() {
    }

    public static UiColor searchBackground(boolean focused) {
        return focused ? SEARCH_FOCUSED_BACKGROUND : SEARCH_IDLE_BACKGROUND;
    }

    public static UiColor clearBackground(boolean focused) {
        return focused ? CLEAR_FOCUSED_BACKGROUND : CLEAR_IDLE_BACKGROUND;
    }

    public static UiColor clearText(boolean hasValue) {
        return hasValue ? TEXT : MUTED_TEXT;
    }
}
