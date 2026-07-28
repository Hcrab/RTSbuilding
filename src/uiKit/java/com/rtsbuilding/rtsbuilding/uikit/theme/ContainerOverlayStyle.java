package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 原版容器上方 RTS 储存/合成 Overlay 的共享语义色板。
 *
 * <p>该 Overlay 不属于 {@code BuilderScreen}，但仍是活动生产入口。这里仅接管颜色状态，
 * 不拥有容器生命周期、JEI 交互、物品绘制或网络动作。</p>
 */
public final class ContainerOverlayStyle {
    public static final UiColor INVENTORY_HOME_BACKGROUND = new UiColor(0xCC303030);
    public static final UiColor INVENTORY_HOME_TEXT = new UiColor(0xFFFFFFFF);
    public static final UiColor SEARCH_IDLE = new UiColor(0xAA202731);
    public static final UiColor SEARCH_FOCUSED = new UiColor(0xAA304153);
    public static final UiColor SEARCH_BORDER_LIGHT = new UiColor(0xFF61758A);
    public static final UiColor SEARCH_BORDER_DARK = new UiColor(0xFF10161D);
    public static final UiColor SEARCH_TEXT = new UiColor(0xFFEAF2FF);
    public static final UiColor SEARCH_CLEAR_BACKGROUND = new UiColor(0xAA2A3340);
    public static final UiColor SEARCH_CLEAR_EMPTY = new UiColor(0x88A0B4C8);

    public static final UiColor WINDOW_BACKGROUND = new UiColor(0xF0182028);
    public static final UiColor WINDOW_BORDER_LIGHT = new UiColor(0xFF7489A0);
    public static final UiColor WINDOW_BORDER_DARK = new UiColor(0xFF0B1016);
    public static final UiColor WINDOW_TITLE = RtsMainlineTheme.WINDOW_TITLE;
    public static final UiColor WINDOW_TITLE_TEXT = RtsMainlineTheme.WINDOW_TITLE_TEXT;

    public static final UiColor MINI_BUTTON_BACKGROUND = new UiColor(0xAA2B3642);
    public static final UiColor BUTTON_IDLE = new UiColor(0xAA24303A);
    public static final UiColor BUTTON_HOVER = new UiColor(0xAA3E5368);
    public static final UiColor BUTTON_BORDER_LIGHT = new UiColor(0xFF6E8799);
    public static final UiColor BUTTON_BORDER_DARK = new UiColor(0xFF111821);
    public static final UiColor BUTTON_TEXT = new UiColor(0xFFFFFFFF);

    public static final UiColor SHIFT_IMPORT_IDLE = new UiColor(0xCC2C873F);
    public static final UiColor SHIFT_IMPORT_HOVER = new UiColor(0xCC3AA156);
    public static final UiColor SHIFT_IMPORT_BORDER_LIGHT = new UiColor(0xFF74E88C);
    public static final UiColor SHIFT_IMPORT_BORDER_DARK = new UiColor(0xFF123A1D);
    public static final UiColor REFRESH_RUNNING = new UiColor(0xAA3F627E);

    public static final UiColor INFO_CLOSE_BACKGROUND = new UiColor(0xCC2B3440);
    public static final UiColor INFO_CLOSE_BORDER_LIGHT = new UiColor(0xFF7F92A8);
    public static final UiColor INFO_BODY_TEXT = new UiColor(0xFFD8E6F5);

    public static final UiColor PAGE_BACKGROUND = new UiColor(0xAA2A2A2A);
    public static final UiColor PAGE_TEXT = new UiColor(0xFFDDDDDD);
    public static final UiColor STORAGE_SLOT = new UiColor(0xAA131313);
    public static final UiColor STORAGE_COUNT = new UiColor(0xFFF7E6A8);
    public static final UiColor RETURN_SLOT = new UiColor(0xAA20262E);
    public static final UiColor RETURN_BORDER_LIGHT = new UiColor(0xFF4E5A67);
    public static final UiColor RETURN_BORDER_DARK = new UiColor(0xFF161A20);
    public static final UiColor RETURN_COUNT = new UiColor(0xFFE8F6FF);
    public static final UiColor RETURN_EMPTY_TEXT = new UiColor(0xAACEE1FF);

    public static final UiColor QUICK_SLOT_FILLED = new UiColor(0xAA253043);
    public static final UiColor QUICK_SLOT_EMPTY = new UiColor(0xAA1A1A1A);
    public static final UiColor QUICK_SLOT_BORDER_LIGHT = new UiColor(0xFF67758A);
    public static final UiColor QUICK_SLOT_BORDER_DARK = new UiColor(0xFF0C0D10);
    public static final UiColor QUICK_SLOT_SELECTED = new UiColor(0x3340FF80);
    public static final UiColor QUICK_SLOT_EMPTY_TEXT = new UiColor(0x88D0D8E4);

    public static final UiColor TOOLTIP_COUNT = new UiColor(0xFFFFFFAA);
    public static final UiColor TOOLTIP_CRAFTABLE = new UiColor(0xFFAEE8AE);
    public static final UiColor TOOLTIP_MISSING = new UiColor(0xFFFFB0B0);

    private ContainerOverlayStyle() {
    }

    public static UiColor searchBackground(boolean focused) {
        return focused ? SEARCH_FOCUSED : SEARCH_IDLE;
    }

    public static UiColor controlBackground(boolean active) {
        return active ? BUTTON_HOVER : BUTTON_IDLE;
    }

    public static UiColor shiftImportBackground(boolean enabled, boolean hovered) {
        if (!enabled) {
            return controlBackground(hovered);
        }
        return hovered ? SHIFT_IMPORT_HOVER : SHIFT_IMPORT_IDLE;
    }

    public static UiColor refreshBackground(boolean running, boolean hovered) {
        return running ? REFRESH_RUNNING : controlBackground(hovered);
    }
}
