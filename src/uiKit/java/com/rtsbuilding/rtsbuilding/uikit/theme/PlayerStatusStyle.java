package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 玩家生命、饥饿、护甲与吸收条的共享语义色板。
 *
 * <p>颜色选择只依赖已钳制的比例；玩家实体读取、文本格式和显示开关仍由生产 renderer
 * 负责，离屏回放只复用相同视觉规则。</p>
 */
public final class PlayerStatusStyle {
    public static final UiColor BACKGROUND = new UiColor(0xAA1A1E24);
    public static final UiColor BORDER_LIGHT = new UiColor(0xFF3C4A5A);
    public static final UiColor BORDER_DARK = new UiColor(0xFF0A0D12);
    public static final UiColor TEXT = new UiColor(0xFFFFFFFF);
    public static final UiColor HEALTH_HIGH = new UiColor(0xFFD04040);
    public static final UiColor HEALTH_MEDIUM = new UiColor(0xFFD08030);
    public static final UiColor HEALTH_LOW = new UiColor(0xFFC03020);
    public static final UiColor FOOD_HIGH = new UiColor(0xFFC89030);
    public static final UiColor FOOD_MEDIUM = new UiColor(0xFFB07820);
    public static final UiColor FOOD_LOW = new UiColor(0xFFA06010);
    public static final UiColor ARMOR = new UiColor(0xFF6B8FA0);
    public static final UiColor ABSORPTION = new UiColor(0xFFE8C840);

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
