package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 绑定储存详情窗口的生产、输入与离屏唯一几何来源。
 *
 * <p>本类拥有固定列、可见行、滚动条和半开动作命中；它不持有 ItemStack、文本框或网络动作。
 * 行正文与 2px 行距不会被当作优先级、仅提取或解绑命令。</p>
 */
public final class StorageWindowLayout {
    public static final int WINDOW_W = 390;
    public static final int WINDOW_H = 210;
    public static final int ROW_H = 32;
    public static final int ROW_CHROME_H = ROW_H - 2;
    public static final int HEADER_H = 26;
    public static final int PRIORITY_W = 46;
    public static final int EXTRACT_W = 38;
    public static final int UNLINK_W = 48;
    public static final int UNLINK_H = 16;
    public static final int CONTROL_H = 16;
    public static final int SCROLLBAR_W = 6;
    public static final int SCROLLBAR_GAP = 5;
    public static final int CONTENT_PADDING = 8;
    public static final int COLUMN_GAP = 6;
    public static final int ROW_ICON_X = 5;
    public static final int ROW_ICON_Y = 5;
    public static final int ROW_ICON_SIZE = 16;
    public static final int ROW_TEXT_X = 26;
    public static final int ROW_LABEL_Y = 4;
    public static final int ROW_POSITION_Y = 15;
    public static final int CONTROL_TEXT_X = 4;
    public static final int CONTROL_TEXT_Y = 4;
    public static final int STATUS_Y = HEADER_H + 12;
    public static final int STATUS_DETAIL_GAP = 12;
    public static final int SCROLLBAR_THUMB_MIN_H = 14;
    public static final int HEADER_COLUMN_TOP = 12;

    private StorageWindowLayout() {
    }

    public static int left(int contentX) {
        return contentX + CONTENT_PADDING;
    }

    public static int top(int contentY) {
        return contentY + CONTENT_PADDING;
    }

    public static int innerWidth(int contentWidth) {
        return contentWidth - CONTENT_PADDING * 2;
    }

    public static int visibleRows(int contentHeight) {
        return Math.max(
                1,
                (contentHeight - HEADER_H - CONTENT_PADDING * 2) / ROW_H);
    }

    public static int rowWidth(
            int innerWidth,
            boolean scrollbar) {
        return innerWidth - (scrollbar
                ? SCROLLBAR_W + SCROLLBAR_GAP
                : 0);
    }

    public static int firstRowY(int contentY) {
        return top(contentY) + HEADER_H;
    }

    public static int controlY(int rowY) {
        return rowY + 7;
    }

    public static int unlinkX(int rowX, int rowW) {
        return rowX + rowW - UNLINK_W - COLUMN_GAP;
    }

    public static int extractX(int rowX, int rowW) {
        return unlinkX(rowX, rowW) - EXTRACT_W - COLUMN_GAP;
    }

    public static int priorityX(int rowX, int rowW) {
        return extractX(rowX, rowW) - PRIORITY_W - COLUMN_GAP;
    }

    public static Geometry geometry(
            int contentX,
            int contentY,
            int contentWidth,
            int contentHeight,
            int visibleEntryCount,
            int totalRows,
            int scroll) {
        return new Geometry(
                contentX,
                contentY,
                contentWidth,
                contentHeight,
                visibleEntryCount,
                totalRows,
                scroll);
    }

    public enum Control {
        PRIORITY,
        EXTRACT,
        UNLINK
    }

    public static final class Hit {
        public final int rowIndex;
        public final Control control;

        private Hit(int rowIndex, Control control) {
            this.rowIndex = rowIndex;
            this.control = control;
        }
    }

    public static final class RowGeometry {
        public final UiRect row;
        public final UiRect icon;
        public final UiRect priority;
        public final UiRect extract;
        public final UiRect unlink;

        private RowGeometry(int rowX, int rowY, int rowWidth) {
            this.row = new UiRect(
                    rowX,
                    rowY,
                    rowWidth,
                    ROW_CHROME_H);
            this.icon = new UiRect(
                    rowX + ROW_ICON_X,
                    rowY + ROW_ICON_Y,
                    ROW_ICON_SIZE,
                    ROW_ICON_SIZE);
            int controlsY = controlY(rowY);
            this.priority = new UiRect(
                    priorityX(rowX, rowWidth),
                    controlsY,
                    PRIORITY_W,
                    CONTROL_H);
            this.extract = new UiRect(
                    extractX(rowX, rowWidth),
                    controlsY,
                    EXTRACT_W,
                    CONTROL_H);
            this.unlink = new UiRect(
                    unlinkX(rowX, rowWidth),
                    controlsY,
                    UNLINK_W,
                    UNLINK_H);
        }
    }

