package com.rtsbuilding.rtsbuilding.uikit.layout;

/** 指南窗口内容区的正式几何公式。 */
public final class GuideWindowLayout {
    public static final int DEFAULT_W = 330;
    public static final int DEFAULT_H = 198;
    public static final int MIN_W = 250;
    public static final int MIN_H = 158;
    public static final int CONTENT_PAD = 8;

    private GuideWindowLayout() {
    }

    public static int topicTabWidth(boolean bottomContext) { return bottomContext ? 92 : 20; }
    public static int topicAreaHeight(int panelHeight) { return Math.max(18, panelHeight - CONTENT_PAD * 2); }
    public static int visibleTopicRows(int panelHeight) { return Math.max(1, topicAreaHeight(panelHeight) / 22); }
    public static int textAreaHeight(int panelHeight) { return Math.max(24, panelHeight - 36); }
    public static int textMaxWidth(int panelWidth, int tabWidth) { return Math.max(48, panelWidth - tabWidth - 42); }
    public static int visibleTextLines(int panelHeight) { return Math.max(1, textAreaHeight(panelHeight) / 12); }

    /** 指南窗口/内容区的无平台整数矩形。 */
    public static final class Rect {
        public final int x, y, w, h;

        public Rect(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }
}
