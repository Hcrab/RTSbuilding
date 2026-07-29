package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 蓝图放置/捕获浮窗内容区的共享语义色板。
 *
 * <p>本类只表达区块、状态、轴标签、文本框遮罩和主动作的视觉语义；不拥有蓝图状态、
 * Minecraft 字体、物品绘制或按钮副作用。生产窗口与离屏回放必须消费同一组 token，
 * 这样捕获阶段和放置阶段不会再分别维护颜色常量。</p>
 */
public final class BlueprintWindowStyle {
    public static final UiColor SECTION_BACKGROUND = new UiColor(0x33111821);
    public static final UiColor SECTION_BORDER_LIGHT = new UiColor(0x55344555);
    public static final UiColor SECTION_BORDER_DARK = new UiColor(0x550D1117);
    public static final UiColor SECTION_TITLE_TEXT = new UiColor(0xFFD8E3EE);

    public static final UiColor PRIMARY_TEXT = new UiColor(0xFFEAF2FF);
    public static final UiColor MUTED_TEXT = new UiColor(0xFF9FB3C8);
    public static final UiColor DISABLED_TEXT = new UiColor(0xFF4F5B68);
    public static final UiColor READY_TEXT = new UiColor(0xFF8EEA9B);
    public static final UiColor WARNING_TEXT = new UiColor(0xFFFFC06C);
    public static final UiColor PLACEMENT_WARNING_TEXT = new UiColor(0xFFFFE66D);
    public static final UiColor INFO_TEXT = new UiColor(0xFFB7CDE2);

    public static final UiColor STATUS_BACKGROUND = new UiColor(0x66111821);
    public static final UiColor STATUS_BORDER_LIGHT = new UiColor(0x44344555);
    public static final UiColor DISABLED_FIELD_OVERLAY = new UiColor(0x55101620);

    public static final UiColor PRIMARY_ACTION_BACKGROUND = new UiColor(0xCC244E35);
    public static final UiColor PRIMARY_ACTION_BORDER = new UiColor(0xFF7FCEA0);

    public static final UiColor FIELD_BACKGROUND = new UiColor(0xAA18212B);
    public static final UiColor FIELD_DISABLED_BACKGROUND = new UiColor(0xAA101620);
    public static final UiColor FIELD_BORDER_LIGHT = new UiColor(0xFF596D84);
    public static final UiColor FIELD_BORDER_DARK = new UiColor(0xFF0D1117);

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
