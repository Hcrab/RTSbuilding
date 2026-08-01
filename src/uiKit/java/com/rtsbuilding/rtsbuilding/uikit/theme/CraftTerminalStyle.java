package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 合成终端概念图的语义色板。
 *
 * <p>颜色直接对应 {@code textures/gui/ui/terminal.png} 的主面板、槽位与描边层级。
 * 本类只定义视觉语义，不拥有布局、输入或 Minecraft 渲染生命周期。</p>
 */
public final class CraftTerminalStyle {
    public static final UiColor TEXT = new UiColor(0xFFEAF2FF);
    public static final UiColor MUTED_TEXT = new UiColor(0xFFB8C7D8);
    public static final UiColor UNEDITABLE_TEXT = new UiColor(0xFF8D9CAF);
    public static final UiColor COUNT_TEXT = new UiColor(0xFFFFFFFF);

    public static final UiColor PANEL = new UiColor(0xFF252E3B);
    public static final UiColor PANEL_ALT = new UiColor(0xFF202936);
    public static final UiColor HEADER = new UiColor(0xFF252E3B);
    public static final UiColor SEARCH = new UiColor(0xFF1A202A);
    public static final UiColor SLOT = new UiColor(0xFF324153);
    public static final UiColor SLOT_HOVER = new UiColor(0xFF3E5268);
    public static final UiColor BORDER_LIGHT = new UiColor(0xFF536679);
    public static final UiColor BORDER_MID = new UiColor(0xFF445468);
    public static final UiColor BORDER_DARK = new UiColor(0xFF111821);
    public static final UiColor BUTTON = new UiColor(0xFF2B3746);
    public static final UiColor BUTTON_HOVER = new UiColor(0xFF40556B);
    public static final UiColor BUTTON_ACTIVE = new UiColor(0xFF2E6A50);
    public static final UiColor SCROLL_TRACK = new UiColor(0xFF1A202A);
    public static final UiColor SCROLL_THUMB = new UiColor(0xFF536679);
    public static final UiColor ICON = new UiColor(0xFFDCE9F7);
    public static final UiColor ICON_MUTED = new UiColor(0xFF91A2B5);
    public static final UiColor TRANSPARENT = new UiColor(0x00000000);

    /* 兼容终端正式屏幕尚在使用的语义名称。 */
    public static final UiColor SEARCH_BACKGROUND = SEARCH;
    public static final UiColor SEARCH_BORDER_LIGHT = BORDER_LIGHT;
    public static final UiColor SEARCH_BORDER_DARK = BORDER_DARK;
    public static final UiColor SLOT_BACKGROUND = SLOT;
    public static final UiColor SLOT_HOVER_BACKGROUND = SLOT_HOVER;
    public static final UiColor SLOT_BORDER_LIGHT = BORDER_LIGHT;
    public static final UiColor SLOT_BORDER_DARK = BORDER_DARK;
    public static final UiColor IMPORT_EMPTY_BACKGROUND = PANEL_ALT;
    public static final UiColor IMPORT_READY_BACKGROUND = BUTTON_ACTIVE;
    public static final UiColor BUTTON_BORDER_LIGHT = RtsMainlineTheme.BUTTON_BORDER_LIGHT;
    public static final UiColor BUTTON_BORDER_DARK = RtsMainlineTheme.BUTTON_BORDER_DARK;
    public static final UiColor BUTTON_TEXT = RtsMainlineTheme.BUTTON_TEXT;

    public static UiColor importBackground(boolean carriedStackPresent) {
        return carriedStackPresent ? IMPORT_READY_BACKGROUND : IMPORT_EMPTY_BACKGROUND;
    }

    public static UiColor slotBackground(boolean hovered) {
        return hovered ? SLOT_HOVER_BACKGROUND : SLOT_BACKGROUND;
    }

    private CraftTerminalStyle() {
    }
}
