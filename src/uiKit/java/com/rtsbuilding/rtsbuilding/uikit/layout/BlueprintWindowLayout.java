package com.rtsbuilding.rtsbuilding.uikit.layout;

/** BlueprintWindowPanel 生产尺寸与行布局的 Java 8 描述；不拥有蓝图业务状态。 */
public final class BlueprintWindowLayout {
    public static final int PLACEMENT_W = 248;
    public static final int PLACEMENT_H = 312;
    public static final int CAPTURE_W = 324;
    public static final int CAPTURE_H = 160;
    public static final int PAD = 12;
    public static final int GAP = 8;
    public static final int CONTROL_GAP = 4;
    public static final int SECTION_PAD = 8;
    public static final int BUTTON_H = 20;
    public static final int SMALL_BUTTON_W = 18;
    public static final int POSITION_INPUT_W = 64;
    public static final int DETAILS_BUTTON_W = 58;
    public static final int STATUS_H = 34;
    public static final int SELECTOR_H = 56;
    public static final int POSITION_H = 106;
    public static final int MATERIAL_W = 560;
    public static final int MATERIAL_H = 340;
    public static final int NAME_W = 420;
    public static final int NAME_H = 146;
    public static final int DIALOG_HORIZONTAL_PAD = 10;
    public static final int NAME_BUTTON_H = 14;
    public static final int NAME_CONFIRM_W = 70;
    public static final int NAME_CANCEL_W = 58;
    public static final int NAME_BUTTON_GAP = 6;
    public static final int MATERIAL_ROW_H = 22;
    public static final int MATERIAL_COLUMN_GAP = 6;
    public static final int CAPTURE_HINT_TOP = 14;
    public static final int CAPTURE_SCROLL_HINT_TOP = 26;
    public static final int CAPTURE_SIZE_TOP = 42;
    public static final int SELECTOR_CONTENT_TOP = 8;
    public static final int POSITION_TITLE_TOP = 6;
    public static final int POSITION_ROWS_TOP = 22;
    public static final int SELECTOR_NAME_TEXT_TOP = 7;
    public static final int SELECTOR_SIZE_TEXT_TOP = 32;
    public static final int SELECTOR_DETAILS_TOP = 27;
    public static final int STATUS_TEXT_HORIZONTAL_INSET = 12;
    public static final int PRIMARY_BUTTON_TEXT_INSET = 10;
    public static final int NAME_SUMMARY_TOP = 10;
    public static final int NAME_SUMMARY_TEXT_INSET = 10;
    public static final int NAME_SUMMARY_LINE_STEP = 12;
    public static final int NAME_INPUT_LABEL_GAP = 11;
    public static final int NAME_INPUT_H = 18;
    public static final int NAME_INPUT_TEXT_INSET = 4;
    public static final int NAME_INPUT_TEXT_TOP = 5;
    public static final int NAME_BUTTON_TEXT_INSET = 6;
    public static final int NAME_BUTTON_TEXT_TOP = 3;

    private BlueprintWindowLayout() {
    }

    public static Geometry geometry(boolean capture, int contentX, int contentY,
                                    int contentWidth, int contentHeight) {
        int x = contentX + PAD;
        int y = contentY + 8;
        int width = Math.max(1, contentWidth - PAD * 2);
        int footerY = contentY + contentHeight - BUTTON_H - 8;
        int actionY = contentY + contentHeight - BUTTON_H * 2 - CONTROL_GAP - 8;
        int statusY = (capture ? footerY : actionY) - STATUS_H - 8;
        return new Geometry(x, y, width, footerY, actionY, statusY);
    }

    /** 命名浮窗的生产绘制、命中和离屏预览共用同一组矩形。 */
    public static NameDialogGeometry nameDialog(int x, int y, int width, int height) {
        int inputX = x + DIALOG_HORIZONTAL_PAD;
        int inputW = Math.max(80, width - DIALOG_HORIZONTAL_PAD * 2);
        int buttonY = y + height - 24;
        int inputY = Math.max(y + 36, buttonY - 28);
        int cancelX = x + width - NAME_CANCEL_W - DIALOG_HORIZONTAL_PAD;
        int confirmX = cancelX - NAME_CONFIRM_W - NAME_BUTTON_GAP;
        return new NameDialogGeometry(inputX, inputY, inputW, confirmX,
                cancelX, buttonY);
    }

    /** 材料浮窗的列表视口；列数、绘制和滚动都消费这一个结果。 */
    public static MaterialDialogGeometry materialDialog(int x, int y, int width, int height) {
        int safeW = Math.max(300, width);
        int safeH = Math.max(150, height);
        int listX = x + DIALOG_HORIZONTAL_PAD;
        int listY = y + 38;
        int listW = safeW - DIALOG_HORIZONTAL_PAD * 2;
        int listH = Math.max(44, safeH - 46);
        return new MaterialDialogGeometry(x, y, safeW, safeH, listX, listY, listW, listH);
    }

    public static final class Geometry {
        public final int x, y, width, footerY, actionY, statusY;

        private Geometry(int x, int y, int width, int footerY, int actionY, int statusY) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.footerY = footerY;
            this.actionY = actionY;
            this.statusY = statusY;
        }
    }

    public static final class NameDialogGeometry {
        public final int inputX, inputY, inputW, confirmX, cancelX, buttonY;

        private NameDialogGeometry(int inputX, int inputY, int inputW,
                                   int confirmX, int cancelX, int buttonY) {
            this.inputX = inputX;
            this.inputY = inputY;
            this.inputW = inputW;
            this.confirmX = confirmX;
            this.cancelX = cancelX;
            this.buttonY = buttonY;
        }
    }

    public static final class MaterialDialogGeometry {
        public final int x, y, width, height, listX, listY, listW, listH;

        private MaterialDialogGeometry(int x, int y, int width, int height,
                                       int listX, int listY, int listW, int listH) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.listX = listX;
            this.listY = listY;
            this.listW = listW;
            this.listH = listH;
        }

        public int columns() {
            return listW >= 390 ? 2 : 1;
        }
    }
}
