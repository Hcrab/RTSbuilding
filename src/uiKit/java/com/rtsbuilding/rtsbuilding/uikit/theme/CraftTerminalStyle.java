package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 独立 RTS Craft Terminal 的语义色板。
 *
 * <p>该屏幕仍由 Minecraft 容器负责槽位、配方和网络生命周期；本类只收拢原版
 * crafting table 背景上的 RTS chrome，不拥有布局或输入。按钮的公共边框与文字
 * 复用主线主题，容器各区的半透明层保留现有生产值。</p>
 */
public final class CraftTerminalStyle {
    public static final UiColor TEXT = new UiColor(0xFFEAF2FF);
    public static final UiColor MUTED_TEXT = new UiColor(0xFFD7E3F2);
    public static final UiColor UNEDITABLE_TEXT = new UiColor(0xFFAAB8C8);
    public static final UiColor COUNT_TEXT = new UiColor(0xFFE8F4FF);

    public static final UiColor VANILLA_TITLE_BACKGROUND = new UiColor(0xB0212E3D);
    public static final UiColor VANILLA_TITLE_DIVIDER = new UiColor(0xFF0F151D);
    public static final UiColor CRAFT_GRID_BACKGROUND = new UiColor(0x66405B78);
    public static final UiColor CRAFT_GRID_BORDER_LIGHT = new UiColor(0xFF5B7290);
    public static final UiColor CRAFT_GRID_BORDER_DARK = new UiColor(0xFF10161E);
    public static final UiColor RESULT_BACKGROUND = new UiColor(0x663F5A76);
    public static final UiColor RESULT_BORDER_LIGHT = new UiColor(0xFF617A99);
    public static final UiColor RESULT_BORDER_DARK = new UiColor(0xFF111821);
    public static final UiColor INVENTORY_BACKGROUND = new UiColor(0x441A222C);
    public static final UiColor INVENTORY_BORDER_LIGHT = new UiColor(0xFF4A6079);
    public static final UiColor INVENTORY_BORDER_DARK = new UiColor(0xFF10151C);

    public static final UiColor LINK_BACKGROUND = new UiColor(0xCC141922);
    public static final UiColor LINK_BORDER_LIGHT = new UiColor(0xFF637993);
    public static final UiColor LINK_BORDER_DARK = new UiColor(0xFF0D1218);
    public static final UiColor LINK_TITLE_BACKGROUND = new UiColor(0xA0233345);
    public static final UiColor LINK_TITLE_DIVIDER = new UiColor(0xFF0F171F);
    public static final UiColor STORAGE_ROW_EVEN = new UiColor(0xD0141922);
    public static final UiColor STORAGE_ROW_ODD = new UiColor(0xD01A202D);
    public static final UiColor CRAFT_BACKGROUND = new UiColor(0xE010151B);
    public static final UiColor INVENTORY_PANEL_BACKGROUND = new UiColor(0xD0141922);
    public static final UiColor BUTTON_HOVER_BACKGROUND = new UiColor(0xCC3F5268);
    public static final UiColor BUTTON_ACTIVE_BACKGROUND = new UiColor(0xCC2E6A50);
    public static final UiColor SCROLL_TRACK = new UiColor(0xAA111820);
    public static final UiColor SCROLL_THUMB = new UiColor(0xFF5E738A);
    public static final UiColor IMPORT_EMPTY_BACKGROUND = new UiColor(0x8821262D);
    public static final UiColor IMPORT_READY_BACKGROUND = new UiColor(0xAA2E516A);
    public static final UiColor SEARCH_BACKGROUND = new UiColor(0xAA1E2731);
    public static final UiColor SEARCH_BORDER_LIGHT = new UiColor(0xFF5E738A);
    public static final UiColor SEARCH_BORDER_DARK = new UiColor(0xFF111921);
    public static final UiColor CLEAR_BACKGROUND = new UiColor(0xAA2A3441);
    public static final UiColor CLEAR_BORDER_LIGHT = new UiColor(0xFF647B95);
    public static final UiColor SLOT_BACKGROUND = new UiColor(0xAA1A212B);
    public static final UiColor SLOT_HOVER_BACKGROUND = new UiColor(0xAA304053);
    public static final UiColor SLOT_BORDER_LIGHT = new UiColor(0xFF596D84);
    public static final UiColor SLOT_BORDER_DARK = new UiColor(0xFF11171E);
    public static final UiColor MINI_BUTTON_BACKGROUND = new UiColor(0xAA2B3642);

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
