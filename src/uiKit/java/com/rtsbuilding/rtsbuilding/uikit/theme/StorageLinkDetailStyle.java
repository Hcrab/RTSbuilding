package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 顶栏“链接储存”详情动作的语义色板。
 *
 * <p>下拉动作仍由顶栏的短暂悬停状态驱动；本类只描述普通与悬停框体，
 * 不拥有可见性桥接区域或点击后的窗口打开逻辑。</p>
 */
public final class StorageLinkDetailStyle {
    public static final UiColor IDLE_BACKGROUND = new UiColor(0xF817212D);
    public static final UiColor HOVER_BACKGROUND = new UiColor(0xFF26394A);
    public static final UiColor IDLE_BORDER = RtsMainlineTheme.WINDOW_BORDER_LIGHT;
    public static final UiColor HOVER_BORDER = new UiColor(0xFFB7D2EC);
    public static final UiColor BORDER_DARK = RtsMainlineTheme.WINDOW_BORDER_DARK;
    public static final UiColor TEXT = new UiColor(0xFFF4FAFF);

    public static UiColor background(boolean hovered) {
        return hovered ? HOVER_BACKGROUND : IDLE_BACKGROUND;
    }

    public static UiColor border(boolean hovered) {
        return hovered ? HOVER_BORDER : IDLE_BORDER;
    }

    private StorageLinkDetailStyle() {
    }
}
