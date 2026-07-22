package com.rtsbuilding.rtsbuilding.uikit.layout;

/** 合成数量窗生产/离屏共用几何。 */
public final class CraftQuantityWindowLayout {
    public static final int DEFAULT_W = 238;
    public static final int DEFAULT_H = 196;
    public static final int MIN_W = 220;
    public static final int MIN_H = 176;
    public static final int OPTION_ROW_H = 16;
    public static final int INPUT_W = 42;
    public static final int INPUT_H = 14;
    public static final int STEP_W = 24;
    public static final int STEP_H = 14;
    public static final int ACTION_W = 52;
    public static final int ACTION_H = 16;

    private CraftQuantityWindowLayout() {
    }

    public static Layout resolve(int contentX, int contentY, int contentWidth, int contentHeight) {
        int x = contentX + 8;
        int y = contentY + 7;
        int w = Math.max(1, contentWidth - 16);
        int actionY = contentY + contentHeight - ACTION_H - 8;
        int helpY = actionY - 14;
        int inputY = helpY - 18;
        int detailY = inputY - 14;
        int optionsY = y + 40;
        int optionsH = Math.max(OPTION_ROW_H + 4, detailY - optionsY - 8);
        int controlsW = STEP_W * 4 + INPUT_W + 24;
        int controlsX = x + Math.max(0, (w - controlsW) / 2);
        int minusTenX = controlsX;
        int minusOneX = minusTenX + STEP_W + 4;
        int inputX = minusOneX + STEP_W + 6;
        int plusOneX = inputX + INPUT_W + 6;
        int plusTenX = plusOneX + STEP_W + 4;
        int cancelX = x + w - ACTION_W * 2 - 4;
        int confirmX = x + w - ACTION_W;
        return new Layout(x, y, w, optionsY, w, optionsH, detailY, inputY,
                minusTenX, minusOneX, inputX, plusOneX, plusTenX,
                helpY, actionY, cancelX, confirmX);
    }

    public static int visibleOptionRows(Layout layout) {
        return Math.max(1, (layout.optionsH - 4) / OPTION_ROW_H);
    }

    public static final class Layout {
        public final int x, y, w, optionsY, optionsW, optionsH, detailY, inputY;
        public final int minusTenX, minusOneX, inputX, plusOneX, plusTenX;
        public final int helpY, actionY, cancelX, confirmX;

        Layout(int x, int y, int w, int optionsY, int optionsW, int optionsH,
               int detailY, int inputY, int minusTenX, int minusOneX, int inputX,
               int plusOneX, int plusTenX, int helpY, int actionY,
               int cancelX, int confirmX) {
            this.x = x; this.y = y; this.w = w;
            this.optionsY = optionsY; this.optionsW = optionsW; this.optionsH = optionsH;
            this.detailY = detailY; this.inputY = inputY;
            this.minusTenX = minusTenX; this.minusOneX = minusOneX; this.inputX = inputX;
            this.plusOneX = plusOneX; this.plusTenX = plusTenX;
            this.helpY = helpY; this.actionY = actionY;
            this.cancelX = cancelX; this.confirmX = confirmX;
        }
    }
}
