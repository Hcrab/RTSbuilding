package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底栏合成入口与远程 GUI 绑定槽的共享语义色板。
 *
 * <p>颜色按“合成入口悬停、空绑定、已有绑定、等待世界绑定”命名。生产 renderer 与离屏预览
 * 共同读取这些状态，不再各自复制 ARGB；本类不决定布局、文本或点击动作。</p>
 */
public final class BottomPanelCraftDockStyle {
    public static final UiColor CRAFT_IDLE = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XAA24303A);
    public static final UiColor CRAFT_HOVER = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_HOVER, 0XCC385465);
    public static final UiColor CRAFT_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF6E8799);
    public static final UiColor CRAFT_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_SOFT, 0XFF111821);

    public static final UiColor SLOT_EMPTY = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.SLOT_IDLE, 0XAA202731);
    public static final UiColor SLOT_BOUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.SLOT_SELECTED, 0XAA23384A);
    public static final UiColor SLOT_PENDING = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.WARNING, 0XCC2D6B47);
    public static final UiColor SLOT_EMPTY_HOVER = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.SLOT_HOVER, 0XBB29323D);
    public static final UiColor SLOT_BOUND_HOVER = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.SLOT_HOVER, 0XBB2C4760);
    public static final UiColor SLOT_PENDING_HOVER = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.SLOT_HOVER, 0XDD377F53);
    public static final UiColor SLOT_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF698097);
    public static final UiColor SLOT_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_SOFT, 0XFF0F151C);
    public static final UiColor BIND_CURSOR_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF78B28C);
    public static final UiColor TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFFFFFFF);

    private BottomPanelCraftDockStyle() {
    }

    public static UiColor craftBackground(boolean hovered) {
        return craftBackground(hovered ? 1.0D : 0.0D);
    }

    public static UiColor craftBackground(double hoverStrength) {
        return UiColor.interpolate(CRAFT_IDLE, CRAFT_HOVER, hoverStrength);
    }

    public static UiColor slotBackground(boolean pending, boolean bound, boolean hovered) {
        return slotBackground(pending, bound, hovered ? 1.0D : 0.0D);
    }

    public static UiColor slotBackground(
            boolean pending,
            boolean bound,
            double hoverStrength) {
        if (pending) {
            return UiColor.interpolate(
                    SLOT_PENDING, SLOT_PENDING_HOVER, hoverStrength);
        }
        if (bound) {
            return UiColor.interpolate(
                    SLOT_BOUND, SLOT_BOUND_HOVER, hoverStrength);
        }
        return UiColor.interpolate(
                SLOT_EMPTY, SLOT_EMPTY_HOVER, hoverStrength);
    }
}
