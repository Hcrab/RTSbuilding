package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底栏物品网格在生产与离屏预览之间共享的语义主题。
 *
 * <p>每种网格只声明槽位背景、边框、选中态和数量文字。这里不区分真实物品栈，
 * 也不拥有 hover、点击或分页状态。</p>
 */
public final class BottomPanelGridStyle {
    public static final Visual STORAGE = new Visual(
            0xAA111111, 0xFF4A4A4A, 0xFF1B1B1B, 0x3326C56D, 0xFFF7E6A8, true);
    public static final Visual CREATIVE = new Visual(
            0xAA11151D, 0xFF596D84, 0xFF10151B, 0x3326C56D, 0xFFFFFFFF, true);
    public static final Visual RECENT = new Visual(
            0xAA161C24, 0xFF526171, 0xFF10151B, 0x00000000, 0xFFE8F4C0, false);
    public static final Visual FLUID = new Visual(
            0xAA2E1E12, 0xFFFFA553, 0xFF23140A, 0x3367D8FF, 0xFFFCCB8A, true);

    public static final UiColor RECENT_FLUID_COUNT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFBEE6FF);
    public static final UiColor SELECTED_HOVER = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_HOVER, 0X3340FF80);
    public static final UiColor HOVER = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_HOVER, 0X22FFFFFF);
    public static final UiColor EMPTY_TITLE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFE7C46A);
    public static final UiColor EMPTY_DETAIL = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFB8C7D6);

    private BottomPanelGridStyle() {
    }

    public static final class Visual {
        public final UiColor background;
        public final UiColor borderLight;
        public final UiColor borderDark;
        public final UiColor selectedOverlay;
        public final UiColor countText;

        private Visual(int background, int borderLight, int borderDark,
                       int selectedOverlay, int countText,
                       boolean preserveSelectedAlpha) {
            this.background = UiColor.themeComponentWithLegacyAlpha(
                    UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR,
                    UiThemeToken.SLOT_IDLE, background);
            this.borderLight = UiColor.themeComponent(
                    UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR,
                    UiThemeToken.BORDER_STRONG, borderLight);
            this.borderDark = UiColor.themeComponent(
                    UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR,
                    UiThemeToken.BORDER_SOFT, borderDark);
            this.selectedOverlay = preserveSelectedAlpha
                    ? UiColor.themeComponentWithLegacyAlpha(
                            UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR,
                            UiThemeToken.SLOT_SELECTED, selectedOverlay)
                    : UiColor.themeComponent(
                            UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR,
                            UiThemeToken.SLOT_SELECTED, selectedOverlay);
            this.countText = UiColor.themeComponent(
                    UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR,
                    UiThemeToken.TEXT_PRIMARY, countText);
        }
    }
}
