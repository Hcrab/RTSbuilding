package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 快速建造窗口的共享语义色板。
 *
 * <p>本类只把模式、进度与状态语义映射为颜色，不决定文本、贴图、玩法动作或
 * Minecraft 绘制。生产和离屏 renderer 必须消费同一结果，避免“能点到的按钮”
 * 与截图中展示的按钮长期长成两套视觉。</p>
 */
public final class QuickBuildStyle {
    public static final UiColor MODE_IDLE_BACKGROUND = new UiColor(0xFF141C26);
    public static final UiColor MODE_HOVER_BACKGROUND = new UiColor(0xFF223040);
    public static final UiColor MODE_ACTIVE_BACKGROUND = new UiColor(0xFF29583E);
    public static final UiColor MODE_DISABLED_BACKGROUND = new UiColor(0xFF111720);
    public static final UiColor MODE_IDLE_BORDER = new UiColor(0xFF647B92);
    public static final UiColor MODE_HOVER_BORDER = new UiColor(0xFF7B91A6);
    public static final UiColor MODE_ACTIVE_BORDER = new UiColor(0xFF5FE36C);
    public static final UiColor MODE_DISABLED_BORDER = new UiColor(0xFF3A4652);
    public static final UiColor MODE_TEXT = new UiColor(0xFFD8E3EE);
    public static final UiColor MODE_ACTIVE_TEXT = new UiColor(0xFFD8FFE0);
    public static final UiColor MODE_DISABLED_TEXT = new UiColor(0xFF7B8794);
    public static final UiColor MODE_ANIMATION_OVERLAY =
            RtsMainlineTheme.SELECTION_ANIMATION_OVERLAY;
    public static final UiColor TRANSPARENT = RtsMainlineTheme.TRANSPARENT;

    public static final UiColor SECTION_TEXT = new UiColor(0xFFD8E3EE);
    public static final UiColor VALUE_TEXT = new UiColor(0xFFEAF4FF);
    public static final UiColor CHAIN_SELECTED_BORDER = new UiColor(0xFF78B28C);
    public static final UiColor CHAIN_SELECTED_BACKGROUND = new UiColor(0xFF163222);
    public static final UiColor DIVIDER = new UiColor(0xFF647B92);
    public static final UiColor PROGRESS_TRACK = new UiColor(0xFF0B1118);
    public static final UiColor PROGRESS_FILL = new UiColor(0xFFFF8EAD);
    public static final UiColor PROGRESS_IDLE_TICK = new UiColor(0xFF5F6F7F);
    public static final UiColor SUCCESS_TEXT = new UiColor(0xFFB8FFB8);
    public static final UiColor ERROR_TEXT = new UiColor(0xFFFFB8B8);
    public static final UiColor HINT_TEXT = new UiColor(0xFFD8E8FF);
    public static final UiColor DIMENSION_TEXT = new UiColor(0xFFC9D8E8);
    /** 正式模式圆点贴图不额外染色。 */
    public static final UiColor ICON_TINT = new UiColor(0xFFFFFFFF);

    private QuickBuildStyle() {
    }

    public static ModeVisual mode(boolean enabled, boolean active, boolean hovered) {
        if (!enabled) {
            return new ModeVisual(
                    MODE_DISABLED_BACKGROUND,
                    MODE_DISABLED_BORDER,
                    MODE_DISABLED_TEXT);
        }
        if (active) {
            return new ModeVisual(
                    MODE_ACTIVE_BACKGROUND,
                    MODE_ACTIVE_BORDER,
                    MODE_ACTIVE_TEXT);
        }
        return new ModeVisual(
                hovered ? MODE_HOVER_BACKGROUND : MODE_IDLE_BACKGROUND,
                hovered ? MODE_HOVER_BORDER : MODE_IDLE_BORDER,
                MODE_TEXT);
    }

    /** 单个模式按钮已解析的视觉；不携带业务模式枚举，方便其它版本复用。 */
    public static final class ModeVisual {
        public final UiColor background;
        public final UiColor border;
        public final UiColor text;

        private ModeVisual(UiColor background, UiColor border, UiColor text) {
            this.background = background;
            this.border = border;
            this.text = text;
        }
    }
}
