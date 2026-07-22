package com.rtsbuilding.rtsbuilding.uikit.layout;

/** 底部“蓝图空间”生产布局的 Java 8 描述，不持有 Minecraft Font。 */
public final class BlueprintLibraryLayout {
    public static final int ROW_H = 24;
    public static final int BUTTON_H = 14;
    public static final int SEARCH_H = 14;
    public static final int DETAIL_BUTTON_H = 14;
    public static final int LIST_COLUMN_GAP = 4;

    private BlueprintLibraryLayout() {
    }

    public static Geometry geometry(int x, int y, int width, int height) {
        int listY = y + 19;
        int statusY = y + height - 13;
        int listH = Math.max(24, statusY - listY - 4);
        int detailsW = Math.min(210, Math.max(148, width / 4));
        int listW = Math.max(120, width - detailsW - 8);
        return new Geometry(x, y, width, height, listY, statusY, listH,
                listW, x + listW + 8, detailsW);
    }

    public static int listColumns(int width) {
        return width >= 320 ? 2 : 1;
    }

    public static int listCellWidth(int width, int columns) {
        return Math.max(80, (width - 2 - (Math.max(1, columns) - 1) * LIST_COLUMN_GAP)
                / Math.max(1, columns));
    }

    public static int maxListScroll(int entryCount, int columns, int visibleRows) {
        int safeColumns = Math.max(1, columns);
        int rows = Math.max(0, (entryCount + safeColumns - 1) / safeColumns);
        return Math.max(0, rows - Math.max(1, visibleRows));
    }

    /**
     * 计算当前列表真正会绘制的条目区间。
     *
     * <p>材料完成度可能需要扫描整张蓝图，生产适配层必须只为这个有界区间
     * 生成重快照，不能因为列表里存在大量文件就在每一帧扫描全部蓝图。</p>
     */
    public static VisibleWindow visibleWindow(int entryCount, int scrollRows,
                                               int listWidth, int listHeight) {
        int safeCount = Math.max(0, entryCount);
        int columns = listColumns(listWidth);
        int visibleRows = Math.max(1, listHeight / ROW_H);
        int clampedScroll = clamp(scrollRows, 0,
                maxListScroll(safeCount, columns, visibleRows));
        int fromIndex = Math.min(safeCount, clampedScroll * columns);
        int toIndex = Math.min(safeCount, fromIndex + visibleRows * columns);
        return new VisibleWindow(fromIndex, toIndex, columns, visibleRows, clampedScroll);
    }

    public static TopBar topBar(int x, int width, boolean captureActive,
                                int folderTextWidth, int importTextWidth,
                                int syncTextWidth, int captureTextWidth) {
        int gap = 4;
        int folderW = clamp(folderTextWidth + 12, 64, 96);
        int importW = clamp(importTextWidth + 12, 44, 72);
        int syncW = clamp(syncTextWidth + 12, 58, 94);
        int captureW = clamp(captureTextWidth + 12, 74, 112);
        int actionW = folderW + importW + syncW + captureW + gap * 3;
        int searchX = x + actionW + 8;
        int searchW = Math.max(60, x + width - searchX);
        if (searchW < 80) {
            folderW = 56;
            importW = 44;
            syncW = 58;
            captureW = 70;
            actionW = folderW + importW + syncW + captureW + gap * 3;
            searchX = x + actionW + 6;
            searchW = Math.max(50, x + width - searchX);
        }
        int folderX = x;
        int importX = folderX + folderW + gap;
        int syncX = importX + importW + gap;
        int captureX = syncX + syncW + gap;
        return new TopBar(folderX, folderW, importX, importW, syncX, syncW,
                captureX, captureW, searchX, searchW, captureActive);
    }

    public static RowActions rowActions(int cellX, int rowY, int cellWidth,
                                        int saveTextWidth, int renameTextWidth,
                                        int deleteTextWidth) {
        int gap = 3;
        int saveW = clamp(saveTextWidth + 12, 38, 46);
        int renameW = clamp(renameTextWidth + 12, 38, 48);
        int deleteW = clamp(deleteTextWidth + 12, 34, 42);
        int totalW = saveW + renameW + deleteW + gap * 2;
        int x = cellX + Math.max(4, cellWidth - totalW - 4);
        return new RowActions(x, saveW, x + saveW + gap, renameW,
                x + saveW + gap + renameW + gap, deleteW, rowY + 5);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Geometry {
        public final int x, y, width, height, listY, statusY, listH;
        public final int listW, detailsX, detailsW;

        private Geometry(int x, int y, int width, int height, int listY, int statusY,
                         int listH, int listW, int detailsX, int detailsW) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.listY = listY;
            this.statusY = statusY;
            this.listH = listH;
            this.listW = listW;
            this.detailsX = detailsX;
            this.detailsW = detailsW;
        }
    }

    public static final class TopBar {
        public final int folderX, folderW, importX, importW, syncX, syncW;
        public final int captureX, captureW, searchX, searchW;
        public final boolean captureActive;

        private TopBar(int folderX, int folderW, int importX, int importW,
                       int syncX, int syncW, int captureX, int captureW,
                       int searchX, int searchW, boolean captureActive) {
            this.folderX = folderX;
            this.folderW = folderW;
            this.importX = importX;
            this.importW = importW;
            this.syncX = syncX;
            this.syncW = syncW;
            this.captureX = captureX;
            this.captureW = captureW;
            this.searchX = searchX;
            this.searchW = searchW;
            this.captureActive = captureActive;
        }
    }

    public static final class RowActions {
        public final int saveX, saveW, renameX, renameW, deleteX, deleteW, buttonY;

        private RowActions(int saveX, int saveW, int renameX, int renameW,
                           int deleteX, int deleteW, int buttonY) {
            this.saveX = saveX;
            this.saveW = saveW;
            this.renameX = renameX;
            this.renameW = renameW;
            this.deleteX = deleteX;
            this.deleteW = deleteW;
            this.buttonY = buttonY;
        }
    }

    public static final class VisibleWindow {
        public final int fromIndex, toIndex, columns, visibleRows, scrollRows;

        private VisibleWindow(int fromIndex, int toIndex, int columns,
                              int visibleRows, int scrollRows) {
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
            this.columns = columns;
            this.visibleRows = visibleRows;
            this.scrollRows = scrollRows;
        }

        public int size() {
            return Math.max(0, toIndex - fromIndex);
        }
    }
}
