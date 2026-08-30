package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/**
 * 原版容器 Overlay 合成数量对话框的固定几何。
 *
 * <p>它保留旧对话框 238px 宽度，并把高度扩到 196px，避免帮助文字与底部动作重叠。
 * 不替代 RTS 浮窗使用的
 * {@link CraftQuantityWindowLayout}。两者入口和生命周期不同，只共享主题与半开命中原则。</p>
 */
public final class CraftQuantityDialogLayout {
    public static final int PANEL_W = 238;
    public static final int PANEL_H = 196;
    public static final int TITLE_H = 20;
    public static final int CLOSE_SIZE = 14;
    public static final int OPTION_VISIBLE_ROWS = 4;
    public static final int OPTION_ROW_H = 16;
    public static final int INPUT_W = 42;
    public static final int INPUT_H = 14;
    public static final int STEP_W = 24;
    public static final int STEP_H = 14;
    public static final int ACTION_W = 52;
    public static final int ACTION_H = 16;

    private CraftQuantityDialogLayout() {
    }

    public static Layout resolve(int screenWidth, int screenHeight) {
        int panelX = (screenWidth - PANEL_W) / 2;
        int panelY = (screenHeight - PANEL_H) / 2;
        int closeX = panelX + PANEL_W - CLOSE_SIZE - 4;
        int closeY = panelY + 3;
        int optionsX = panelX + 8;
        int optionsY = panelY + 50;
        int optionsW = PANEL_W - 16;
        int optionsH = OPTION_VISIBLE_ROWS * OPTION_ROW_H + 4;
        int detailY = optionsY + optionsH + 8;
        int inputY = detailY + 14;
        int minusTenX = panelX + 8;
        int minusOneX = minusTenX + STEP_W + 4;
        int inputX = minusOneX + STEP_W + 6;
        int plusOneX = inputX + INPUT_W + 6;
        int plusTenX = plusOneX + STEP_W + 4;
        int helpY = inputY + 20;
        int actionY = panelY + PANEL_H - ACTION_H - 8;
        int cancelX = panelX + PANEL_W - ACTION_W * 2 - 12;
        int confirmX = panelX + PANEL_W - ACTION_W - 8;
        return new Layout(panelX, panelY, closeX, closeY, optionsX, optionsY,
                optionsW, optionsH, detailY, inputY, minusTenX, minusOneX,
                inputX, plusOneX, plusTenX, helpY, actionY, cancelX, confirmX);
    }

    public static int optionIndexAt(Layout layout, int scroll, int optionCount,
                                    double mouseX, double mouseY) {
        if (layout == null || !UiRect.contains(layout.optionsX, layout.optionsY,
                layout.optionsW, layout.optionsH, mouseX, mouseY)) {
            return -1;
        }
        int localY = (int) (mouseY - layout.optionsY) - 2;
        if (localY < 0) {
            return -1;
        }
        int row = localY / OPTION_ROW_H;
        if (row < 0 || row >= OPTION_VISIBLE_ROWS) {
            return -1;
        }
        int index = scroll + row;
        return index >= 0 && index < optionCount ? index : -1;
    }

