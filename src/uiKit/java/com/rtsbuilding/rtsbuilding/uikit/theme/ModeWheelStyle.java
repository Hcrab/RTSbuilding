package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 模式轮盘与放置状态轮盘共用的语义色板。
 *
 * <p>本类只拥有轮盘轨道、选项状态、标签与提示的视觉映射，不拥有轮盘几何、
 * 命中检测或 Minecraft 方块模型渲染。透明度缩放和悬停插值也集中在这里，
 * 避免两个生产轮盘各自复制一套 ARGB 位运算。</p>
 */
public final class ModeWheelStyle {
    public static final UiColor TRACK_BACKGROUND = new UiColor(0x241A222B);
    public static final UiColor TRACK_BORDER = new UiColor(0xA07E8C99);
    public static final UiColor PLACEMENT_TRACK = new UiColor(0x768996A3);
    public static final UiColor CENTER_DOT = new UiColor(0xFFD9E2EA);
    public static final UiColor CENTER_BRACKET = new UiColor(0xB8CFD8E1);

    public static final UiColor OPTION_BORDER_IDLE = new UiColor(0xFF82909D);
    public static final UiColor OPTION_BORDER_HOVER = new UiColor(0xFFFFD878);
    public static final UiColor OPTION_BORDER_CURRENT = new UiColor(0xFF8FD4A8);
    public static final UiColor OPTION_BACKGROUND_IDLE = new UiColor(0xC91A2026);
    public static final UiColor OPTION_BACKGROUND_HOVER = new UiColor(0xE6453820);
    public static final UiColor OPTION_BACKGROUND_CURRENT = new UiColor(0xD522382D);

    public static final UiColor LABEL_BACKGROUND = new UiColor(0xD0161B22);
    public static final UiColor LABEL_TEXT = new UiColor(0xFFF0F4F7);
    public static final UiColor HINT_TEXT = new UiColor(0xFFD6DFEA);

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
        return hovered ? OPTION_BORDER_HOVER : OPTION_BORDER_IDLE;
    }

    public static UiColor pageBackground(boolean hovered) {
        return hovered ? OPTION_BACKGROUND_HOVER : OPTION_BACKGROUND_IDLE;
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
