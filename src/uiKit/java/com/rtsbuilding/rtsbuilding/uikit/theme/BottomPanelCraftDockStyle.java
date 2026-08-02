package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底栏合成入口与远程 GUI 绑定槽的共享语义色板。
 *
 * <p>颜色按“合成入口悬停、空绑定、已有绑定、等待世界绑定”命名。生产 renderer 与离屏预览
 * 共同读取这些状态，不再各自复制 ARGB；本类不决定布局、文本或点击动作。</p>
 */
public final class BottomPanelCraftDockStyle {
    public static final UiColor CRAFT_IDLE = new UiColor(0xAA24303A);
    public static final UiColor CRAFT_HOVER = new UiColor(0xCC385465);
    public static final UiColor CRAFT_BORDER_LIGHT = new UiColor(0xFF6E8799);
    public static final UiColor CRAFT_BORDER_DARK = new UiColor(0xFF111821);

    public static final UiColor SLOT_EMPTY = new UiColor(0xAA202731);
    public static final UiColor SLOT_BOUND = new UiColor(0xAA23384A);
    public static final UiColor SLOT_PENDING = new UiColor(0xCC2D6B47);
    public static final UiColor SLOT_EMPTY_HOVER = new UiColor(0xBB29323D);
    public static final UiColor SLOT_BOUND_HOVER = new UiColor(0xBB2C4760);
    public static final UiColor SLOT_PENDING_HOVER = new UiColor(0xDD377F53);
    public static final UiColor SLOT_BORDER_LIGHT = new UiColor(0xFF698097);
    public static final UiColor SLOT_BORDER_DARK = new UiColor(0xFF0F151C);
    public static final UiColor BIND_CURSOR_BORDER_LIGHT = new UiColor(0xFF78B28C);
    public static final UiColor TEXT = new UiColor(0xFFFFFFFF);

    private BottomPanelCraftDockStyle() {
    }

    public static UiColor craftBackground(boolean hovered) {
        return hovered ? CRAFT_HOVER : CRAFT_IDLE;
    }

    public static UiColor slotBackground(boolean pending, boolean bound, boolean hovered) {
        if (pending) {
            return hovered ? SLOT_PENDING_HOVER : SLOT_PENDING;
        }
        if (bound) {
            return hovered ? SLOT_BOUND_HOVER : SLOT_BOUND;
        }
        return hovered ? SLOT_EMPTY_HOVER : SLOT_EMPTY;
    }
}
