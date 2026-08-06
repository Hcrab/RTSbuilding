package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底栏整体框体、页签、状态文字及右侧入口共享的语义色板。
 */
public final class BottomPanelHeaderStyle {
    public static final UiColor PANEL_BACKGROUND = new UiColor(0xD014151A);
    public static final UiColor PANEL_BORDER_LIGHT = new UiColor(0xFF64788E);
    public static final UiColor PANEL_BORDER_DARK = new UiColor(0xFF0D1015);
    public static final UiColor HEADER_BACKGROUND = new UiColor(0xCC1C242F);
    public static final UiColor LOGO_TEXT = new UiColor(0xFFF2F6FB);
    public static final UiColor TAB_IDLE_BACKGROUND = new UiColor(0x8826303B);
    public static final UiColor TAB_HOVER_BACKGROUND = new UiColor(0xAA334052);
    public static final UiColor TAB_ACTIVE_BACKGROUND = new UiColor(0xCC355B4C);
    public static final UiColor TAB_IDLE_BORDER = new UiColor(0xFF536679);
    public static final UiColor TAB_ACTIVE_BORDER = new UiColor(0xFF7CCB93);
    public static final UiColor TAB_IDLE_TEXT = new UiColor(0xFFD8E2EE);
    public static final UiColor TAB_ACTIVE_TEXT = new UiColor(0xFFFFFFFF);
    public static final UiColor STATUS_TEXT = new UiColor(0xFFD8E2EE);
    public static final UiColor ACTION_IDLE_BACKGROUND = new UiColor(0xAA2B3542);
    public static final UiColor ACTION_HOVER_BACKGROUND = new UiColor(0xCC41576F);
    public static final UiColor ACTION_BORDER = new UiColor(0xFF5D7287);
    public static final UiColor ACTION_TEXT = new UiColor(0xFFEAF4FF);
    public static final UiColor REFRESH_SCANNING_BACKGROUND = new UiColor(0xCC3F627E);
    public static final UiColor REFRESH_DIRTY_BACKGROUND = new UiColor(0xCC248C3A);
    public static final UiColor REFRESH_DIRTY_HOVER_BACKGROUND = new UiColor(0xDD2FAF49);
    public static final UiColor REFRESH_DIRTY_BORDER = new UiColor(0xFF92F7A0);
    public static final UiColor PLUGIN_IDLE_BACKGROUND = new UiColor(0xAA273441);
    public static final UiColor PLUGIN_HOVER_BACKGROUND = new UiColor(0xCC3A4D60);
    public static final UiColor PLUGIN_TEXT = new UiColor(0xFFEAF2FF);
    public static final UiColor TAB_ANIMATION_OVERLAY =
            RtsMainlineTheme.SELECTION_ANIMATION_OVERLAY;
    public static final UiColor TRANSPARENT = RtsMainlineTheme.TRANSPARENT;

    private BottomPanelHeaderStyle() {
    }

    public static UiColor tabBackground(boolean active, boolean hovered) {
        if (active) {
            return TAB_ACTIVE_BACKGROUND;
        }
        return hovered ? TAB_HOVER_BACKGROUND : TAB_IDLE_BACKGROUND;
    }

    public static UiColor tabBorder(boolean active) {
        return active ? TAB_ACTIVE_BORDER : TAB_IDLE_BORDER;
    }

    public static UiColor tabText(boolean active) {
        return active ? TAB_ACTIVE_TEXT : TAB_IDLE_TEXT;
    }

    public static UiColor actionBackground(boolean hovered) {
        return hovered ? ACTION_HOVER_BACKGROUND : ACTION_IDLE_BACKGROUND;
    }

    public static UiColor refreshBackground(
            boolean scanning, boolean dirty, boolean hovered) {
        if (scanning) {
            return REFRESH_SCANNING_BACKGROUND;
        }
        if (dirty) {
            return hovered
                    ? REFRESH_DIRTY_HOVER_BACKGROUND
                    : REFRESH_DIRTY_BACKGROUND;
        }
        return actionBackground(hovered);
    }

    public static UiColor refreshBorder(boolean dirty) {
        return dirty ? REFRESH_DIRTY_BORDER : ACTION_BORDER;
    }

    public static UiColor pluginBackground(boolean hovered) {
        return hovered ? PLUGIN_HOVER_BACKGROUND : PLUGIN_IDLE_BACKGROUND;
    }
}
