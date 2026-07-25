package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 开发者场景任务页的诊断色板。
 *
 * <p>该页面只用于人工验证场景步骤；样式独立命名可避免诊断界面把颜色常量
 * 留回生产屏幕包，同时不把开发者状态误装成普通玩家状态。</p>
 */
public final class DeveloperScreenStyle {
    public static final UiColor BACKGROUND = new UiColor(0xF0101820);
    public static final UiColor TITLE = RtsMainlineTheme.BUTTON_TEXT;
    public static final UiColor ACTIVE_STATUS = new UiColor(0xFFFFD27F);

    private DeveloperScreenStyle() {
    }
}
