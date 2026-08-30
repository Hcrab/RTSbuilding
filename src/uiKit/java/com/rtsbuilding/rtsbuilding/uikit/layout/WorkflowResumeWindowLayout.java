package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 普通放置恢复窗与蓝图材料恢复窗的共享几何。
 *
 * <p>本类只负责像素边界、可见材料行、滚动钳制和半开按钮命中；不认识 Minecraft
 * ItemStack、扫描 payload 或网络恢复策略。</p>
 */
public final class WorkflowResumeWindowLayout {
    public static final int PLACEMENT_W = 260;
    public static final int PLACEMENT_H = 200;
    public static final int BLUEPRINT_W = 280;
    public static final int BLUEPRINT_H = 240;
    public static final int PADDING = 8;
    public static final int ACTION_H = 20;
    public static final int ACTION_GAP = 2;
    public static final int PLACEMENT_LINE_H = 12;
    public static final int BLUEPRINT_ROW_H = 18;
    public static final int BLUEPRINT_MAX_VISIBLE_ROWS = 8;
    public static final int ITEM_SIZE = 16;
    public static final int PLACEMENT_ITEM_TEXT_X = 20;
    public static final int PLACEMENT_ITEM_TEXT_TOP = 4;
    public static final int BLUEPRINT_HEADER_TOP = 22;

    private WorkflowResumeWindowLayout() {
    }

    public static PlacementGeometry placement(
            int contentX,
            int contentY,
            int contentWidth,
            int contentHeight,
            boolean hasConflicts) {
        int x = contentX + PADDING;
        int y = contentY + PADDING;
        int innerWidth = Math.max(0, contentWidth - PADDING * 2);
        int statsY = y + 28;
        int statCount = hasConflicts ? 6 : 5;
        int actionY = contentY + contentHeight - ACTION_H - PADDING;
        UiRect primary;
        UiRect secondary = null;
        if (hasConflicts) {
            int buttonWidth = Math.max(0, (innerWidth - ACTION_GAP) / 2);
            primary = new UiRect(x, actionY, buttonWidth, ACTION_H);
            secondary = new UiRect(
                    x + buttonWidth + ACTION_GAP,
                    actionY,
                    buttonWidth,
                    ACTION_H);
        } else {
            primary = new UiRect(x, actionY, innerWidth, ACTION_H);
        }
        return new PlacementGeometry(
                x,
                y,
                innerWidth,
                statsY,
                x + innerWidth - 80,
                hasConflicts,
                new UiRect(x, y, ITEM_SIZE, ITEM_SIZE),
                new UiRect(x, y + 22, innerWidth, 1),
                new UiRect(
                        x,
                        statsY + statCount * PLACEMENT_LINE_H + 4,
                        innerWidth,
                        1),
                primary,
                secondary);
    }

    public static BlueprintGeometry blueprint(
            int contentX,
            int contentY,
            int contentWidth,
            int contentHeight,
            int visibleRows) {
        int x = contentX + PADDING;
        int y = contentY + PADDING;
        int innerWidth = Math.max(0, contentWidth - PADDING * 2);
        int safeRows = Math.max(
                0,
                Math.min(BLUEPRINT_MAX_VISIBLE_ROWS, visibleRows));
        int rowStartY = y + 40;
        List<BlueprintRowGeometry> rows =
                new ArrayList<BlueprintRowGeometry>(safeRows);
        for (int index = 0; index < safeRows; index++) {
            int rowY = rowStartY + index * BLUEPRINT_ROW_H;
            rows.add(new BlueprintRowGeometry(
                    index,
                    new UiRect(x, rowY, innerWidth, BLUEPRINT_ROW_H),
                    new UiRect(x, rowY, ITEM_SIZE, ITEM_SIZE)));
        }
        int actionY = contentY + contentHeight - ACTION_H - PADDING;
        return new BlueprintGeometry(
                x,
                y,
                innerWidth,
                x + innerWidth - 130,
                x + innerWidth - 70,
                new UiRect(x, y + 18, innerWidth, 1),
                new UiRect(x, actionY - 4, innerWidth, 1),
                new UiRect(x, actionY, innerWidth, ACTION_H),
                rows);
    }

