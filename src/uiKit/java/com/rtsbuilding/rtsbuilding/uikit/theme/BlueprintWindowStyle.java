package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 蓝图放置/捕获浮窗内容区的共享语义色板。
 *
 * <p>本类只表达区块、状态、轴标签、文本框遮罩和主动作的视觉语义；不拥有蓝图状态、
 * Minecraft 字体、物品绘制或按钮副作用。生产窗口与离屏回放必须消费同一组 token，
 * 这样捕获阶段和放置阶段不会再分别维护颜色常量。</p>
 */
public final class BlueprintWindowStyle {
    public static final UiColor SECTION_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.CONTROL_IDLE, 0X33111821);
    public static final UiColor SECTION_BORDER_LIGHT = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.BORDER_STRONG, 0X55344555);
    public static final UiColor SECTION_BORDER_DARK = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.BORDER_SOFT, 0X550D1117);
    public static final UiColor SECTION_TITLE_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.TEXT_PRIMARY, 0XFFD8E3EE);

    public static final UiColor PRIMARY_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.TEXT_PRIMARY, 0XFFEAF2FF);
    public static final UiColor MUTED_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.TEXT_MUTED, 0XFF9FB3C8);
    public static final UiColor DISABLED_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.TEXT_MUTED, 0XFF4F5B68);
    public static final UiColor READY_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.SUCCESS, 0XFF8EEA9B);
    public static final UiColor WARNING_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.WARNING, 0XFFFFC06C);
    public static final UiColor PLACEMENT_WARNING_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.WARNING, 0XFFFFE66D);
    public static final UiColor INFO_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.TEXT_PRIMARY, 0XFFB7CDE2);

    public static final UiColor STATUS_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.CONTROL_IDLE, 0X66111821);
    public static final UiColor STATUS_BORDER_LIGHT = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.BORDER_STRONG, 0X44344555);
    public static final UiColor DISABLED_FIELD_OVERLAY = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.CONTROL_DISABLED, 0X55101620);

    public static final UiColor PRIMARY_ACTION_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.CONTROL_SELECTED, 0XCC244E35);
    public static final UiColor PRIMARY_ACTION_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.BORDER_STRONG, 0XFF7FCEA0);

    public static final UiColor FIELD_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.CONTROL_IDLE, 0XAA18212B);
    public static final UiColor FIELD_DISABLED_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.CONTROL_DISABLED, 0XAA101620);
    public static final UiColor FIELD_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.BORDER_STRONG, 0XFF596D84);
    public static final UiColor FIELD_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.BORDER_SOFT, 0XFF0D1117);

    private BlueprintWindowStyle() {
    }

    public static UiColor captureState(boolean complete) {
        return complete ? READY_TEXT : WARNING_TEXT;
    }

    public static UiColor axisLabel(boolean enabled) {
        return enabled ? MUTED_TEXT : DISABLED_TEXT;
    }

    public static UiColor fieldBackground(boolean enabled) {
        return enabled ? FIELD_BACKGROUND : FIELD_DISABLED_BACKGROUND;
    }
}
