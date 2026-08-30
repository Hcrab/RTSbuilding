package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 模式轮盘与放置状态轮盘共用的语义色板。
 *
 * <p>本类只拥有轮盘轨道、选项状态、标签与提示的视觉映射，不拥有轮盘几何、
 * 命中检测或 Minecraft 方块模型渲染。透明度缩放和悬停插值也集中在这里，
 * 避免两个生产轮盘各自复制一套 ARGB 位运算。</p>
 */
public final class ModeWheelStyle {
    public static final UiColor TRACK_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.SURFACE_SUNKEN, 0X241A222B);
    public static final UiColor TRACK_BORDER = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.BORDER_STRONG, 0XA07E8C99);
    public static final UiColor PLACEMENT_TRACK = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.SURFACE_SUNKEN, 0X768996A3);
    public static final UiColor CENTER_DOT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_PRIMARY, 0XFFD9E2EA);
    public static final UiColor CENTER_BRACKET = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_PRIMARY, 0XB8CFD8E1);

    public static final UiColor OPTION_BORDER_IDLE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.BORDER_STRONG, 0XFF82909D);
    public static final UiColor OPTION_BORDER_HOVER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.FOCUS_RING, 0XFFFFD878);
    public static final UiColor OPTION_BORDER_CURRENT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.BORDER_STRONG, 0XFF8FD4A8);
    public static final UiColor OPTION_BACKGROUND_IDLE = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.CONTROL_IDLE, 0XC91A2026);
    public static final UiColor OPTION_BACKGROUND_HOVER = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.CONTROL_HOVER, 0XE6453820);
    public static final UiColor OPTION_BACKGROUND_CURRENT = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.CONTROL_SELECTED, 0XD522382D);

    public static final UiColor LABEL_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.SURFACE, 0XD0161B22);
    public static final UiColor LABEL_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_PRIMARY, 0XFFF0F4F7);
    public static final UiColor HINT_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_MUTED, 0XFFD6DFEA);

    public static UiColor optionBorder(boolean current, double hoverProgress) {
        if (hoverProgress > 0.01D) {
            return UiColor.interpolate(
                    OPTION_BORDER_IDLE, OPTION_BORDER_HOVER, hoverProgress);
        }
        return current ? OPTION_BORDER_CURRENT : OPTION_BORDER_IDLE;
    }

    public static UiColor optionBackground(boolean current, double hoverProgress) {
        if (hoverProgress > 0.01D) {
            return UiColor.interpolate(
                    OPTION_BACKGROUND_IDLE, OPTION_BACKGROUND_HOVER, hoverProgress);
        }
        return current ? OPTION_BACKGROUND_CURRENT : OPTION_BACKGROUND_IDLE;
    }

    public static UiColor pageBorder(boolean hovered) {
        return pageBorder(hovered ? 1.0D : 0.0D);
    }

    public static UiColor pageBorder(double hoverProgress) {
        return UiColor.interpolate(
                OPTION_BORDER_IDLE, OPTION_BORDER_HOVER, hoverProgress);
    }

    public static UiColor pageBackground(boolean hovered) {
        return pageBackground(hovered ? 1.0D : 0.0D);
    }

    public static UiColor pageBackground(double hoverProgress) {
        return UiColor.interpolate(
                OPTION_BACKGROUND_IDLE, OPTION_BACKGROUND_HOVER, hoverProgress);
    }

    /** 按原颜色的透明通道缩放，并把异常进度钳制到可绘制范围。 */
    public static UiColor multiplyAlpha(UiColor color, double multiplier) {
        if (color == null) {
            throw new IllegalArgumentException("color must not be null");
        }
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier)) {
            throw new IllegalArgumentException("multiplier must be finite");
        }
        double clamped = Math.max(0.0D, Math.min(1.0D, multiplier));
        return color.withAlpha((int) Math.round(color.alpha() * clamped));
    }

    private ModeWheelStyle() {
    }
}
