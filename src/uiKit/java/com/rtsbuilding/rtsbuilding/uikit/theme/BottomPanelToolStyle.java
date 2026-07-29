package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底栏热栏、空手槽、固定槽与翻页槽共享的语义色板。
 *
 * <p>本类只表达槽位状态，不读取物品、鼠标或控制器状态。生产绘制和离屏预览都必须从这里
 * 取得颜色，避免两条路径逐渐形成不同的选中、悬停与空槽视觉。</p>
 */
public final class BottomPanelToolStyle {
    public static final UiColor HOTBAR_IDLE_BACKGROUND = new UiColor(0xAA1B1E25);
    public static final UiColor HOTBAR_SELECTED_BACKGROUND = new UiColor(0xCC3A6E57);
    public static final UiColor EMPTY_HAND_IDLE_BACKGROUND = new UiColor(0xB06F5146);
    public static final UiColor EMPTY_HAND_SELECTED_BACKGROUND = new UiColor(0xCC9B604B);
    public static final UiColor HOTBAR_BORDER_LIGHT = new UiColor(0xFF5E6874);
    public static final UiColor EMPTY_HAND_BORDER_LIGHT = new UiColor(0xFFFFD0B0);
    public static final UiColor PIN_BORDER_LIGHT = new UiColor(0xFF67758A);
    public static final UiColor BORDER_DARK = new UiColor(0xFF0C0D10);
    public static final UiColor PIN_EMPTY_BACKGROUND = new UiColor(0xAA1A1A1A);
    public static final UiColor PIN_FILLED_BACKGROUND = new UiColor(0xAA253043);
    public static final UiColor PIN_PAGER_OVERLAY = new UiColor(0xAA2C3A26);
    public static final UiColor SELECTED_OVERLAY = new UiColor(0x3340FF80);
    public static final UiColor HOVER_OVERLAY = new UiColor(0x22FFFFFF);
    public static final UiColor EMPTY_HAND_MARK = new UiColor(0xFFFFC3A3);
    public static final UiColor PIN_PAGER_TEXT = new UiColor(0xFFE9F7DA);
    public static final UiColor PIN_INDEX_TEXT = new UiColor(0x88D0D8E4);
    public static final UiColor PIN_COUNT_AVAILABLE = new UiColor(0xFFF7E6A8);
    public static final UiColor PIN_COUNT_EMPTY = new UiColor(0xFFB4B9C3);

    private BottomPanelToolStyle() {
    }

    public static UiColor hotbarBackground(boolean emptyHand, boolean selected) {
        if (emptyHand) {
            return selected ? EMPTY_HAND_SELECTED_BACKGROUND : EMPTY_HAND_IDLE_BACKGROUND;
        }
        return selected ? HOTBAR_SELECTED_BACKGROUND : HOTBAR_IDLE_BACKGROUND;
    }

    public static UiColor hotbarBorderLight(boolean emptyHand) {
        return emptyHand ? EMPTY_HAND_BORDER_LIGHT : HOTBAR_BORDER_LIGHT;
    }

    public static UiColor pinBackground(boolean filled) {
        return filled ? PIN_FILLED_BACKGROUND : PIN_EMPTY_BACKGROUND;
    }

    public static UiColor pinCount(long amount) {
        return amount > 0L ? PIN_COUNT_AVAILABLE : PIN_COUNT_EMPTY;
    }
}
