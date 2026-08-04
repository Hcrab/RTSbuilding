package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底栏热栏、空手槽、固定槽与翻页槽共享的语义色板。
 *
 * <p>本类只表达槽位状态，不读取物品、鼠标或控制器状态。生产绘制和离屏预览都必须从这里
 * 取得颜色，避免两条路径逐渐形成不同的选中、悬停与空槽视觉。</p>
 */
public final class BottomPanelToolStyle {
    public static final UiColor HOTBAR_IDLE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XAA1B1E25);
    public static final UiColor HOTBAR_SELECTED_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_SELECTED, 0XCC3A6E57);
    public static final UiColor EMPTY_HAND_IDLE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XB06F5146);
    public static final UiColor EMPTY_HAND_SELECTED_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_SELECTED, 0XCC9B604B);
    public static final UiColor HOTBAR_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF5E6874);
    public static final UiColor EMPTY_HAND_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFFFFD0B0);
    public static final UiColor PIN_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF67758A);
    public static final UiColor BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_SOFT, 0XFF0C0D10);
    public static final UiColor PIN_EMPTY_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XAA1A1A1A);
    public static final UiColor PIN_FILLED_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XAA253043);
    public static final UiColor PIN_PAGER_OVERLAY = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XAA2C3A26);
    public static final UiColor SELECTED_OVERLAY = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_SELECTED, 0X3340FF80);
    public static final UiColor HOVER_OVERLAY = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_HOVER, 0X22FFFFFF);
    public static final UiColor EMPTY_HAND_MARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.ICON_PRIMARY, 0XFFFFC3A3);
    public static final UiColor PIN_PAGER_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFE9F7DA);
    public static final UiColor PIN_INDEX_TEXT = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_MUTED, 0X88D0D8E4);
    public static final UiColor PIN_COUNT_AVAILABLE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.SUCCESS, 0XFFF7E6A8);
    public static final UiColor PIN_COUNT_EMPTY = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_MUTED, 0XFFB4B9C3);

    private BottomPanelToolStyle() {
    }

    public static UiColor hotbarBackground(boolean emptyHand, boolean selected) {
        if (emptyHand) {
            return selected ? EMPTY_HAND_SELECTED_BACKGROUND : EMPTY_HAND_IDLE_BACKGROUND;
        }
        return selected ? HOTBAR_SELECTED_BACKGROUND : HOTBAR_IDLE_BACKGROUND;
    }

    public static UiColor hotbarBorderLight(boolean emptyHand) {
        return emptyHand ? EMPTY_HAND_BORDER_LIGHT : HOTBAR_BORDER_LIGHT;
    }

    public static UiColor pinBackground(boolean filled) {
        return filled ? PIN_FILLED_BACKGROUND : PIN_EMPTY_BACKGROUND;
    }

    public static UiColor pinCount(long amount) {
        return amount > 0L ? PIN_COUNT_AVAILABLE : PIN_COUNT_EMPTY;
    }
}
