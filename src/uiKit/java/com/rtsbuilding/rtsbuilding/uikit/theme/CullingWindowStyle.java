package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 范围剔除窗口在生产绘制与离屏回放之间共享的语义色板。
 *
 * <p>本类只描述阶段提示与危险删除按钮的视觉状态，不持有选区、尺寸或删除业务。
 * 这样窗口状态仍归 Core/生产管理器所有，而正式客户端和截图不会各自维护一套近似颜色。</p>
 */
public final class CullingWindowStyle {
    public static final UiColor PRIMARY_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.TEXT_PRIMARY, 0XFFE7F2FF);
    public static final UiColor MUTED_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.TEXT_MUTED, 0XFF9FB2C4);
    public static final UiColor PHASE_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.TEXT_PRIMARY, 0XFF8EC8FF);
    public static final UiColor DELETE_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.DESTRUCTIVE, 0XFF742833);
    public static final UiColor DELETE_HOVER_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.DESTRUCTIVE, 0XFF9A3340);
    public static final UiColor DELETE_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.DESTRUCTIVE, 0XFFFFA2AE);
    public static final UiColor DELETE_HOVER_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.DESTRUCTIVE, 0XFFFFD1D7);
    public static final UiColor DELETE_DARK_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.STORAGE, UiThemeToken.DESTRUCTIVE, 0XFF0B1017);

    private CullingWindowStyle() {
    }

    public static DeleteVisual deleteButton(boolean hovered) {
        return new DeleteVisual(
                hovered ? DELETE_HOVER_BACKGROUND : DELETE_BACKGROUND,
                hovered ? DELETE_HOVER_BORDER : DELETE_BORDER,
                DELETE_DARK_BORDER);
    }

    /** 已解析的删除按钮三层框体颜色，不携带点击或悬停判定。 */
    public static final class DeleteVisual {
        public final UiColor background;
        public final UiColor border;
        public final UiColor darkBorder;

        private DeleteVisual(UiColor background, UiColor border, UiColor darkBorder) {
            this.background = background;
            this.border = border;
            this.darkBorder = darkBorder;
        }
    }
}
