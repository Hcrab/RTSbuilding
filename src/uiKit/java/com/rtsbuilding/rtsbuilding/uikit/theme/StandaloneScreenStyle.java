package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 独立 RTS 页面（不属于浮窗层）的共享语义色板。
 *
 * <p>该类只保存页面、顶/底栏和信息行颜色；Screen 生命周期、按钮和业务状态仍由
 * 各平台页面拥有。首页状态方法避免生产层重新拼接成功、警告和缺失三套颜色。</p>
 */
public final class StandaloneScreenStyle {
    public static final UiColor PAGE_BACKGROUND = new UiColor(0xFF101820);
    public static final UiColor BAR_BACKGROUND = new UiColor(0xFF151B23);
    public static final UiColor BAR_DIVIDER = new UiColor(0xFF273747);
    public static final UiColor TITLE_TEXT = RtsMainlineTheme.BUTTON_TEXT;

    public static final UiColor INFO_ROW_BACKGROUND = new UiColor(0xFF17202A);
    public static final UiColor INFO_ROW_DIVIDER = new UiColor(0xFF263545);
    public static final UiColor INFO_LABEL = new UiColor(0xFFAFC2D4);
    public static final UiColor INFO_VALUE = new UiColor(0xFFEAF2FF);
    public static final UiColor INFO_DIMENSION = new UiColor(0xFFD7E6F7);
    public static final UiColor INFO_EMPTY = new UiColor(0xFFB8C7D6);
    public static final UiColor INFO_RADIUS = new UiColor(0xFFD8E6F5);
    public static final UiColor SECTION_TEXT = new UiColor(0xFFF4F7FF);
    public static final UiColor SCROLLBAR_TRACK = new UiColor(0x66263545);
    public static final UiColor STATUS_ENABLED = new UiColor(0xFFB7E8C2);
    public static final UiColor STATUS_DISABLED = new UiColor(0xFFFFC4A8);
    public static final UiColor WARNING_TEXT = new UiColor(0xFFFFD980);
    public static final UiColor WARNING_BACKGROUND = new UiColor(0xFF1B1F24);
    public static final UiColor WARNING_DIVIDER = new UiColor(0xFF6E8799);
    public static final UiColor INPUT_BORDER_LIGHT = new UiColor(0xFFA0A0A0);
    public static final UiColor INPUT_BACKGROUND = new UiColor(0xFF101010);
    public static final UiColor INPUT_TEXT = new UiColor(0xFFE0E0E0);
    public static final UiColor INPUT_CURSOR = new UiColor(0xFFD0D0D0);

    public static UiColor progressionStatus(boolean enabled) {
        return enabled ? STATUS_ENABLED : STATUS_DISABLED;
    }

    public static UiColor homeStatus(boolean coolingDown) {
        return coolingDown ? WARNING_TEXT : INFO_VALUE;
    }

    private StandaloneScreenStyle() {
    }
}
