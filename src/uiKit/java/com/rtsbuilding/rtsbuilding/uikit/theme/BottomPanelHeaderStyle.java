package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底栏整体框体、页签、状态文字及右侧入口共享的语义色板。
 */
public final class BottomPanelHeaderStyle {
    public static final UiColor PANEL_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XD014151A);
    public static final UiColor PANEL_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF64788E);
    public static final UiColor PANEL_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_SOFT, 0XFF0D1015);
    public static final UiColor HEADER_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.SURFACE_RAISED, 0XCC1C242F);
    public static final UiColor LOGO_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFF2F6FB);
    public static final UiColor TAB_IDLE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0X8826303B);
    public static final UiColor TAB_HOVER_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_HOVER, 0XAA334052);
    public static final UiColor TAB_ACTIVE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_SELECTED, 0XCC355B4C);
    public static final UiColor TAB_IDLE_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF536679);
    public static final UiColor TAB_ACTIVE_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.ACCENT_PRIMARY, 0XFF7CCB93);
    public static final UiColor TAB_IDLE_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFD8E2EE);
    public static final UiColor TAB_ACTIVE_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_ON_ACCENT, 0XFFFFFFFF);
    public static final UiColor STATUS_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFD8E2EE);
    public static final UiColor ACTION_IDLE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XAA2B3542);
    public static final UiColor ACTION_HOVER_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_HOVER, 0XCC41576F);
    public static final UiColor ACTION_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF5D7287);
    public static final UiColor ACTION_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFEAF4FF);
    public static final UiColor REFRESH_SCANNING_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XCC3F627E);
    public static final UiColor REFRESH_DIRTY_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XCC248C3A);
    public static final UiColor REFRESH_DIRTY_HOVER_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_HOVER, 0XDD2FAF49);
    public static final UiColor REFRESH_DIRTY_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF92F7A0);
    public static final UiColor PLUGIN_IDLE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XAA273441);
    public static final UiColor PLUGIN_HOVER_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_HOVER, 0XCC3A4D60);
    public static final UiColor PLUGIN_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFEAF2FF);
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
