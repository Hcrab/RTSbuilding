package com.rtsbuilding.rtsbuilding.uikit.layout;

/**
 * 底栏排序、排序方向与面板高度控制的纯 Java 几何。
 *
 * <p>本类同时拥有四个按钮的绘制矩形和半开命中规则。它不决定排序类型，也不直接修改
 * 面板高度，生产输入只根据返回的 {@link Control} 派发对应动作。</p>
 */
public final class BottomPanelSortLayout {
    public static final int BUTTON_SIZE = RtsMainlineLayout.SORT_BUTTON_SIZE;
    public static final int ROW_GAP = RtsMainlineLayout.SORT_BUTTON_GAP;
    public static final int HEIGHT_CONTROL_X_OFFSET = BUTTON_SIZE + 26;
    public static final int LABEL_GAP = 4;

    public final Area cycleSort;
    public final Area toggleDirection;
    public final Area increaseHeight;
    public final Area decreaseHeight;

    private BottomPanelSortLayout(int x, int y) {
        int secondRowY = y + BUTTON_SIZE + ROW_GAP;
        int heightX = x + HEIGHT_CONTROL_X_OFFSET;
        this.cycleSort = new Area(x, y, BUTTON_SIZE, BUTTON_SIZE);
        this.toggleDirection = new Area(x, secondRowY, BUTTON_SIZE, BUTTON_SIZE);
        this.increaseHeight = new Area(heightX, y, BUTTON_SIZE, BUTTON_SIZE);
        this.decreaseHeight = new Area(heightX, secondRowY, BUTTON_SIZE, BUTTON_SIZE);
    }

    public static BottomPanelSortLayout resolve(int x, int y) {
        return new BottomPanelSortLayout(x, y);
    }

    public int labelX() {
        return cycleSort.x + BUTTON_SIZE + LABEL_GAP;
    }

    public int labelY() {
        return cycleSort.y + 6;
    }

    public Control controlAt(double mouseX, double mouseY) {
        if (cycleSort.contains(mouseX, mouseY)) {
            return Control.CYCLE_SORT;
        }
        if (toggleDirection.contains(mouseX, mouseY)) {
            return Control.TOGGLE_DIRECTION;
        }
        if (increaseHeight.contains(mouseX, mouseY)) {
            return Control.INCREASE_HEIGHT;
        }
        if (decreaseHeight.contains(mouseX, mouseY)) {
            return Control.DECREASE_HEIGHT;
        }
        return null;
    }

    public enum Control {
        CYCLE_SORT,
        TOGGLE_DIRECTION,
        INCREASE_HEIGHT,
        DECREASE_HEIGHT
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