    /**
     * 统一解析数量对话框的鼠标目标，保证绘制坐标与输入坐标只由本布局维护。
     */
    public static Hit hitAt(Layout layout, int scroll, int optionCount,
                            double mouseX, double mouseY) {
        if (layout == null || !UiRect.contains(layout.panelX, layout.panelY,
                PANEL_W, PANEL_H, mouseX, mouseY)) {
            return Hit.outsidePanel();
        }
        if (UiRect.contains(layout.closeX, layout.closeY, CLOSE_SIZE, CLOSE_SIZE,
                mouseX, mouseY)) {
            return Hit.control(Control.CLOSE);
        }
        int optionIndex = optionIndexAt(layout, scroll, optionCount, mouseX, mouseY);
        if (optionIndex >= 0) {
            return Hit.option(optionIndex);
        }
        if (UiRect.contains(layout.minusTenX, layout.inputY, STEP_W, STEP_H,
                mouseX, mouseY)) {
            return Hit.control(Control.MINUS_TEN);
        }
        if (UiRect.contains(layout.minusOneX, layout.inputY, STEP_W, STEP_H,
                mouseX, mouseY)) {
            return Hit.control(Control.MINUS_ONE);
        }
        if (UiRect.contains(layout.plusOneX, layout.inputY, STEP_W, STEP_H,
                mouseX, mouseY)) {
            return Hit.control(Control.PLUS_ONE);
        }
        if (UiRect.contains(layout.plusTenX, layout.inputY, STEP_W, STEP_H,
                mouseX, mouseY)) {
            return Hit.control(Control.PLUS_TEN);
        }
        if (UiRect.contains(layout.cancelX, layout.actionY, ACTION_W, ACTION_H,
                mouseX, mouseY)) {
            return Hit.control(Control.CANCEL);
        }
        if (UiRect.contains(layout.confirmX, layout.actionY, ACTION_W, ACTION_H,
                mouseX, mouseY)) {
            return Hit.control(Control.CONFIRM);
        }
        return Hit.control(Control.NONE);
    }

    public enum Control {
        OUTSIDE_PANEL,
        CLOSE,
        OPTION,
        MINUS_TEN,
        MINUS_ONE,
        PLUS_ONE,
        PLUS_TEN,
        CANCEL,
        CONFIRM,
        NONE
    }

    public static final class Hit {
        private final Control control;
        private final int optionIndex;

        private Hit(Control control, int optionIndex) {
            this.control = control;
            this.optionIndex = optionIndex;
        }

        private static Hit outsidePanel() {
            return new Hit(Control.OUTSIDE_PANEL, -1);
        }

        private static Hit control(Control control) {
            return new Hit(control, -1);
        }

        private static Hit option(int optionIndex) {
            return new Hit(Control.OPTION, optionIndex);
        }

        public Control control() {
            return control;
        }

        public int optionIndex() {
            return optionIndex;
        }
    }

    public static final class Layout {
        public final int panelX, panelY, closeX, closeY;
        public final int optionsX, optionsY, optionsW, optionsH;
        public final int detailY, inputY;
        public final int minusTenX, minusOneX, inputX, plusOneX, plusTenX;
        public final int helpY, actionY, cancelX, confirmX;

        Layout(int panelX, int panelY, int closeX, int closeY,
               int optionsX, int optionsY, int optionsW, int optionsH,
               int detailY, int inputY, int minusTenX, int minusOneX,
               int inputX, int plusOneX, int plusTenX, int helpY,
               int actionY, int cancelX, int confirmX) {
            this.panelX = panelX;
            this.panelY = panelY;
            this.closeX = closeX;
            this.closeY = closeY;
            this.optionsX = optionsX;
            this.optionsY = optionsY;
            this.optionsW = optionsW;
            this.optionsH = optionsH;
            this.detailY = detailY;
            this.inputY = inputY;
            this.minusTenX = minusTenX;
            this.minusOneX = minusOneX;
            this.inputX = inputX;
            this.plusOneX = plusOneX;
            this.plusTenX = plusTenX;
            this.helpY = helpY;
            this.actionY = actionY;
            this.cancelX = cancelX;
            this.confirmX = confirmX;
        }

        public int panelX() { return panelX; }
        public int panelY() { return panelY; }
        public int closeX() { return closeX; }
        public int closeY() { return closeY; }
        public int optionsX() { return optionsX; }
        public int optionsY() { return optionsY; }
        public int optionsW() { return optionsW; }
        public int optionsH() { return optionsH; }
        public int detailY() { return detailY; }
        public int inputY() { return inputY; }
        public int minusTenX() { return minusTenX; }
        public int minusOneX() { return minusOneX; }
        public int inputX() { return inputX; }
        public int plusOneX() { return plusOneX; }
        public int plusTenX() { return plusTenX; }
        public int helpY() { return helpY; }
        public int actionY() { return actionY; }
        public int cancelX() { return cancelX; }
        public int confirmX() { return confirmX; }
    }
}
