package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * RTS 顶层提示、扫描进度与伤害闪烁共用的语义色板。
 *
 * <p>本类不依赖网络阶段常量，也不拥有弹窗位置和进度计算；生产适配层只需把
 * “错误、不可用、完成、运行中”等语义布尔值映射进来。这样弹窗框体、文字和
 * 进度条可以统一换肤，同时不会把游戏状态带进 Kit。</p>
 */
public final class OverlayStyle {
    public static final UiColor HOME_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.CONTROL_IDLE, 0XCC101820);
    public static final UiColor HOME_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_STRONG, 0XFF6E8799);
    public static final UiColor HOME_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_SOFT, 0XFF0D1218);
    public static final UiColor HOME_TITLE = RtsMainlineTheme.BUTTON_TEXT;
    public static final UiColor HOME_AREA = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFD8E6F5);
    public static final UiColor HOME_CONFIRM = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFBFD2E6);
    public static final UiColor HOME_GUIDE = RtsMainlineTheme.GUIDE_HINT;

    public static final UiColor POPUP_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.CONTROL_IDLE, 0XEE151A22);
    public static final UiColor POPUP_BORDER_LIGHT = RtsMainlineTheme.INPUT_BORDER_LIGHT;
    public static final UiColor POPUP_BORDER_DARK = RtsMainlineTheme.WINDOW_BORDER_DARK;
    public static final UiColor POPUP_TITLE = RtsMainlineTheme.WINDOW_TITLE_TEXT;
    public static final UiColor STATUS_NORMAL = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFFCFE3F7);
    public static final UiColor STATUS_UNAVAILABLE = RtsMainlineTheme.GUIDE_HINT;
    public static final UiColor STATUS_ERROR = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.ERROR, 0XFFFFB0B0);

    public static final UiColor PROGRESS_TRACK = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.SURFACE_SUNKEN, 0XAA202832);
    public static final UiColor PROGRESS_RUNNING = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.TEXT_PRIMARY, 0XFF88BEF4);
    public static final UiColor PROGRESS_COMPLETE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.SUCCESS, 0XFF78B28C);
    public static final UiColor PROGRESS_ERROR = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.ERROR, 0XFFE07070);
    public static final UiColor PROGRESS_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_STRONG, 0XFF405064);
    public static final UiColor PROGRESS_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY, UiThemeToken.BORDER_SOFT, 0XFF0A0D12);
    public static final UiColor CURSOR_LINE = UiColor.themeComponent(
            UiThemeCoverageCatalog.ComponentFamily.HUD_OVERLAY,
            UiThemeToken.TEXT_ON_ACCENT, 0XFFFFFFFF);
    public static final UiColor CURSOR_TRANSPARENT = RtsMainlineTheme.TRANSPARENT;

    public static UiColor questStatus(boolean error, boolean unavailable) {
        if (error) {
            return STATUS_ERROR;
        }
        return unavailable ? STATUS_UNAVAILABLE : STATUS_NORMAL;
    }

    public static UiColor questProgress(boolean error, boolean complete) {
        if (error) {
            return PROGRESS_ERROR;
        }
        return complete ? PROGRESS_COMPLETE : PROGRESS_RUNNING;
    }

    public static UiColor storageProgress(boolean running) {
        return running ? PROGRESS_RUNNING : PROGRESS_COMPLETE;
    }

    /** 保留历史上最高 50% 不透明度的红色伤害闪烁。 */
    public static UiColor damageFlash(double visibility) {
        if (Double.isNaN(visibility) || Double.isInfinite(visibility)) {
            throw new IllegalArgumentException("visibility must be finite");
        }
        double clamped = Math.max(0.0D, Math.min(1.0D, visibility));
        return UiColor.argb((int) (clamped * 128.0D), 255, 0, 0);
    }

    private OverlayStyle() {
    }
}
