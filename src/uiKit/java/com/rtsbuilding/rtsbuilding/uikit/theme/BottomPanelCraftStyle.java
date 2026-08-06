package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底栏合成区在生产和离屏预览之间共享的语义色板。
 *
 * <p>这里只描述“待应用搜索”“显示不可用配方”“可合成/缺料”等业务视觉，不拥有布局、文本或动作。</p>
 */
public final class BottomPanelCraftStyle {
    public static final UiColor PANEL_BACKGROUND = new UiColor(0xAA141922);
    public static final UiColor PANEL_BORDER_LIGHT = new UiColor(0xFF637993);
    public static final UiColor PANEL_BORDER_DARK = new UiColor(0xFF0D1218);
    public static final UiColor TITLE = new UiColor(0xFFEAF2FF);

    public static final UiColor SEARCH_BACKGROUND = new UiColor(0xAA1E2731);
    public static final UiColor SEARCH_BORDER_LIGHT = new UiColor(0xFF5E738A);
    public static final UiColor SEARCH_BORDER_DARK = new UiColor(0xFF111921);
    public static final UiColor SEARCH_TEXT = new UiColor(0xFFEAF2FF);
    public static final UiColor SEARCH_UNEDITABLE_TEXT = new UiColor(0xFFAAB8C8);

    public static final UiColor BUTTON_IDLE = new UiColor(0xAA24303A);
    public static final UiColor APPLY_DIRTY = new UiColor(0xAA4C6E39);
    public static final UiColor TOGGLE_MAKE = new UiColor(0xAA2C5A41);
    public static final UiColor TOGGLE_ALL = new UiColor(0xAA5A3D2A);
    public static final UiColor BUTTON_BORDER_LIGHT = new UiColor(0xFF6E8799);
    public static final UiColor TOGGLE_BORDER_LIGHT = new UiColor(0xFF667D95);
    public static final UiColor BUTTON_BORDER_DARK = new UiColor(0xFF111821);
    public static final UiColor BUTTON_TEXT = new UiColor(0xFFFFFFFF);
    public static final UiColor BUTTON_TEXT_IDLE = new UiColor(0xFFB8C7D6);

    public static final UiColor SLOT_EMPTY = new UiColor(0xAA1A212B);
    public static final UiColor SLOT_AVAILABLE = new UiColor(0xAA214131);
    public static final UiColor SLOT_UNAVAILABLE = new UiColor(0xAA3F2323);
    public static final UiColor SLOT_BORDER_LIGHT = new UiColor(0xFF596D84);
    public static final UiColor SLOT_BORDER_DARK = new UiColor(0xFF11171E);
    public static final UiColor SLOT_COUNT = new UiColor(0xFFE8F4FF);
    public static final UiColor SLOT_UNAVAILABLE_OVERLAY = new UiColor(0x44220000);
    public static final UiColor SLOT_HOVER_OVERLAY = new UiColor(0x22FFFFFF);

    private BottomPanelCraftStyle() {
    }

    public static UiColor applyBackground(boolean dirty) {
        return dirty ? APPLY_DIRTY : BUTTON_IDLE;
    }

    public static UiColor toggleBackground(boolean showUnavailable) {
        return showUnavailable ? TOGGLE_ALL : TOGGLE_MAKE;
    }

    public static UiColor slotBackground(boolean present, boolean available) {
        if (!present) {
            return SLOT_EMPTY;
        }
        return available ? SLOT_AVAILABLE : SLOT_UNAVAILABLE;
    }
}
