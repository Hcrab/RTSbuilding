package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 顶栏“链接储存”详情动作的语义色板。
 *
 * <p>下拉动作仍由顶栏的短暂悬停状态驱动；本类只描述普通与悬停框体，
 * 不拥有可见性桥接区域或点击后的窗口打开逻辑。</p>
 */
public final class StorageLinkDetailStyle {
    public static final UiColor IDLE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.CONTROL_IDLE, 0XF817212D);
    public static final UiColor HOVER_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.CONTROL_HOVER, 0XFF26394A);
    public static final UiColor IDLE_BORDER = RtsMainlineTheme.WINDOW_BORDER_LIGHT;
    public static final UiColor HOVER_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.FOCUS_RING, 0XFFB7D2EC);
    public static final UiColor BORDER_DARK = RtsMainlineTheme.WINDOW_BORDER_DARK;
    public static final UiColor TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.TEXT_PRIMARY, 0XFFF4FAFF);

    public static UiColor background(boolean hovered) {
        return background(hovered ? 1.0D : 0.0D);
    }

    public static UiColor border(boolean hovered) {
        return border(hovered ? 1.0D : 0.0D);
    }

    public static UiColor background(double hoverStrength) {
        return UiColor.interpolate(IDLE_BACKGROUND, HOVER_BACKGROUND, hoverStrength);
    }

    public static UiColor border(double hoverStrength) {
        return UiColor.interpolate(IDLE_BORDER, HOVER_BORDER, hoverStrength);
    }

    private StorageLinkDetailStyle() {
    }
}
