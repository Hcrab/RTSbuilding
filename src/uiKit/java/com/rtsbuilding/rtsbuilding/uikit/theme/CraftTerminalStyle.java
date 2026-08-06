package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 合成终端概念图的语义色板。
 *
 * <p>颜色直接对应 {@code textures/gui/ui/terminal.png} 的主面板、槽位与描边层级。
 * 本类只定义视觉语义，不拥有布局、输入或 Minecraft 渲染生命周期。</p>
 */
public final class CraftTerminalStyle {
    public static final UiColor TEXT = theme(UiThemeToken.TEXT_PRIMARY, 0xFFEAF2FF);
    public static final UiColor MUTED_TEXT = theme(UiThemeToken.TEXT_SECONDARY, 0xFFB8C7D8);
    public static final UiColor UNEDITABLE_TEXT = theme(UiThemeToken.TEXT_MUTED, 0xFF8D9CAF);
    public static final UiColor COUNT_TEXT = theme(UiThemeToken.TEXT_PRIMARY, 0xFFFFFFFF);

    public static final UiColor PANEL = theme(UiThemeToken.SURFACE, 0xFF252E3B);
    public static final UiColor PANEL_ALT = theme(UiThemeToken.SURFACE_RAISED, 0xFF202936);
    public static final UiColor HEADER = theme(UiThemeToken.SURFACE_RAISED, 0xFF252E3B);
    public static final UiColor SEARCH = theme(UiThemeToken.SURFACE_SUNKEN, 0xFF1A202A);
    public static final UiColor SLOT = theme(UiThemeToken.SLOT_IDLE, 0xFF324153);
    public static final UiColor SLOT_HOVER = theme(UiThemeToken.SLOT_HOVER, 0xFF3E5268);
    public static final UiColor BORDER_LIGHT = theme(UiThemeToken.BORDER_STRONG, 0xFF536679);
    public static final UiColor BORDER_MID = theme(UiThemeToken.BORDER_SOFT, 0xFF445468);
    public static final UiColor BORDER_DARK = theme(UiThemeToken.SURFACE_SUNKEN, 0xFF111821);
    public static final UiColor BUTTON = theme(UiThemeToken.CONTROL_IDLE, 0xFF2B3746);
    public static final UiColor BUTTON_HOVER = theme(UiThemeToken.CONTROL_HOVER, 0xFF40556B);
    public static final UiColor BUTTON_ACTIVE = theme(UiThemeToken.CONTROL_SELECTED, 0xFF2E6A50);
    public static final UiColor BUTTON_HOVER_OVERLAY = UiColor.themeComponentWithLegacyAlpha(
            UiThemeCoverageCatalog.ComponentFamily.CRAFT_TERMINAL,
            UiThemeToken.CONTROL_HOVER,
            0x663F5268);
    public static final UiColor SCROLL_TRACK = theme(UiThemeToken.SCROLLBAR_TRACK, 0xFF1A202A);
    public static final UiColor SCROLL_THUMB = theme(UiThemeToken.SCROLLBAR_THUMB, 0xFF536679);
    public static final UiColor ICON = theme(UiThemeToken.ICON_PRIMARY, 0xFFDCE9F7);
    public static final UiColor ICON_MUTED = theme(UiThemeToken.ICON_MUTED, 0xFF91A2B5);
    public static final UiColor TRANSPARENT = new UiColor(0x00000000);

