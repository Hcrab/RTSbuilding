package com.rtsbuilding.rtsbuilding.uikit.theme;

/** 漏斗缓存按钮、面板、行与物品槽的共享语义色板。 */
public final class FunnelBufferStyle {
    public static final UiColor TOGGLE_VISIBLE = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.CONTROL_SELECTED, 0XAA2C4E3D);
    public static final UiColor TOGGLE_HIDDEN = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.CONTROL_IDLE, 0XAA2A2D36);
    public static final UiColor TOGGLE_HOVER = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.CONTROL_HOVER, 0XBB3A4A58);
    public static final UiColor PANEL_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.CONTROL_IDLE, 0XAA17191F);
    public static final UiColor ROW_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.CONTROL_IDLE, 0X88303845);
    public static final UiColor SLOT_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.SLOT_IDLE, 0XAA1E222A);
    public static final UiColor ROW_HOVER_OVERLAY = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.CONTROL_HOVER, 0X33FFFFFF);
    public static final UiColor PRIMARY_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.TEXT_PRIMARY, 0XFFFFFFFF);
    public static final UiColor TITLE_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.TEXT_PRIMARY, 0XFFF0F0F0);
    public static final UiColor COUNT_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.TEXT_PRIMARY, 0XFFFFDFAE);
    public static final UiColor EMPTY_TEXT = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.TEXT_MUTED, 0X99B4BCC8);

    private FunnelBufferStyle() {
    }

    public static UiColor toggle(boolean panelVisible) {
        return panelVisible ? TOGGLE_VISIBLE : TOGGLE_HIDDEN;
    }

    /** 漏斗按钮的显隐和悬停都只做视觉插值，不延迟面板开关。 */
    public static UiColor toggle(double visibleProgress, double hoverProgress) {
        UiColor state = UiColor.interpolate(
                TOGGLE_HIDDEN, TOGGLE_VISIBLE, visibleProgress);
        return UiColor.interpolate(state, TOGGLE_HOVER, hoverProgress);
    }
}
