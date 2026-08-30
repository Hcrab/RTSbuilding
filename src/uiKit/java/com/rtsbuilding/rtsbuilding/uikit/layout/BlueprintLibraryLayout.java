package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 底部“蓝图空间”生产布局的 Java 8 描述，不持有 Minecraft Font。 */
public final class BlueprintLibraryLayout {
    public static final int ROW_H = 24;
    public static final int BUTTON_H = 14;
    public static final int SEARCH_H = 14;
    public static final int DETAIL_BUTTON_H = 14;
    public static final int LIST_COLUMN_GAP = 4;
    public static final int TOP_GAP = 4;
    public static final int TOP_SEARCH_GAP = 8;
    public static final int LIST_TOP_GAP = 5;
    public static final int DETAILS_GAP = 8;
    public static final int FRAME_TEXT_X = 6;
    public static final int EMPTY_TEXT_Y = 8;
    public static final int ROW_NAME_X = 5;
    public static final int ROW_NAME_Y = 4;
    public static final int ROW_SIZE_Y = 14;
    public static final int ROW_PERCENT_RIGHT = 36;
    public static final int ROW_PROGRESS_X = 64;
    public static final int ROW_PROGRESS_RIGHT = 4;
    public static final int ROW_PROGRESS_BOTTOM = 5;
    public static final int DETAILS_NAME_Y = 6;
    public static final int DETAILS_META_Y = 18;
    public static final int DETAILS_SUMMARY_Y = 31;
    public static final int DETAILS_PROGRESS_Y = 44;
    public static final int DETAILS_CONTENT_Y = 56;
    public static final int PREVIEW_ITEM_SIZE = 18;
    public static final int PREVIEW_ITEM_PITCH = 20;
    public static final int PREVIEW_COLUMNS = 6;
    public static final int MAX_PREVIEW_ITEMS = 18;
    public static final int CAPTURE_TEXT_X = 8;
    public static final int CAPTURE_TITLE_Y = 8;
    public static final int CAPTURE_STATUS_Y = 22;
    public static final int STATUS_TEXT_X = 2;
    public static final int STATUS_TEXT_RIGHT_INSET = 8;
    public static final int SEARCH_TEXT_INSET = 4;
    public static final int SEARCH_TEXT_TOP = 3;

    private BlueprintLibraryLayout() {
    }

    public static Geometry geometry(int x, int y, int width, int height) {
        int listY = y + 19;
        int statusY = y + height - 13;
        int listH = Math.max(24, statusY - listY - 4);
        int detailsW = Math.min(210, Math.max(148, width / 4));
        int listW = Math.max(120, width - detailsW - DETAILS_GAP);
        return new Geometry(x, y, width, height, listY, statusY, listH,
                listW, x + listW + DETAILS_GAP, detailsW);
    }

    public static int listColumns(int width) {
        return width >= 320 ? 2 : 1;
    }

    public static int listCellWidth(int width, int columns) {
        return Math.max(80, (width - 2 - (Math.max(1, columns) - 1) * LIST_COLUMN_GAP)
                / Math.max(1, columns));
    }

    /**
     * 解析失败详情空间不足时，错误行替代格式行，避免被底边钳制后盖住标题。
     */
    public static boolean invalidDetailsShowMeta(int listHeight, int lineHeight) {
        return listHeight >= DETAILS_SUMMARY_Y + lineHeight + FRAME_TEXT_X;
    }

