package com.rtsbuilding.rtsbuilding.uikit.layout;

/**
 * 底栏搜索与分页工具条的纯 Java 几何。
 *
 * <p>搜索 EditBox 仍由 Minecraft 生产层拥有，但它的外框、清除键、上一页和下一页按钮
 * 必须与生产点击及离屏预览消费同一组区域。所有区域均使用半开边界，右/下边缘不会同时
 * 命中相邻控件。</p>
 */
public final class BottomPanelBrowseLayout {
    public static final int SEARCH_HEIGHT = 14;
    public static final int SEARCH_MIN_WIDTH = 56;
    public static final int CLEAR_SIZE = 12;
    public static final int CLEAR_GAP = 2;
    public static final int CLEAR_Y_OFFSET = 1;
    public static final int PAGE_BUTTON_WIDTH = 16;
    public static final int PAGE_BUTTON_HEIGHT = 14;
    public static final int NEXT_PAGE_OFFSET = 58;
    public static final int PAGE_TEXT_OFFSET = 19;
    public static final int PAGE_TEXT_TOP = 3;

    public final Area searchArea;
    public final Area searchField;
    public final Area clearSearch;
    public final Area previousPage;
    public final Area nextPage;

    private BottomPanelBrowseLayout(
            Area searchArea,
            Area searchField,
            Area clearSearch,
            Area previousPage,
            Area nextPage) {
        this.searchArea = searchArea;
        this.searchField = searchField;
        this.clearSearch = clearSearch;
        this.previousPage = previousPage;
        this.nextPage = nextPage;
    }

    public static BottomPanelBrowseLayout resolve(
            int searchX, int y, int searchAreaWidth, int pagerX) {
        if (searchAreaWidth < 0) {
            throw new IllegalArgumentException("search area width must be non-negative");
        }
        int fieldWidth = Math.max(
                SEARCH_MIN_WIDTH,
                searchAreaWidth - CLEAR_GAP - CLEAR_SIZE);
        int resolvedAreaWidth = fieldWidth + CLEAR_GAP + CLEAR_SIZE;
        return new BottomPanelBrowseLayout(
                new Area(searchX, y, resolvedAreaWidth, SEARCH_HEIGHT),
                new Area(searchX, y, fieldWidth, SEARCH_HEIGHT),
                new Area(
                        searchX + fieldWidth + CLEAR_GAP,
                        y + CLEAR_Y_OFFSET,
                        CLEAR_SIZE,
                        CLEAR_SIZE),
                new Area(
                        pagerX, y,
                        PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT),
                new Area(
                        pagerX + NEXT_PAGE_OFFSET, y,
                        PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT));
    }

    public int pageTextX() {
        return previousPage.x + PAGE_TEXT_OFFSET;
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
