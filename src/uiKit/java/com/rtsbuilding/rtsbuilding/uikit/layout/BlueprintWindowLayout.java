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
}
