package com.rtsbuilding.rtsbuilding.client.screen.developer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 开发者任务页的纯响应式布局。
 *
 * <p>常规屏幕保持原有单列像素坐标；只有可用高度不足、原布局会碰到返回按钮时才分栏。
 * 本类不持有 Screen、字体或任务状态，因此高 GUI 缩放可以用普通单元测试验证。</p>
 */
public final class RtsDeveloperTaskLayout {
    static final int PAGE_MARGIN = 16;
    static final int FIRST_TASK_Y = 48;
    static final int BUTTON_HEIGHT = 20;
    static final int BUTTON_GAP = 6;
    static final int MAX_CONTENT_WIDTH = 260;

    private RtsDeveloperTaskLayout() {
    }

    public static Layout resolve(int screenWidth, int screenHeight, int taskCount) {
        int safeWidth = Math.max(1, screenWidth);
        int safeHeight = Math.max(1, screenHeight);
        int contentWidth = Math.min(MAX_CONTENT_WIDTH, Math.max(1, safeWidth - PAGE_MARGIN * 2));
        int contentX = Math.max(0, (safeWidth - contentWidth) / 2);
        int backY = Math.max(0, safeHeight - 32);

        int usableBottom = Math.max(FIRST_TASK_Y + BUTTON_HEIGHT, backY - 2);
        int rowsPerColumn = Math.max(1,
                (usableBottom - FIRST_TASK_Y + BUTTON_GAP) / (BUTTON_HEIGHT + BUTTON_GAP));
        int safeTaskCount = Math.max(0, taskCount);
        int columns = Math.max(1, (safeTaskCount + rowsPerColumn - 1) / rowsPerColumn);
        int buttonWidth = Math.max(1,
                (contentWidth - BUTTON_GAP * Math.max(0, columns - 1)) / columns);

        List<Bounds> tasks = new ArrayList<>(safeTaskCount);
        for (int index = 0; index < safeTaskCount; index++) {
            int column = index / rowsPerColumn;
            int row = index % rowsPerColumn;
            int x = contentX + column * (buttonWidth + BUTTON_GAP);
            int y = FIRST_TASK_Y + row * (BUTTON_HEIGHT + BUTTON_GAP);
            tasks.add(new Bounds(x, y, buttonWidth, BUTTON_HEIGHT));
        }
        return new Layout(tasks,
                new Bounds(contentX, backY, contentWidth, BUTTON_HEIGHT),
                safeWidth / 2, 18, 34);
    }

    public static final class Layout {
        private final List<Bounds> taskButtons;
        private final Bounds backButton;
        private final int centerX;
        private final int titleY;
        private final int activeStatusY;

        public Layout(List<Bounds> taskButtons, Bounds backButton,
                      int centerX, int titleY, int activeStatusY) {
            this.taskButtons = Collections.unmodifiableList(new ArrayList<Bounds>(taskButtons));
            this.backButton = backButton;
            this.centerX = centerX;
            this.titleY = titleY;
            this.activeStatusY = activeStatusY;
        }

        public List<Bounds> taskButtons() { return taskButtons; }
        public Bounds backButton() { return backButton; }
        public int centerX() { return centerX; }
        public int titleY() { return titleY; }
        public int activeStatusY() { return activeStatusY; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Layout)) return false;
            Layout value = (Layout) other;
            return centerX == value.centerX && titleY == value.titleY
                    && activeStatusY == value.activeStatusY
                    && Objects.equals(taskButtons, value.taskButtons)
                    && Objects.equals(backButton, value.backButton);
        }

        @Override public int hashCode() {
            return Objects.hash(taskButtons, backButton, centerX, titleY, activeStatusY);
        }
    }

    public static final class Bounds {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        public Bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int width() { return width; }
        public int height() { return height; }
        public int right() { return x + width; }
        public int bottom() { return y + height; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Bounds)) return false;
            Bounds value = (Bounds) other;
            return x == value.x && y == value.y
                    && width == value.width && height == value.height;
        }

        @Override public int hashCode() {
            return Objects.hash(x, y, width, height);
        }
    }
}
