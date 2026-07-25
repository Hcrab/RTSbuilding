package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 工作流窗口的主线固定列与动态高度。
 *
 * <p>这里只保存生产面板、输入和离屏预览必须共同遵守的几何与半开命中，不处理工作流状态、
 * 文本度量或网络动作。每一行的正文、进度条和三枚动作键只在这里排布一次。</p>
 */
public final class WorkflowWindowLayout {
    public static final int WINDOW_W = 220;
    public static final int ROW_H = 22;
    public static final int PADDING = 6;
    public static final int BUTTON_W = 16;
    public static final int BAR_H = 6;
    public static final int BUTTON_GAP = 2;
    public static final int ROW_BUTTON_GAP = 2;
    public static final int LABEL_X = 4;
    public static final int LABEL_Y = 2;
    public static final int PROGRESS_X = 4;
    public static final int PROGRESS_Y = 12;
    public static final int PROGRESS_TEXT_X = 2;
    public static final int PROGRESS_TEXT_Y = 1;

    private WorkflowWindowLayout() {
    }

    public static int rowWidth() {
        return WINDOW_W - PADDING * 2
                - BUTTON_W * 3
                - BUTTON_GAP * 2
                - ROW_BUTTON_GAP;
    }

    public static int protectX(int contentX) {
        return contentX + rowWidth() + ROW_BUTTON_GAP;
    }

    public static int actionX(int contentX) {
        return protectX(contentX) + BUTTON_W + BUTTON_GAP;
    }

    public static int deleteX(int contentX) {
        return actionX(contentX) + BUTTON_W + BUTTON_GAP;
    }

    public static int totalHeight(int titleBarHeight, int rows) {
        return titleBarHeight + 1 + PADDING + Math.max(0, rows) * ROW_H + PADDING;
    }

    public static Geometry geometry(int contentX, int firstRowY, int rowCount) {
        return new Geometry(contentX, firstRowY, Math.max(0, rowCount));
    }

    public enum Control {
        PROTECT,
        ACTION,
        DELETE
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
        public final UiRect progress;
        public final UiRect protect;
        public final UiRect action;
        public final UiRect delete;

        private RowGeometry(int contentX, int rowY) {
            int rowWidth = rowWidth();
            this.row = new UiRect(contentX, rowY, rowWidth, ROW_H);
            this.progress = new UiRect(
                    contentX + PROGRESS_X,
                    rowY + PROGRESS_Y,
                    rowWidth - PROGRESS_X * 2,
                    BAR_H);
            this.protect = new UiRect(
                    protectX(contentX), rowY, BUTTON_W, ROW_H);
            this.action = new UiRect(
                    actionX(contentX), rowY, BUTTON_W, ROW_H);
            this.delete = new UiRect(
                    deleteX(contentX), rowY, BUTTON_W, ROW_H);
        }
    }

    public static final class Geometry {
        public final int contentX;
        public final int firstRowY;
        public final List<RowGeometry> rows;

        private Geometry(int contentX, int firstRowY, int rowCount) {
            this.contentX = contentX;
            this.firstRowY = firstRowY;
            List<RowGeometry> result =
                    new ArrayList<RowGeometry>(rowCount);
            for (int index = 0; index < rowCount; index++) {
                result.add(new RowGeometry(
                        contentX, firstRowY + index * ROW_H));
            }
            this.rows = Collections.unmodifiableList(result);
        }

        /** 只返回三枚动作键；正文与按钮间隙不会被伪装成可执行动作。 */
        public Hit hitAt(double mouseX, double mouseY) {
            for (int index = 0; index < rows.size(); index++) {
                RowGeometry row = rows.get(index);
                if (row.protect.contains(mouseX, mouseY)) {
                    return new Hit(index, Control.PROTECT);
                }
                if (row.action.contains(mouseX, mouseY)) {
                    return new Hit(index, Control.ACTION);
                }
                if (row.delete.contains(mouseX, mouseY)) {
                    return new Hit(index, Control.DELETE);
                }
            }
            return null;
        }
    }
}