    public static final class Geometry {
        public final int x;
        public final int y;
        public final int innerWidth;
        public final int rowWidth;
        public final int priorityColumnX;
        public final int extractColumnX;
        public final int visibleCapacity;
        public final int firstRowY;
        public final List<RowGeometry> rows;
        public final UiRect scrollbarTrack;
        public final UiRect scrollbarInset;
        public final UiRect scrollbarThumb;

        private Geometry(
                int contentX,
                int contentY,
                int contentWidth,
                int contentHeight,
                int visibleEntryCount,
                int totalRows,
                int scroll) {
            this.x = left(contentX);
            this.y = top(contentY);
            this.innerWidth = innerWidth(contentWidth);
            this.visibleCapacity = visibleRows(contentHeight);
            boolean hasScrollbar = totalRows > visibleCapacity;
            this.rowWidth = rowWidth(innerWidth, hasScrollbar);
            this.priorityColumnX = priorityX(x, rowWidth);
            this.extractColumnX = extractX(x, rowWidth);
            this.firstRowY = firstRowY(contentY);

            int rowCount = Math.max(
                    0,
                    Math.min(visibleEntryCount, visibleCapacity));
            List<RowGeometry> resolvedRows =
                    new ArrayList<RowGeometry>(rowCount);
            for (int index = 0; index < rowCount; index++) {
                resolvedRows.add(new RowGeometry(
                        x,
                        firstRowY + index * ROW_H,
                        rowWidth));
            }
            this.rows = Collections.unmodifiableList(resolvedRows);

            if (!hasScrollbar) {
                this.scrollbarTrack = null;
                this.scrollbarInset = null;
                this.scrollbarThumb = null;
                return;
            }
            int trackX = x + rowWidth + SCROLLBAR_GAP;
            int trackHeight = visibleCapacity * ROW_H;
            this.scrollbarTrack = new UiRect(
                    trackX,
                    firstRowY,
                    SCROLLBAR_W,
                    trackHeight);
            this.scrollbarInset = new UiRect(
                    trackX + 1,
                    firstRowY + 1,
                    SCROLLBAR_W - 2,
                    Math.max(0, trackHeight - 2));
            int thumbHeight = Math.max(
                    SCROLLBAR_THUMB_MIN_H,
                    trackHeight * visibleCapacity / Math.max(1, totalRows));
            thumbHeight = Math.min(trackHeight, thumbHeight);
            int maxScroll = Math.max(0, totalRows - visibleCapacity);
            int clampedScroll = Math.max(0, Math.min(scroll, maxScroll));
            int thumbY = firstRowY + (trackHeight - thumbHeight)
                    * clampedScroll / Math.max(1, maxScroll);
            this.scrollbarThumb = new UiRect(
                    trackX + 1,
                    thumbY,
                    SCROLLBAR_W - 2,
                    thumbHeight);
        }

        public boolean hasScrollbar() {
            return scrollbarTrack != null;
        }

        /** 只返回三枚动作控件；正文、行距和列间隙均返回 null。 */
        public Hit hitAt(double mouseX, double mouseY) {
            for (int index = 0; index < rows.size(); index++) {
                RowGeometry row = rows.get(index);
                if (row.priority.contains(mouseX, mouseY)) {
                    return new Hit(index, Control.PRIORITY);
                }
                if (row.extract.contains(mouseX, mouseY)) {
                    return new Hit(index, Control.EXTRACT);
                }
                if (row.unlink.contains(mouseX, mouseY)) {
                    return new Hit(index, Control.UNLINK);
                }
            }
            return null;
        }

        /**
         * 返回鼠标纵坐标对应的真实可见行；2px 行距属于该行但永远不是动作键。
         */
        public int rowIndexAt(double mouseY) {
            if (mouseY < firstRowY) {
                return -1;
            }
            int index = (int) ((mouseY - firstRowY) / ROW_H);
            return index >= 0 && index < rows.size() ? index : -1;
        }
    }
}
