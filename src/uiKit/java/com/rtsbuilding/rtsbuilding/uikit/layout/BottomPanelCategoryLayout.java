package com.rtsbuilding.rtsbuilding.uikit.layout;

/**
 * 底栏分类树的纯 Java 几何与半开命中规则。
 *
 * <p>本类同时服务生产绘制、点击、滚轮归属与离屏预览。它只负责面板、上下滚动键、
 * 可见行和展开键的位置，不读取分类文本，也不执行选择、展开或网络动作。行距保持
 * 11px，但真实可点击/绘制行高为 9px，因此每行下方 2px 间隙不会再误选相邻分类。</p>
 */
public final class BottomPanelCategoryLayout {
    public static final int WIDTH = RtsMainlineLayout.CATEGORY_W;
    public static final int HEADER_HEIGHT = 13;
    public static final int BOTTOM_INSET = 2;
    public static final int ROW_HEIGHT = 9;
    public static final int ROW_PITCH = 11;
    public static final int ROW_HORIZONTAL_INSET = 2;
    public static final int TEXT_LEFT_INSET = 6;
    public static final int DEPTH_INDENT = 10;
    public static final float TEXT_SCALE = 0.84F;
    public static final int TOGGLE_WIDTH = 9;
    public static final int TOGGLE_HEIGHT = 8;
    public static final int TITLE_TOP = 2;
    public static final int LABEL_TOGGLE_GAP = 3;

    public final Area panel;
    public final Area scrollUp;
    public final Area scrollDown;
    public final Area list;
    public final int totalRows;
    public final int visibleCapacity;
    public final int scroll;
    public final int maxScroll;

    private BottomPanelCategoryLayout(
            Area panel,
            Area scrollUp,
            Area scrollDown,
            Area list,
            int totalRows,
            int visibleCapacity,
            int scroll,
            int maxScroll) {
        this.panel = panel;
        this.scrollUp = scrollUp;
        this.scrollDown = scrollDown;
        this.list = list;
        this.totalRows = totalRows;
        this.visibleCapacity = visibleCapacity;
        this.scroll = scroll;
        this.maxScroll = maxScroll;
    }

    public static BottomPanelCategoryLayout resolve(
            int x, int y, int width, int height, int totalRows, int requestedScroll) {
        if (width < 24 || height < HEADER_HEIGHT + BOTTOM_INSET + ROW_PITCH) {
            throw new IllegalArgumentException("category panel is too small");
        }
        int safeRows = Math.max(0, totalRows);
        Area list = new Area(
                x + ROW_HORIZONTAL_INSET,
                y + HEADER_HEIGHT,
                width - ROW_HORIZONTAL_INSET * 2,
                height - HEADER_HEIGHT - BOTTOM_INSET);
        int visibleCapacity = Math.max(1, list.height / ROW_PITCH);
        int maxScroll = Math.max(0, safeRows - visibleCapacity);
        int scroll = clamp(requestedScroll, 0, maxScroll);
        return new BottomPanelCategoryLayout(
                new Area(x, y, width, height),
                new Area(x + width - 24, y + 1, 11, 10),
                new Area(x + width - 12, y + 1, 10, 10),
                list,
                safeRows,
                visibleCapacity,
                scroll,
                maxScroll);
    }

    public int visibleCount() {
        return Math.min(visibleCapacity, Math.max(0, totalRows - scroll));
    }

    public Area rowArea(int categoryIndex) {
        int visibleIndex = requireVisibleIndex(categoryIndex);
        return new Area(
                list.x,
                list.y + visibleIndex * ROW_PITCH,
                list.width,
                ROW_HEIGHT);
    }

    public Area toggleArea(int categoryIndex) {
        Area row = rowArea(categoryIndex);
        return new Area(
                panel.x + panel.width - 12,
                row.y + 1,
                TOGGLE_WIDTH,
                TOGGLE_HEIGHT);
    }

    /**
     * 返回 Core 分类列表中的绝对索引；标题、滚动键、行间空隙和边缘外侧均返回 -1。
     */
    public int categoryIndexAt(double mouseX, double mouseY) {
        if (!list.contains(mouseX, mouseY)) {
            return -1;
        }
        int visibleIndex = (int) ((mouseY - list.y) / ROW_PITCH);
        if (visibleIndex < 0 || visibleIndex >= visibleCount()) {
            return -1;
        }
        int categoryIndex = scroll + visibleIndex;
        return rowArea(categoryIndex).contains(mouseX, mouseY) ? categoryIndex : -1;
    }

    private int requireVisibleIndex(int categoryIndex) {
        int visibleIndex = categoryIndex - scroll;
        if (categoryIndex < 0 || categoryIndex >= totalRows
                || visibleIndex < 0 || visibleIndex >= visibleCount()) {
            throw new IllegalArgumentException(
                    "category index is not visible: " + categoryIndex);
        }
        return visibleIndex;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Area {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        private Area(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                    && mouseY >= y && mouseY < y + height;
        }
    }
}
