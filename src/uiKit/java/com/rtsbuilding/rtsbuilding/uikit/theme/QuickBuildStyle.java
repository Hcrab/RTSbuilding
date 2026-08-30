package com.rtsbuilding.rtsbuilding.uikit.theme;

import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;

/**
 * 快速建造窗口的共享语义色板。
 *
 * <p>本类只把模式、进度与状态语义映射为颜色，不决定文本、贴图、玩法动作或
 * Minecraft 绘制。生产和离屏 renderer 必须消费同一结果，避免“能点到的按钮”
 * 与截图中展示的按钮长期长成两套视觉。</p>
 */
public final class QuickBuildStyle {
    public static final UiColor MODE_IDLE_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.CONTROL_IDLE, 0XFF141C26);
    public static final UiColor MODE_HOVER_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.CONTROL_HOVER, 0XFF223040);
    public static final UiColor MODE_ACTIVE_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.CONTROL_SELECTED, 0XFF29583E);
    public static final UiColor MODE_PRESSED_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.CONTROL_PRESSED, 0XFF17344A);
    public static final UiColor MODE_DISABLED_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.CONTROL_DISABLED, 0XFF111720);
    public static final UiColor MODE_IDLE_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.BORDER_STRONG, 0XFF647B92);
    public static final UiColor MODE_HOVER_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.FOCUS_RING, 0XFF7B91A6);
    public static final UiColor MODE_ACTIVE_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.ACCENT_PRIMARY, 0XFF5FE36C);
    public static final UiColor MODE_PRESSED_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.ACCENT_SECONDARY, 0XFFFF8EAD);
    public static final UiColor MODE_DISABLED_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.BORDER_STRONG, 0XFF3A4652);
    public static final UiColor MODE_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_PRIMARY, 0XFFD8E3EE);
    public static final UiColor MODE_ACTIVE_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_PRIMARY, 0XFFD8FFE0);
    public static final UiColor MODE_PRESSED_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_ON_ACCENT, 0XFFFFFFFF);
    public static final UiColor MODE_DISABLED_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_MUTED, 0XFF7B8794);
    public static final UiColor MODE_ANIMATION_OVERLAY =
            RtsMainlineTheme.SELECTION_ANIMATION_OVERLAY;
    public static final UiColor TRANSPARENT = RtsMainlineTheme.TRANSPARENT;

    public static final UiColor SECTION_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_PRIMARY, 0XFFD8E3EE);
    public static final UiColor VALUE_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_PRIMARY, 0XFFEAF4FF);
    public static final UiColor CHAIN_SELECTED_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.ACCENT_PRIMARY, 0XFF78B28C);
    public static final UiColor CHAIN_SELECTED_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.CONTROL_SELECTED, 0XFF163222);
    public static final UiColor DIVIDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.BORDER_STRONG, 0XFF647B92);
    public static final UiColor PROGRESS_TRACK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.SURFACE_SUNKEN, 0XFF0B1118);
    public static final UiColor PROGRESS_FILL = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.ACCENT_SECONDARY, 0XFFFF8EAD);
    public static final UiColor PROGRESS_IDLE_TICK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_PRIMARY, 0XFF5F6F7F);
    public static final UiColor SUCCESS_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.SUCCESS, 0XFFB8FFB8);
    public static final UiColor ERROR_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.ERROR, 0XFFFFB8B8);
    public static final UiColor HINT_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_MUTED, 0XFFD8E8FF);
    public static final UiColor DIMENSION_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_MUTED, 0XFFC9D8E8);
    /** Legacy 模式圆点贴图不额外染色；Palette 轨道不会读取这项。 */
    public static final UiColor ICON_TINT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.ICON_PRIMARY, 0XFFFFFFFF);
    public static final UiColor INDICATOR_IDLE_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.CONTROL_IDLE, 0XFF26303B);
    public static final UiColor INDICATOR_HOVER_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.CONTROL_HOVER, 0XFF35475A);
    public static final UiColor INDICATOR_SELECTED_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.CONTROL_SELECTED, 0XFF2D6B47);
    public static final UiColor INDICATOR_DARK_EDGE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.SURFACE_SUNKEN, 0XFF10161D);
    public static final UiColor INDICATOR_IDLE_EDGE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.BORDER_STRONG, 0XFF647B92);
    public static final UiColor INDICATOR_SELECTED_EDGE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.ACCENT_PRIMARY, 0XFF7CCB93);
    public static final UiColor INDICATOR_IDLE_GLYPH = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.ICON_MUTED, 0XFF9FB0C2);
    public static final UiColor INDICATOR_SELECTED_GLYPH = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.SWITCH_THUMB, 0XFF72F07A);

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

    /**
     * 把通用控件的悬停、选中和禁用通道混合成快速建造模式按钮的最终颜色。
     * 业务模式会立即切换，只有背景、边框和文字颜色追随短动效。
     */
    public static ModeVisual animatedMode(UiControlAnimationState.Snapshot animation) {
        if (animation == null) {
            throw new IllegalArgumentException("animation");
        }
        UiColor background = UiColor.interpolate(
                MODE_IDLE_BACKGROUND, MODE_HOVER_BACKGROUND, animation.hover());
        UiColor border = UiColor.interpolate(
                MODE_IDLE_BORDER, MODE_HOVER_BORDER, animation.hover());
        UiColor text = MODE_TEXT;
        background = UiColor.interpolate(
                background, MODE_ACTIVE_BACKGROUND, animation.selection());
        border = UiColor.interpolate(
                border, MODE_ACTIVE_BORDER, animation.selection());
        text = UiColor.interpolate(text, MODE_ACTIVE_TEXT, animation.selection());
        background = UiColor.interpolate(
                background, MODE_PRESSED_BACKGROUND, animation.press());
        border = UiColor.interpolate(
                border, MODE_PRESSED_BORDER, animation.press());
        text = UiColor.interpolate(text, MODE_PRESSED_TEXT, animation.press());
        background = UiColor.interpolate(
                background, MODE_DISABLED_BACKGROUND, animation.disabled());
        border = UiColor.interpolate(
                border, MODE_DISABLED_BORDER, animation.disabled());
        text = UiColor.interpolate(text, MODE_DISABLED_TEXT, animation.disabled());
        return new ModeVisual(background, border, text);
    }

    /** Palette 轨道的小型工具状态标记；Legacy 仍由原三帧纹理完整接管。 */
    public static ControlIndicatorVisual controlIndicator(boolean selected, boolean hovered) {
        return new ControlIndicatorVisual(
                INDICATOR_DARK_EDGE,
                selected ? INDICATOR_SELECTED_EDGE : INDICATOR_IDLE_EDGE,
                selected ? INDICATOR_SELECTED_BACKGROUND
                        : hovered ? INDICATOR_HOVER_BACKGROUND : INDICATOR_IDLE_BACKGROUND,
                selected ? INDICATOR_SELECTED_GLYPH : INDICATOR_IDLE_GLYPH);
    }

    /**
     * 右栏状态块的连续视觉快照。状态值立即切换，颜色只追随统一短动画；
     * 因而快速点击不会改变行按钮几何，也不会出现瞬间跳色。
     */
    public static ControlIndicatorVisual animatedControlIndicator(
            UiControlAnimationState.Snapshot animation) {
        if (animation == null) {
            throw new IllegalArgumentException("animation");
        }
        UiColor lightEdge = UiColor.interpolate(
                INDICATOR_IDLE_EDGE, INDICATOR_SELECTED_EDGE, animation.selection());
        UiColor background = UiColor.interpolate(
                INDICATOR_IDLE_BACKGROUND, INDICATOR_HOVER_BACKGROUND, animation.hover());
        background = UiColor.interpolate(
                background, INDICATOR_SELECTED_BACKGROUND, animation.selection());
        UiColor glyph = UiColor.interpolate(
                INDICATOR_IDLE_GLYPH, INDICATOR_SELECTED_GLYPH, animation.selection());
        return new ControlIndicatorVisual(
                INDICATOR_DARK_EDGE, lightEdge, background, glyph);
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

    /** 已解析的小型状态标记颜色，不携带业务控件身份。 */
    public static final class ControlIndicatorVisual {
        public final UiColor darkEdge;
        public final UiColor lightEdge;
        public final UiColor background;
        public final UiColor glyph;

        private ControlIndicatorVisual(UiColor darkEdge, UiColor lightEdge,
                                       UiColor background, UiColor glyph) {
            this.darkEdge = darkEdge;
            this.lightEdge = lightEdge;
            this.background = background;
            this.glyph = glyph;
        }
    }
}