    /* 兼容终端正式屏幕尚在使用的语义名称。 */
    public static final UiColor SEARCH_BACKGROUND = SEARCH;
    public static final UiColor SEARCH_BORDER_LIGHT = BORDER_LIGHT;
    public static final UiColor SEARCH_BORDER_DARK = BORDER_DARK;
    public static final UiColor SLOT_BACKGROUND = SLOT;
    public static final UiColor SLOT_HOVER_BACKGROUND = SLOT_HOVER;
    public static final UiColor SLOT_BORDER_LIGHT = BORDER_LIGHT;
    public static final UiColor SLOT_BORDER_DARK = BORDER_DARK;
    public static final UiColor IMPORT_EMPTY_BACKGROUND = PANEL_ALT;
    public static final UiColor IMPORT_READY_BACKGROUND = BUTTON_ACTIVE;
    public static final UiColor BUTTON_BORDER_LIGHT = RtsMainlineTheme.BUTTON_BORDER_LIGHT;
    public static final UiColor BUTTON_BORDER_DARK = RtsMainlineTheme.BUTTON_BORDER_DARK;
    public static final UiColor BUTTON_TEXT = RtsMainlineTheme.BUTTON_TEXT;
    // 1.12 独立容器仍以这些历史名称绘制；全部保留原始 Legacy 值并接入同一终端主题域。
    public static final UiColor VANILLA_TITLE_BACKGROUND = alphaTheme(UiThemeToken.CONTROL_IDLE, 0XB0212E3D);
    public static final UiColor VANILLA_TITLE_DIVIDER = theme(UiThemeToken.SURFACE_SUNKEN, 0XFF0F151D);
    public static final UiColor CRAFT_GRID_BACKGROUND = alphaTheme(UiThemeToken.SLOT_IDLE, 0X66405B78);
    public static final UiColor CRAFT_GRID_BORDER_LIGHT = theme(UiThemeToken.BORDER_STRONG, 0XFF5B7290);
    public static final UiColor CRAFT_GRID_BORDER_DARK = theme(UiThemeToken.SURFACE_SUNKEN, 0XFF10161E);
    public static final UiColor RESULT_BACKGROUND = alphaTheme(UiThemeToken.SLOT_IDLE, 0X663F5A76);
    public static final UiColor RESULT_BORDER_LIGHT = theme(UiThemeToken.BORDER_STRONG, 0XFF617A99);
    public static final UiColor RESULT_BORDER_DARK = theme(UiThemeToken.SURFACE_SUNKEN, 0XFF111821);
    public static final UiColor INVENTORY_BACKGROUND = alphaTheme(UiThemeToken.SURFACE, 0X441A222C);
    public static final UiColor INVENTORY_BORDER_LIGHT = theme(UiThemeToken.BORDER_STRONG, 0XFF4A6079);
    public static final UiColor INVENTORY_BORDER_DARK = theme(UiThemeToken.SURFACE_SUNKEN, 0XFF10151C);
    public static final UiColor LINK_BACKGROUND = alphaTheme(UiThemeToken.SURFACE, 0XCC141922);
    public static final UiColor LINK_BORDER_LIGHT = theme(UiThemeToken.BORDER_STRONG, 0XFF637993);
    public static final UiColor LINK_BORDER_DARK = theme(UiThemeToken.SURFACE_SUNKEN, 0XFF0D1218);
    public static final UiColor LINK_TITLE_BACKGROUND = alphaTheme(UiThemeToken.SURFACE_RAISED, 0XA0233345);
    public static final UiColor LINK_TITLE_DIVIDER = theme(UiThemeToken.SURFACE_SUNKEN, 0XFF0F171F);
    public static final UiColor CLEAR_BACKGROUND = alphaTheme(UiThemeToken.CONTROL_IDLE, 0XAA2A3441);
    public static final UiColor CLEAR_BORDER_LIGHT = theme(UiThemeToken.BORDER_STRONG, 0XFF647B95);
    public static final UiColor MINI_BUTTON_BACKGROUND = alphaTheme(UiThemeToken.CONTROL_IDLE, 0XAA2B3642);
    public static final UiColor INPUT_BORDER_LIGHT = theme(UiThemeToken.BORDER_STRONG, 0XFFA0A0A0);
    public static final UiColor INPUT_BACKGROUND = theme(UiThemeToken.SURFACE_SUNKEN, 0XFF101010);
    public static final UiColor INPUT_TEXT = theme(UiThemeToken.TEXT_PRIMARY, 0XFFE0E0E0);
    public static final UiColor INPUT_CURSOR = theme(UiThemeToken.TEXT_SECONDARY, 0XFFD0D0D0);

    public static UiColor importBackground(boolean carriedStackPresent) {
        return carriedStackPresent ? IMPORT_READY_BACKGROUND : IMPORT_EMPTY_BACKGROUND;
    }

    public static UiColor slotBackground(boolean hovered) {
        return hovered ? SLOT_HOVER_BACKGROUND : SLOT_BACKGROUND;
    }

    public static UiColor buttonHoverOverlay(double hoverStrength) {
        return UiColor.interpolate(
                TRANSPARENT, BUTTON_HOVER_OVERLAY, hoverStrength);
    }

    private CraftTerminalStyle() {
    }

    private static UiColor theme(UiThemeToken token, int legacyArgb) {
        return UiColor.themeComponent(
                UiThemeCoverageCatalog.ComponentFamily.CRAFT_TERMINAL, token, legacyArgb);
    }

    private static UiColor alphaTheme(UiThemeToken token, int legacyArgb) {
        return UiColor.themeComponentWithLegacyAlpha(
                UiThemeCoverageCatalog.ComponentFamily.CRAFT_TERMINAL, token, legacyArgb);
    }
}
