package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底栏合成区在生产和离屏预览之间共享的语义色板。
 *
 * <p>这里只描述“待应用搜索”“显示不可用配方”“可合成/缺料”等业务视觉，不拥有布局、文本或动作。</p>
 */
public final class BottomPanelCraftStyle {
    public static final UiColor PANEL_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XAA141922);
    public static final UiColor PANEL_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF637993);
    public static final UiColor PANEL_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_SOFT, 0XFF0D1218);
    public static final UiColor TITLE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFEAF2FF);

    public static final UiColor SEARCH_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.SURFACE_SUNKEN, 0XAA1E2731);
    public static final UiColor SEARCH_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF5E738A);
    public static final UiColor SEARCH_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_SOFT, 0XFF111921);
    public static final UiColor SEARCH_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFEAF2FF);
    public static final UiColor SEARCH_UNEDITABLE_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_MUTED, 0XFFAAB8C8);

    public static final UiColor BUTTON_IDLE = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XAA24303A);
    public static final UiColor APPLY_DIRTY = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_SELECTED, 0XAA4C6E39);
    public static final UiColor TOGGLE_MAKE = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_SELECTED, 0XAA2C5A41);
    public static final UiColor TOGGLE_ALL = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.WARNING, 0XAA5A3D2A);
    public static final UiColor BUTTON_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF6E8799);
    public static final UiColor TOGGLE_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF667D95);
    public static final UiColor BUTTON_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_SOFT, 0XFF111821);
    public static final UiColor BUTTON_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFFFFFFF);
    public static final UiColor BUTTON_TEXT_IDLE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_MUTED, 0XFFB8C7D6);

    public static final UiColor SLOT_EMPTY = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.SLOT_IDLE, 0XAA1A212B);
    public static final UiColor SLOT_AVAILABLE = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.SLOT_SELECTED, 0XAA214131);
    public static final UiColor SLOT_UNAVAILABLE = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.SLOT_MISSING, 0XAA3F2323);
    public static final UiColor SLOT_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF596D84);
    public static final UiColor SLOT_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_SOFT, 0XFF11171E);
    public static final UiColor SLOT_COUNT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFE8F4FF);
    public static final UiColor SLOT_UNAVAILABLE_OVERLAY = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.DESTRUCTIVE, 0X44220000);
    public static final UiColor SLOT_HOVER_OVERLAY = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.SLOT_HOVER, 0X22FFFFFF);

    private BottomPanelCraftStyle() {
    }

    public static UiColor applyBackground(boolean dirty) {
        return dirty ? APPLY_DIRTY : BUTTON_IDLE;
    }

    public static UiColor toggleBackground(boolean showUnavailable) {
        return showUnavailable ? TOGGLE_ALL : TOGGLE_MAKE;
    }

    public static UiColor slotBackground(boolean present, boolean available) {
        if (!present) {
            return SLOT_EMPTY;
        }
        return available ? SLOT_AVAILABLE : SLOT_UNAVAILABLE;
    }
}
