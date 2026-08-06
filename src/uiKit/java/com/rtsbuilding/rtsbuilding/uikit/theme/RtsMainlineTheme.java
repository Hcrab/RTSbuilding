package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 当前 1.21.1 主线固定栏与浮窗实际使用的语义颜色。
 *
 * <p>值来自生产绘制代码；预览器不得自行发明另一套调色盘。平台代码可以只在
 * 边界处把 {@link UiColor#toArgb()} 转成自己的颜色表示。</p>
 */
public final class RtsMainlineTheme {
    /** 透明色只用于插值和“无覆盖层”状态，不应替代明确的控件背景。 */
    public static final UiColor TRANSPARENT = new UiColor(0x00000000);

    public static final UiColor TOP_BAR_BACKGROUND = new UiColor(0xC0101116);
    public static final UiColor WINDOW_BACKGROUND = new UiColor(0xFF161C24);
    public static final UiColor WINDOW_BORDER_LIGHT = new UiColor(0xFF6C839A);
    public static final UiColor WINDOW_BORDER_DARK = new UiColor(0xFF0D1117);
    public static final UiColor WINDOW_BORDER_HOVER_LIGHT = new UiColor(0xFFAAC8E8);
    public static final UiColor WINDOW_BORDER_HOVER_DARK = new UiColor(0xFF2A3A4A);
    public static final UiColor WINDOW_TITLE = new UiColor(0xCC233345);
    public static final UiColor WINDOW_TITLE_TEXT = new UiColor(0xFFF2F7FF);

    public static final UiColor BOTTOM_BACKGROUND = new UiColor(0xD014151A);
    public static final UiColor BOTTOM_HEADER = new UiColor(0xCC1C242F);
    public static final UiColor BOTTOM_BORDER_LIGHT = new UiColor(0xFF64788E);
    public static final UiColor BOTTOM_BORDER_DARK = new UiColor(0xFF0D1015);
    public static final UiColor TAB_ACTIVE = new UiColor(0xCC355B4C);
    public static final UiColor TAB_ACTIVE_BORDER = new UiColor(0xFF7CCB93);
    public static final UiColor TAB_IDLE = new UiColor(0x8826303B);
    public static final UiColor TAB_IDLE_BORDER = new UiColor(0xFF536679);
    public static final UiColor PRIMARY_TEXT = new UiColor(0xFFF2F6FB);
    public static final UiColor SECONDARY_TEXT = new UiColor(0xFFD8E2EE);
    public static final UiColor MUTED_TEXT = new UiColor(0xFF9FB0C2);

    public static final UiColor CONTROL_IDLE_BACKGROUND = new UiColor(0xAA1F2329);
    public static final UiColor CONTROL_IDLE_BORDER_LIGHT = new UiColor(0xFF5B6673);
    public static final UiColor CONTROL_IDLE_BORDER_DARK = new UiColor(0xFF0D0E10);
    public static final UiColor CONTROL_IDLE_ICON = new UiColor(0xFFBDC9D6);
    public static final UiColor CONTROL_HOVER_BACKGROUND = new UiColor(0xFF1D2530);
    public static final UiColor CONTROL_HOVER_BORDER_LIGHT = new UiColor(0xFF7A90AA);
    public static final UiColor CONTROL_HOVER_ICON = new UiColor(0xFFD9E3EF);
    public static final UiColor CONTROL_PRESSED_BACKGROUND = new UiColor(0xFF1F5037);
    public static final UiColor CONTROL_PRESSED_BORDER_LIGHT = new UiColor(0xFF6AA784);
    public static final UiColor CONTROL_SELECTED_BACKGROUND = new UiColor(0xFF2D6B47);
    public static final UiColor CONTROL_SELECTED_BORDER_LIGHT = new UiColor(0xFF9AD2AE);
    public static final UiColor CONTROL_SELECTED_ICON = new UiColor(0xFFF4FBF5);
    public static final UiColor CONTROL_DISABLED_OVERLAY = new UiColor(0x880B0E12);
    public static final UiColor CONTROL_PENDING = new UiColor(0xFFFFC96B);
    public static final UiColor CONTROL_ERROR = new UiColor(0xFFE36B6B);

    public static final UiColor BUTTON_BACKGROUND = new UiColor(0xAA2A3340);
    public static final UiColor BUTTON_PRIMARY_BACKGROUND = new UiColor(0xAA345A38);
    public static final UiColor BUTTON_DESTRUCTIVE_BACKGROUND = new UiColor(0xAA473030);
    public static final UiColor BUTTON_BORDER_LIGHT = new UiColor(0xFF667D95);
    public static final UiColor BUTTON_BORDER_DARK = new UiColor(0xFF111821);
    public static final UiColor BUTTON_TEXT = new UiColor(0xFFFFFFFF);

    public static final UiColor INPUT_BACKGROUND = new UiColor(0xFF202833);
    public static final UiColor INPUT_BORDER_LIGHT = new UiColor(0xFF61758A);
    public static final UiColor INPUT_BORDER_DARK = new UiColor(0xFF11161C);

    public static final UiColor STATUS_LINKED = new UiColor(0xFFB8FFB8);
    public static final UiColor STATUS_UNLINKED = new UiColor(0xFFFFD8AE);
    public static final UiColor GUIDE_HINT = new UiColor(0xFFE7C46A);
    public static final UiColor TOOLTIP_BACKGROUND = new UiColor(0xF010141A);
    public static final UiColor TOOLTIP_BORDER = new UiColor(0xFF6C839A);
    public static final UiColor SLOT_COUNT_BACKGROUND = new UiColor(0xB0000000);

    /** 选中插值只叠加很薄的绿色，避免改变现有纹理的识别度。 */
    public static final UiColor SELECTION_ANIMATION_OVERLAY = new UiColor(0x4A7CCB93);

    private RtsMainlineTheme() {
    }
}
