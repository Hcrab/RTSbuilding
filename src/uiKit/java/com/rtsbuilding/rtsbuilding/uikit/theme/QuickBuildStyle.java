package com.rtsbuilding.rtsbuilding.uikit.theme;

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
    public static final UiColor MODE_DISABLED_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.CONTROL_DISABLED, 0XFF111720);
    public static final UiColor MODE_IDLE_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.BORDER_STRONG, 0XFF647B92);
    public static final UiColor MODE_HOVER_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.FOCUS_RING, 0XFF7B91A6);
    public static final UiColor MODE_ACTIVE_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.ACCENT_PRIMARY, 0XFF5FE36C);
    public static final UiColor MODE_DISABLED_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.BORDER_STRONG, 0XFF3A4652);
    public static final UiColor MODE_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_PRIMARY, 0XFFD8E3EE);
    public static final UiColor MODE_ACTIVE_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.TEXT_PRIMARY, 0XFFD8FFE0);
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
    /** 正式模式圆点贴图不额外染色。 */
    public static final UiColor ICON_TINT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.QUICK_BUILD, UiThemeToken.ICON_PRIMARY, 0XFFFFFFFF);

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
