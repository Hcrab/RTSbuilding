package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 当前 1.21.1 主线固定栏与浮窗实际使用的语义颜色。
 *
 * <p>值来自生产绘制代码；预览器不得自行发明另一套调色盘。平台代码可以只在
 * 边界处把 {@link UiColor#toArgb()} 转成自己的颜色表示。</p>
 */
public final class RtsMainlineTheme {
    public static final UiColor WINDOW_BACKGROUND = new UiColor(0xFF161C24);
    public static final UiColor WINDOW_BORDER_LIGHT = new UiColor(0xFF6C839A);
    public static final UiColor WINDOW_BORDER_DARK = new UiColor(0xFF0D1117);
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

    private RtsMainlineTheme() {
    }
}