    public static int clampBlueprintScroll(int scroll, int totalRows) {
        return Math.max(
                0,
                Math.min(
                        Math.max(0, totalRows - BLUEPRINT_MAX_VISIBLE_ROWS),
                        scroll));
    }

    /**
     * 保留当前生产回调的滚轮符号约定：正值向列表末端，负值向列表开头。
     */
    public static int scrollBlueprint(
            int scroll,
            int totalRows,
            double scrollY) {
        int current = clampBlueprintScroll(scroll, totalRows);
        if (scrollY > 0.0D) {
            return clampBlueprintScroll(current + 1, totalRows);
        }
        if (scrollY < 0.0D) {
            return clampBlueprintScroll(current - 1, totalRows);
        }
        return current;
    }

    public enum PlacementControl {
        RESUME_OR_SKIP,
        OVERWRITE
    }

    public static final class PlacementGeometry {
        public final int x;
        public final int y;
        public final int innerWidth;
        public final int statsY;
        public final int valueX;
        public final boolean hasConflicts;
        public final UiRect itemIcon;
        public final UiRect topDivider;
        public final UiRect summaryDivider;
        public final UiRect primaryAction;
        public final UiRect secondaryAction;

        private PlacementGeometry(
                int x,
                int y,
                int innerWidth,
                int statsY,
                int valueX,
                boolean hasConflicts,
                UiRect itemIcon,
                UiRect topDivider,
                UiRect summaryDivider,
                UiRect primaryAction,
                UiRect secondaryAction) {
            this.x = x;
            this.y = y;
            this.innerWidth = innerWidth;
            this.statsY = statsY;
            this.valueX = valueX;
            this.hasConflicts = hasConflicts;
            this.itemIcon = itemIcon;
            this.topDivider = topDivider;
            this.summaryDivider = summaryDivider;
            this.primaryAction = primaryAction;
            this.secondaryAction = secondaryAction;
        }

        public int statY(int row) {
            return statsY + Math.max(0, row) * PLACEMENT_LINE_H;
        }

        public PlacementControl hitAt(
                double mouseX,
                double mouseY,
                boolean enabled) {
            if (!enabled) {
                return null;
            }
            if (primaryAction.contains(mouseX, mouseY)) {
                return PlacementControl.RESUME_OR_SKIP;
            }
            if (secondaryAction != null
                    && secondaryAction.contains(mouseX, mouseY)) {
                return PlacementControl.OVERWRITE;
            }
            return null;
        }
    }

    public static final class BlueprintGeometry {
        public final int x;
        public final int y;
        public final int innerWidth;
        public final int requiredColumnX;
        public final int availableColumnX;
        public final UiRect headerDivider;
        public final UiRect actionDivider;
        public final UiRect action;
        public final List<BlueprintRowGeometry> rows;

        private BlueprintGeometry(
                int x,
                int y,
                int innerWidth,
                int requiredColumnX,
                int availableColumnX,
                UiRect headerDivider,
                UiRect actionDivider,
                UiRect action,
                List<BlueprintRowGeometry> rows) {
            this.x = x;
            this.y = y;
            this.innerWidth = innerWidth;
            this.requiredColumnX = requiredColumnX;
            this.availableColumnX = availableColumnX;
            this.headerDivider = headerDivider;
            this.actionDivider = actionDivider;
            this.action = action;
            this.rows = Collections.unmodifiableList(
                    new ArrayList<BlueprintRowGeometry>(rows));
        }

        public boolean hitAction(
                double mouseX,
                double mouseY,
                boolean enabled) {
            return enabled && action.contains(mouseX, mouseY);
        }
    }

    public static final class BlueprintRowGeometry {
        public final int visibleIndex;
        public final UiRect row;
        public final UiRect itemIcon;

        private BlueprintRowGeometry(
                int visibleIndex,
                UiRect row,
                UiRect itemIcon) {
            this.visibleIndex = visibleIndex;
            this.row = row;
            this.itemIcon = itemIcon;
        }
    }
}
