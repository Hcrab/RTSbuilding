package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底栏物品网格在生产与离屏预览之间共享的语义主题。
 *
 * <p>每种网格只声明槽位背景、边框、选中态和数量文字。这里不区分真实物品栈，
 * 也不拥有 hover、点击或分页状态。</p>
 */
public final class BottomPanelGridStyle {
    public static final Visual STORAGE = new Visual(
            0xAA111111, 0xFF4A4A4A, 0xFF1B1B1B, 0x3326C56D, 0xFFF7E6A8);
    public static final Visual CREATIVE = new Visual(
            0xAA11151D, 0xFF596D84, 0xFF10151B, 0x3326C56D, 0xFFFFFFFF);
    public static final Visual RECENT = new Visual(
            0xAA161C24, 0xFF526171, 0xFF10151B, 0x00000000, 0xFFE8F4C0);
    public static final Visual FLUID = new Visual(
            0xAA2E1E12, 0xFFFFA553, 0xFF23140A, 0x3367D8FF, 0xFFFCCB8A);

    public static final UiColor RECENT_FLUID_COUNT = new UiColor(0xFFBEE6FF);
    public static final UiColor SELECTED_HOVER = new UiColor(0x3340FF80);
    public static final UiColor HOVER = new UiColor(0x22FFFFFF);
    public static final UiColor EMPTY_TITLE = new UiColor(0xFFE7C46A);
    public static final UiColor EMPTY_DETAIL = new UiColor(0xFFB8C7D6);

    private BottomPanelGridStyle() {
    }

    public static final class Visual {
        public final UiColor background;
        public final UiColor borderLight;
        public final UiColor borderDark;
        public final UiColor selectedOverlay;
        public final UiColor countText;

        private Visual(int background, int borderLight, int borderDark,
                       int selectedOverlay, int countText) {
            this.background = new UiColor(background);
            this.borderLight = new UiColor(borderLight);
            this.borderDark = new UiColor(borderDark);
            this.selectedOverlay = new UiColor(selectedOverlay);
            this.countText = new UiColor(countText);
        }
    }
}
