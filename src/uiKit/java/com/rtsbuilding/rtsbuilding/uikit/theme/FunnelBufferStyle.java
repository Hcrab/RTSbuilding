package com.rtsbuilding.rtsbuilding.uikit.theme;

/** 漏斗缓存按钮、面板、行与物品槽的共享语义色板。 */
public final class FunnelBufferStyle {
    public static final UiColor TOGGLE_VISIBLE = new UiColor(0xAA2C4E3D);
    public static final UiColor TOGGLE_HIDDEN = new UiColor(0xAA2A2D36);
    public static final UiColor PANEL_BACKGROUND = new UiColor(0xAA17191F);
    public static final UiColor ROW_BACKGROUND = new UiColor(0x88303845);
    public static final UiColor SLOT_BACKGROUND = new UiColor(0xAA1E222A);
    public static final UiColor ROW_HOVER_OVERLAY = new UiColor(0x33FFFFFF);
    public static final UiColor PRIMARY_TEXT = new UiColor(0xFFFFFFFF);
    public static final UiColor TITLE_TEXT = new UiColor(0xFFF0F0F0);
    public static final UiColor COUNT_TEXT = new UiColor(0xFFFFDFAE);
    public static final UiColor EMPTY_TEXT = new UiColor(0x99B4BCC8);

    private FunnelBufferStyle() {
    }

    public static UiColor toggle(boolean panelVisible) {
        return panelVisible ? TOGGLE_VISIBLE : TOGGLE_HIDDEN;
    }
}
