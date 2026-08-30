package com.rtsbuilding.rtsbuilding.uikit.layout;

/**
 * 底栏合成区的纯 Java 布局、滚动钳制与槽位命中。
 *
 * <p>本类是生产绘制、鼠标输入、滚轮和离屏预览的共同几何来源。它不读取配方、物品或 Minecraft
 * 控件，也不执行搜索、合成和网络动作。</p>
 */
public final class BottomPanelCraftLayout {
    public static final int TITLE_X = 5;
    public static final int TITLE_Y = 4;
    public static final int SEARCH_CONTENT_INSET = 2;
    public static final int SEARCH_MIN_CONTENT_W = 22;
    public static final int BUTTON_TEXT_TOP = 2;

    public final Area panel;
    public final Area search;
    public final Area apply;
    public final Area toggle;
    public final int gridX;
    public final int gridY;
    public final int visibleRows;
    public final int maxScroll;
    public final int scroll;
    public final int startIndex;
    public final int entryCount;

    private BottomPanelCraftLayout(Area panel, Area search, Area apply, Area toggle,
                                   int gridX, int gridY, int visibleRows,
                                   int maxScroll, int scroll, int startIndex,
                                   int entryCount) {
        this.panel = panel;
        this.search = search;
        this.apply = apply;
        this.toggle = toggle;
        this.gridX = gridX;
        this.gridY = gridY;
        this.visibleRows = visibleRows;
        this.maxScroll = maxScroll;
        this.scroll = scroll;
        this.startIndex = startIndex;
        this.entryCount = entryCount;
    }

    public static BottomPanelCraftLayout resolve(int x, int y, int width, int height,
                                                 int entryCount, int requestedScroll) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("craft panel size must be positive");
        }
        if (entryCount < 0) {
            throw new IllegalArgumentException("entryCount must be non-negative");
        }

        int searchX = x + 4;
        int searchY = y + 15;
        int searchWidth = Math.max(24, width
                - RtsMainlineLayout.CRAFT_PANEL_APPLY_W
                - RtsMainlineLayout.CRAFT_PANEL_TOGGLE_W - 16);
        int applyX = searchX + searchWidth + 4;
        int toggleX = applyX + RtsMainlineLayout.CRAFT_PANEL_APPLY_W + 4;
        int gridX = x + 4;
        int gridY = searchY + RtsMainlineLayout.CRAFT_PANEL_SEARCH_H + 6;
        int visibleRows = Math.max(1,
                (height - (gridY - y) - 6) / RtsMainlineLayout.CRAFT_PANEL_PITCH);
        int totalRows = Math.max(1, divideRoundUp(entryCount, RtsMainlineLayout.CRAFT_PANEL_COLS));
        int maxScroll = Math.max(0, totalRows - visibleRows);
        int scroll = clamp(requestedScroll, 0, maxScroll);

        return new BottomPanelCraftLayout(
                new Area(x, y, width, height),
                new Area(searchX, searchY, searchWidth, RtsMainlineLayout.CRAFT_PANEL_SEARCH_H),
                new Area(applyX, searchY, RtsMainlineLayout.CRAFT_PANEL_APPLY_W,
                        RtsMainlineLayout.CRAFT_PANEL_SEARCH_H),
                new Area(toggleX, searchY, RtsMainlineLayout.CRAFT_PANEL_TOGGLE_W,
                        RtsMainlineLayout.CRAFT_PANEL_SEARCH_H),
                gridX, gridY, visibleRows, maxScroll, scroll,
                scroll * RtsMainlineLayout.CRAFT_PANEL_COLS, entryCount);
    }

    public int slotX(int column) {
        requireCell(column, RtsMainlineLayout.CRAFT_PANEL_COLS, "column");
        return gridX + column * RtsMainlineLayout.CRAFT_PANEL_PITCH;
    }

    public int slotY(int row) {
        requireCell(row, visibleRows, "row");
        return gridY + row * RtsMainlineLayout.CRAFT_PANEL_PITCH;
    }

    public int entryIndex(int row, int column) {
        requireCell(row, visibleRows, "row");
        requireCell(column, RtsMainlineLayout.CRAFT_PANEL_COLS, "column");
        int index = startIndex + row * RtsMainlineLayout.CRAFT_PANEL_COLS + column;
        return index < entryCount ? index : -1;
    }

    /** 只命中真实 18px 槽位，不把 2px pitch 间隙算作相邻槽位。 */
    public int entryIndexAt(double mouseX, double mouseY) {
        if (mouseX < gridX || mouseY < gridY) {
            return -1;
        }
        int column = (int) ((mouseX - gridX) / RtsMainlineLayout.CRAFT_PANEL_PITCH);
        int row = (int) ((mouseY - gridY) / RtsMainlineLayout.CRAFT_PANEL_PITCH);
        if (column < 0 || column >= RtsMainlineLayout.CRAFT_PANEL_COLS
                || row < 0 || row >= visibleRows) {
            return -1;
        }
        int slotX = slotX(column);
        int slotY = slotY(row);
        if (mouseX >= slotX + RtsMainlineLayout.CRAFT_PANEL_SLOT
                || mouseY >= slotY + RtsMainlineLayout.CRAFT_PANEL_SLOT) {
            return -1;
        }
        return entryIndex(row, column);
    }

    private static int divideRoundUp(int value, int divisor) {
        return value == 0 ? 0 : 1 + (value - 1) / divisor;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void requireCell(int value, int count, String name) {
        if (value < 0 || value >= count) {
            throw new IllegalArgumentException(name + " out of bounds: " + value);
        }
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
