package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底栏搜索与分页工具条的共享语义色板。
 */
public final class BottomPanelBrowseStyle {
    public static final UiColor SEARCH_IDLE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.SURFACE_SUNKEN, 0XAA1A222C);
    public static final UiColor SEARCH_FOCUSED_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.FOCUS_RING, 0XCC09111B);
    public static final UiColor SEARCH_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF5C6F84);
    public static final UiColor SEARCH_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_SOFT, 0XFF0C1015);
    public static final UiColor CLEAR_IDLE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XAA2A313B);
    public static final UiColor CLEAR_FOCUSED_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.FOCUS_RING, 0XAA3B4755);
    public static final UiColor CLEAR_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF637283);
    public static final UiColor CLEAR_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_SOFT, 0XFF101318);
    public static final UiColor PAGE_BUTTON_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XAA2A2A2A);
    public static final UiColor TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFFFFFFF);
    public static final UiColor MUTED_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_MUTED, 0XFF99A6B5);
    public static final UiColor PLACEHOLDER_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_MUTED, 0XFF73859A);

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
