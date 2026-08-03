package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 原版容器上方 RTS 储存/合成 Overlay 的共享语义色板。
 *
 * <p>该 Overlay 不属于 {@code BuilderScreen}，但仍是活动生产入口。这里仅接管颜色状态，
 * 不拥有容器生命周期、JEI 交互、物品绘制或网络动作。</p>
 */
public final class ContainerOverlayStyle {
    public static final UiColor SEARCH_IDLE = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.SURFACE_SUNKEN, 0XAA202731);
    public static final UiColor SEARCH_FOCUSED = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.FOCUS_RING, 0XAA304153);
    public static final UiColor SEARCH_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_STRONG, 0XFF61758A);
    public static final UiColor SEARCH_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_SOFT, 0XFF10161D);
    public static final UiColor SEARCH_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFEAF2FF);
    public static final UiColor SEARCH_CLEAR_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.SURFACE_SUNKEN, 0XAA2A3340);
    public static final UiColor SEARCH_CLEAR_EMPTY = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.SURFACE_SUNKEN, 0X88A0B4C8);

    public static final UiColor WINDOW_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.CONTROL_IDLE, 0XF0182028);
    public static final UiColor WINDOW_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_STRONG, 0XFF7489A0);
    public static final UiColor WINDOW_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_SOFT, 0XFF0B1016);
    public static final UiColor WINDOW_TITLE = RtsMainlineTheme.WINDOW_TITLE;
    public static final UiColor WINDOW_TITLE_TEXT = RtsMainlineTheme.WINDOW_TITLE_TEXT;

    public static final UiColor MINI_BUTTON_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.CONTROL_IDLE, 0XAA2B3642);
    public static final UiColor BUTTON_IDLE = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.CONTROL_IDLE, 0XAA24303A);
    public static final UiColor BUTTON_HOVER = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.CONTROL_HOVER, 0XAA3E5368);
    public static final UiColor BUTTON_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_STRONG, 0XFF6E8799);
    public static final UiColor BUTTON_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_SOFT, 0XFF111821);
    public static final UiColor BUTTON_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_ON_ACCENT, 0XFFFFFFFF);

    public static final UiColor SHIFT_IMPORT_IDLE = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XCC2C873F);
    public static final UiColor SHIFT_IMPORT_HOVER = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.CONTROL_HOVER, 0XCC3AA156);
    public static final UiColor SHIFT_IMPORT_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_STRONG, 0XFF74E88C);
    public static final UiColor SHIFT_IMPORT_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_SOFT, 0XFF123A1D);
    public static final UiColor REFRESH_RUNNING = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XAA3F627E);

    public static final UiColor INFO_CLOSE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.CONTROL_IDLE, 0XCC2B3440);
    public static final UiColor INFO_CLOSE_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_STRONG, 0XFF7F92A8);
    public static final UiColor INFO_BODY_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFD8E6F5);

    public static final UiColor PAGE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.CONTROL_IDLE, 0XAA2A2A2A);
    public static final UiColor PAGE_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFDDDDDD);
    public static final UiColor STORAGE_SLOT = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.SLOT_IDLE, 0XAA131313);
    public static final UiColor STORAGE_COUNT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_ON_ACCENT, 0XFFF7E6A8);
    public static final UiColor RETURN_SLOT = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.SLOT_IDLE, 0XAA20262E);
    public static final UiColor RETURN_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_STRONG, 0XFF4E5A67);
    public static final UiColor RETURN_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_SOFT, 0XFF161A20);
    public static final UiColor RETURN_COUNT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_ON_ACCENT, 0XFFE8F6FF);
    public static final UiColor RETURN_EMPTY_TEXT = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XAACEE1FF);

    public static final UiColor QUICK_SLOT_FILLED = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.SLOT_IDLE, 0XAA253043);
    public static final UiColor QUICK_SLOT_EMPTY = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.SLOT_IDLE, 0XAA1A1A1A);
    public static final UiColor QUICK_SLOT_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.SLOT_IDLE, 0XFF67758A);
    public static final UiColor QUICK_SLOT_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.SLOT_IDLE, 0XFF0C0D10);
    public static final UiColor QUICK_SLOT_SELECTED = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.SLOT_SELECTED, 0X3340FF80);
    public static final UiColor QUICK_SLOT_EMPTY_TEXT = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.SLOT_IDLE, 0X88D0D8E4);

    public static final UiColor TOOLTIP_COUNT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_ON_ACCENT, 0XFFFFFFAA);
    public static final UiColor TOOLTIP_CRAFTABLE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFAEE8AE);
    public static final UiColor TOOLTIP_MISSING = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.ERROR, 0XFFFFB0B0);

    private ContainerOverlayStyle() {
    }

    public static UiColor searchBackground(boolean focused) {
        return focused ? SEARCH_FOCUSED : SEARCH_IDLE;
    }

    public static UiColor controlBackground(boolean active) {
        return active ? BUTTON_HOVER : BUTTON_IDLE;
    }

    public static UiColor shiftImportBackground(boolean enabled, boolean hovered) {
        if (!enabled) {
            return controlBackground(hovered);
        }
        return hovered ? SHIFT_IMPORT_HOVER : SHIFT_IMPORT_IDLE;
    }

    public static UiColor refreshBackground(boolean running, boolean hovered) {
        return running ? REFRESH_RUNNING : controlBackground(hovered);
    }
}