    public static int invalidDetailsTextY(int listHeight, int lineHeight) {
        return invalidDetailsShowMeta(listHeight, lineHeight)
                ? DETAILS_SUMMARY_Y
                : DETAILS_META_Y;
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
        int folderW = clamp(folderTextWidth + 12, 64, 96);
        int importW = clamp(importTextWidth + 12, 44, 72);
        int syncW = clamp(syncTextWidth + 12, 58, 94);
        int captureW = clamp(captureTextWidth + 12, 74, 112);
        int actionW = folderW + importW + syncW + captureW + TOP_GAP * 3;
        int searchX = x + actionW + TOP_SEARCH_GAP;
        int searchW = Math.max(60, x + width - searchX);
        if (searchW < 80) {
            folderW = 56;
            importW = 44;
            syncW = 58;
            captureW = 70;
            actionW = folderW + importW + syncW + captureW + TOP_GAP * 3;
            searchX = x + actionW + 6;
            searchW = Math.max(50, x + width - searchX);
        }
        int folderX = x;
        int importX = folderX + folderW + TOP_GAP;
        int syncX = importX + importW + TOP_GAP;
        int captureX = syncX + syncW + TOP_GAP;
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

    /**
     * 将本地化文字宽度转换为当前可见卡片的唯一几何。
     *
     * <p>绘制、hover、左键和离屏回放必须使用这个结果，不能各自重算列间隙或动作宽度。</p>
     */
    public static RowGeometry rowGeometry(
            int listX,
            int listY,
            int listWidth,
            int row,
            int column,
            ActionTextWidths textWidths) {
        if (textWidths == null) {
            throw new IllegalArgumentException("textWidths must not be null");
        }
        int columns = listColumns(listWidth);
        if (row < 0 || column < 0 || column >= columns) {
            throw new IllegalArgumentException("row and column must be inside the visible grid");
        }
        int cellWidth = listCellWidth(listWidth, columns);
        int cellX = listX + 1 + column * (cellWidth + LIST_COLUMN_GAP);
        int rowY = listY + row * ROW_H;
        int cellRight = Math.min(listX + listWidth - 1, cellX + cellWidth);
        int actualWidth = Math.max(44, cellRight - cellX);
        RowActions actions = rowActions(
                cellX,
                rowY,
                actualWidth,
                textWidths.save,
                textWidths.rename,
                textWidths.delete);
        int progressX = cellX + ROW_PROGRESS_X;
        int progressWidth = Math.max(
                12,
                cellX + actualWidth - progressX - ROW_PROGRESS_RIGHT);
        return new RowGeometry(
                row,
                column,
                new UiRect(cellX, rowY, actualWidth, ROW_H),
                new UiRect(cellX, rowY + 1, actualWidth, ROW_H - 2),
                new UiRect(
                        actions.saveX,
                        actions.buttonY,
                        actions.saveW,
                        DETAIL_BUTTON_H),
                new UiRect(
                        actions.renameX,
                        actions.buttonY,
                        actions.renameW,
                        DETAIL_BUTTON_H),
                new UiRect(
                        actions.deleteX,
                        actions.buttonY,
                        actions.deleteW,
                        DETAIL_BUTTON_H),
                new UiRect(
                        progressX,
                        rowY + ROW_H - ROW_PROGRESS_BOTTOM,
                        progressWidth,
                        2));
    }

    public static DetailsGeometry detailsGeometry(Geometry geometry) {
        if (geometry == null) {
            throw new IllegalArgumentException("geometry must not be null");
        }
        return new DetailsGeometry(
                geometry.detailsBounds,
                new UiRect(
                        geometry.detailsX + FRAME_TEXT_X,
                        geometry.listY + DETAILS_PROGRESS_Y,
                        Math.max(36, geometry.detailsW - FRAME_TEXT_X * 2),
                        4),
                previewSlots(
                        geometry.detailsX + FRAME_TEXT_X,
                        geometry.listY + DETAILS_CONTENT_Y,
                        geometry.listY + geometry.listH - 4));
    }

    public static List<UiRect> previewSlots(int x, int y, int bottomY) {
        List<UiRect> slots = new ArrayList<UiRect>();
        for (int index = 0; index < MAX_PREVIEW_ITEMS; index++) {
            int slotX = x + (index % PREVIEW_COLUMNS) * PREVIEW_ITEM_PITCH;
            int slotY = y + (index / PREVIEW_COLUMNS) * PREVIEW_ITEM_PITCH;
            if (slotY + PREVIEW_ITEM_SIZE > bottomY) {
                break;
            }
            slots.add(new UiRect(
                    slotX,
                    slotY,
                    PREVIEW_ITEM_SIZE,
                    PREVIEW_ITEM_SIZE));
        }
        return Collections.unmodifiableList(slots);
    }

    /**
     * 解析底部蓝图库的单次左键目标。
     *
     * <p>所有矩形都使用 {@link UiRect} 的半开边界，列间 4px 空隙、右边和下边不会误触。</p>
     */
    public static Hit hitAt(
            Geometry geometry,
            TopBar top,
            BlueprintLibraryUiState state,
            ActionTextWidths actionWidths,
            double mouseX,
            double mouseY) {
        if (geometry == null || top == null || state == null || actionWidths == null) {
            throw new IllegalArgumentException(
                    "geometry, top, state and actionWidths must not be null");
        }
        if (top.folderBounds(geometry.y).contains(mouseX, mouseY)) {
            return Hit.control(Control.OPEN_FOLDER);
        }
        if (top.importBounds(geometry.y).contains(mouseX, mouseY)) {
            return Hit.control(Control.IMPORT_FILE);
        }
        if (top.syncBounds(geometry.y).contains(mouseX, mouseY)) {
            return Hit.control(Control.SYNC_CREATE);
        }
        if (top.captureBounds(geometry.y).contains(mouseX, mouseY)) {
            return Hit.control(Control.TOGGLE_CAPTURE);
        }
        if (state.captureLocked) {
            return geometry.root.contains(mouseX, mouseY)
                    ? Hit.control(Control.CAPTURE_LOCKED_BODY)
                    : Hit.NONE;
        }
        if (top.searchBounds(geometry.y).contains(mouseX, mouseY)) {
            return Hit.control(Control.SEARCH);
        }
        if (geometry.listBounds.contains(mouseX, mouseY)) {
            List<BlueprintLibraryUiEntry> filtered = state.filteredEntries();
            VisibleWindow window = visibleWindow(
                    filtered.size(),
                    state.scrollRows,
                    geometry.listW,
                    geometry.listH);
            for (int visibleRow = 0;
                 visibleRow < window.visibleRows;
                 visibleRow++) {
                for (int column = 0; column < window.columns; column++) {
                    int index = (window.scrollRows + visibleRow)
                            * window.columns + column;
                    if (index >= filtered.size()) {
                        break;
                    }
                    RowGeometry row = rowGeometry(
                            geometry.x,
                            geometry.listY,
                            geometry.listW,
                            visibleRow,
                            column,
                            actionWidths);
                    if (!row.hitBounds.contains(mouseX, mouseY)) {
                        continue;
                    }
                    BlueprintLibraryUiEntry entry = filtered.get(index);
                    if (entry.valid() && row.save.contains(mouseX, mouseY)) {
                        return Hit.entry(Control.SAVE_AS, index);
                    }
                    if (entry.valid() && row.rename.contains(mouseX, mouseY)) {
                        return Hit.entry(Control.RENAME, index);
                    }
                    if (row.delete.contains(mouseX, mouseY)) {
                        return Hit.entry(Control.DELETE, index);
                    }
                    return Hit.entry(Control.SELECT, index);
                }
            }
            return Hit.control(Control.LIST_GAP);
        }
        if (geometry.detailsBounds.contains(mouseX, mouseY)) {
            return Hit.control(Control.DETAILS);
        }
        return geometry.root.contains(mouseX, mouseY)
                ? Hit.control(Control.PANEL_GAP)
                : Hit.NONE;
    }

    public static int scrollRows(
            int currentRows,
            int entryCount,
            int listWidth,
            int listHeight,
            double scrollY) {
        VisibleWindow window = visibleWindow(
                entryCount,
                currentRows,
                listWidth,
                listHeight);
        int delta = scrollY > 0.0D ? -1 : scrollY < 0.0D ? 1 : 0;
        return clamp(
                window.scrollRows + delta,
                0,
                maxListScroll(
                        entryCount,
                        window.columns,
                        window.visibleRows));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Geometry {
        public final int x, y, width, height, listY, statusY, listH;
        public final int listW, detailsX, detailsW;
        public final UiRect root, listBounds, detailsBounds, captureBounds;

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
            this.root = new UiRect(x, y, width, height);
            this.listBounds = new UiRect(x, listY, listW, listH);
            this.detailsBounds = new UiRect(detailsX, listY, detailsW, listH);
            this.captureBounds = new UiRect(x, listY, width, listH);
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

        public UiRect folderBounds(int y) {
            return new UiRect(folderX, y, folderW, BUTTON_H);
        }

        public UiRect importBounds(int y) {
            return new UiRect(importX, y, importW, BUTTON_H);
        }

        public UiRect syncBounds(int y) {
            return new UiRect(syncX, y, syncW, BUTTON_H);
        }

        public UiRect captureBounds(int y) {
            return new UiRect(captureX, y, captureW, BUTTON_H);
        }

        public UiRect searchBounds(int y) {
            return new UiRect(searchX, y, searchW, SEARCH_H);
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

    public static final class ActionTextWidths {
        public final int save, rename, delete;

        public ActionTextWidths(int save, int rename, int delete) {
            this.save = Math.max(0, save);
            this.rename = Math.max(0, rename);
            this.delete = Math.max(0, delete);
        }
    }

    public static final class RowGeometry {
        public final int visibleRow, column;
        public final UiRect hitBounds, card, save, rename, delete, progress;

        private RowGeometry(
                int visibleRow,
                int column,
                UiRect hitBounds,
                UiRect card,
                UiRect save,
                UiRect rename,
                UiRect delete,
                UiRect progress) {
            this.visibleRow = visibleRow;
            this.column = column;
            this.hitBounds = hitBounds;
            this.card = card;
            this.save = save;
            this.rename = rename;
            this.delete = delete;
            this.progress = progress;
        }
    }

    public static final class DetailsGeometry {
        public final UiRect frame, progress;
        public final List<UiRect> previewSlots;

        private DetailsGeometry(
                UiRect frame,
                UiRect progress,
                List<UiRect> previewSlots) {
            this.frame = frame;
            this.progress = progress;
            this.previewSlots = previewSlots;
        }
    }

    public enum Control {
        NONE,
        OPEN_FOLDER,
        IMPORT_FILE,
        SYNC_CREATE,
        TOGGLE_CAPTURE,
        SEARCH,
        SELECT,
        SAVE_AS,
        RENAME,
        DELETE,
        DETAILS,
        LIST_GAP,
        PANEL_GAP,
        CAPTURE_LOCKED_BODY
    }

    public static final class Hit {
        public static final Hit NONE = new Hit(Control.NONE, -1);

        public final Control control;
        public final int filteredIndex;

        private Hit(Control control, int filteredIndex) {
            this.control = control;
            this.filteredIndex = filteredIndex;
        }

        private static Hit control(Control control) {
            return new Hit(control, -1);
        }

        private static Hit entry(Control control, int filteredIndex) {
            return new Hit(control, filteredIndex);
        }

        public boolean consumed() {
            return control != Control.NONE;
        }
    }
}
