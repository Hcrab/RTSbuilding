package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * RTS 悬停说明追加行的语义色板。
 *
 * <p>Minecraft 原生 Tooltip 仍负责物品与主标题背景；本类只负责紧随其后的
 * 可执行、错误、数量和普通说明文字，不拥有 Tooltip 布局或内容。</p>
 */
public final class TooltipStyle {
    public static final UiColor ACTION_AVAILABLE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.SUCCESS, 0XFFAEE8AE);
    public static final UiColor ERROR = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.ERROR, 0XFFFFB0B0);
    public static final UiColor COUNT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFFFD8B8);
    public static final UiColor DETAIL = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFCFE3F7);

    public static UiColor craftChoice(boolean craftable) {
        return craftable ? ACTION_AVAILABLE : ERROR;
    }

    private TooltipStyle() {
    }
}
