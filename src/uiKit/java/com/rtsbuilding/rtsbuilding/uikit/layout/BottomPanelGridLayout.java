package com.rtsbuilding.rtsbuilding.uikit.layout;

/**
 * 底栏物品网格的纯 Java 布局与命中绑定。
 *
 * <p>本类只负责把可用区域拆成主网格、最近使用网格和可选流体条，并用同一份几何数据解析槽位索引。
 * 它不负责 Minecraft 绘制、滚动状态、物品查询或点击后的业务动作。生产绘制与左右键输入必须共同消费
 * 此结果，避免三条路径各自复制坐标公式。</p>
 */
public final class BottomPanelGridLayout {
    public static final int EMPTY_TEXT_HORIZONTAL_INSET = 6;
    private BottomPanelGridLayout() {
    }

    public static Layout creative(int x, int y, int width, int height,
                                  int slotSize, int recentGap) {
        requirePositive(width, "width");
        requirePositive(height, "height");
        requirePositive(slotSize, "slotSize");
        requireNonNegative(recentGap, "recentGap");
        int mainWidth = Math.max(slotSize, (width - recentGap) / 2);
        int recentX = x + mainWidth + recentGap;
        int recentWidth = Math.max(slotSize, width - mainWidth - recentGap);
        return new Layout(new GridArea(x, y, mainWidth, height),
                new GridArea(recentX, y, recentWidth, height), GridArea.EMPTY);
    }

    public static Layout storage(int x, int y, int width, int height,
                                 int slotSize, int recentGap,
                                 int fluidWidth, int fluidGap) {
        requirePositive(width, "width");
        requirePositive(height, "height");
        requirePositive(slotSize, "slotSize");
        requireNonNegative(recentGap, "recentGap");
        requireNonNegative(fluidWidth, "fluidWidth");
        requireNonNegative(fluidGap, "fluidGap");

        GridArea fluid = fluidWidth > 0
                ? new GridArea(x, y, fluidWidth, height)
                : GridArea.EMPTY;
        int itemX = fluidWidth > 0 ? x + fluidWidth + fluidGap : x;
        int itemWidth = fluidWidth > 0
                ? Math.max(slotSize, width - fluidWidth - fluidGap)
                : width;
        int mainWidth = Math.max(slotSize, (itemWidth - recentGap) / 2);
        int recentX = itemX + mainWidth + recentGap;
        int recentWidth = Math.max(slotSize, itemWidth - mainWidth - recentGap);
        return new Layout(new GridArea(itemX, y, mainWidth, height),
                new GridArea(recentX, y, recentWidth, height), fluid);
    }

    /**
     * 按网格实际可见的完整槽位解析索引；右边界和下边界采用半开区间。
     *
     * @param page 从零开始的页码；普通网格传 0
     */
    public static int indexAt(GridArea area, int slotSize, double mouseX, double mouseY,
                              int entryCount, int page) {
        if (area == null || area.isEmpty() || slotSize <= 0 || entryCount <= 0 || page < 0) {
            return -1;
        }
        return resolve(area, slotSize, slotSize, entryCount, page).entryIndexAt(mouseX, mouseY);
    }

    /**
     * 把一个区域解析成可直接用于绘制和命中的网格视图。
     *
     * @param pitch 相邻槽位左上角之间的距离
     * @param slotExtent 真正可见且可命中的槽位边长；小于 pitch 时，剩余区域是不可点击间隙
     */
    public static GridView resolve(GridArea area, int pitch, int slotExtent,
                                   int entryCount, int page) {
        if (area == null || area.isEmpty()) {
            throw new IllegalArgumentException("area must not be empty");
        }
        requirePositive(pitch, "pitch");
        requirePositive(slotExtent, "slotExtent");
        if (slotExtent > pitch) {
            throw new IllegalArgumentException("slotExtent must not exceed pitch");
        }
        requireNonNegative(entryCount, "entryCount");
        requireNonNegative(page, "page");
        int columns = Math.max(1, area.width / pitch);
        int rows = Math.max(1, area.height / pitch);
        return new GridView(area, pitch, slotExtent, columns, rows, entryCount, page);
    }

    private static int checkedCell(int value, int count, String name) {
        if (value < 0 || value >= count) {
            throw new IllegalArgumentException(name + " out of bounds: " + value);
        }
        return value;
    }

    public static final class GridView {
        public final GridArea area;
        public final int pitch;
        public final int slotExtent;
        public final int columns;
        public final int rows;
        public final int capacity;
        public final int page;
        public final int startIndex;
        public final int entryCount;

        private GridView(GridArea area, int pitch, int slotExtent,
                         int columns, int rows, int entryCount, int page) {
            this.area = area;
            this.pitch = pitch;
            this.slotExtent = slotExtent;
            this.columns = columns;
            this.rows = rows;
            this.capacity = columns * rows;
            this.page = page;
            long requestedStart = (long) page * capacity;
            this.startIndex = requestedStart > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE : (int) requestedStart;
            this.entryCount = entryCount;
        }

        public int slotX(int column) {
            return area.x + checkedCell(column, columns, "column") * pitch;
        }

        public int slotY(int row) {
            return area.y + checkedCell(row, rows, "row") * pitch;
        }

        public int entryIndex(int row, int column) {
            checkedCell(row, rows, "row");
            checkedCell(column, columns, "column");
            long index = (long) startIndex + row * columns + column;
            return index < entryCount && index <= Integer.MAX_VALUE ? (int) index : -1;
        }

        /** 半开命中真实槽位，明确拒绝 pitch 中没有画槽位的间隙。 */
        public int entryIndexAt(double mouseX, double mouseY) {
            int visibleWidth = columns * pitch;
            int visibleHeight = rows * pitch;
            if (mouseX < area.x || mouseX >= area.x + visibleWidth
                    || mouseY < area.y || mouseY >= area.y + visibleHeight) {
                return -1;
            }
            int column = (int) ((mouseX - area.x) / pitch);
            int row = (int) ((mouseY - area.y) / pitch);
            int slotX = slotX(column);
            int slotY = slotY(row);
            if (mouseX >= slotX + slotExtent || mouseY >= slotY + slotExtent) {
                return -1;
            }
            return entryIndex(row, column);
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    public static final class Layout {
        public final GridArea main;
        public final GridArea recent;
        public final GridArea fluid;

        private Layout(GridArea main, GridArea recent, GridArea fluid) {
            this.main = main;
            this.recent = recent;
            this.fluid = fluid;
        }
    }

    public static final class GridArea {
        private static final GridArea EMPTY = new GridArea(0, 0, 0, 0);

        public final int x;
        public final int y;
        public final int width;
        public final int height;

        private GridArea(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }
    }
}
