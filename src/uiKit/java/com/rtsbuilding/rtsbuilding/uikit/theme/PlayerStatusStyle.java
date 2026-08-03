package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 玩家生命、饥饿、护甲与吸收条的共享语义色板。
 *
 * <p>颜色选择只依赖已钳制的比例；玩家实体读取、文本格式和显示开关仍由生产 renderer
 * 负责，离屏回放只复用相同视觉规则。</p>
 */
public final class PlayerStatusStyle {
    public static final UiColor BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.CONTROL_IDLE, 0XAA1A1E24);
    public static final UiColor BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_STRONG, 0XFF3C4A5A);
    public static final UiColor BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_SOFT, 0XFF0A0D12);
    public static final UiColor TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFFFFFFF);
    public static final UiColor HEALTH_HIGH = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFD04040);
    public static final UiColor HEALTH_MEDIUM = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFD08030);
    public static final UiColor HEALTH_LOW = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFC03020);
    public static final UiColor FOOD_HIGH = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFC89030);
    public static final UiColor FOOD_MEDIUM = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFB07820);
    public static final UiColor FOOD_LOW = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFA06010);
    public static final UiColor ARMOR = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFF6B8FA0);
    public static final UiColor ABSORPTION = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFE8C840);

    private PlayerStatusStyle() {
    }

    public static UiColor health(double ratio) {
        double value = clamp(ratio);
        return value > 0.5D ? HEALTH_HIGH : value > 0.25D ? HEALTH_MEDIUM : HEALTH_LOW;
    }

    public static UiColor food(double ratio) {
        double value = clamp(ratio);
        return value > 0.5D ? FOOD_HIGH : value > 0.25D ? FOOD_MEDIUM : FOOD_LOW;
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("ratio must be finite");
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
