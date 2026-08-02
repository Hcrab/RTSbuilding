package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiContext;

/**
 * 指南窗口内容区的正式几何与输入路由。
 *
 * <p>本类只计算主题行、正文、滚动条和首次开窗位置；不持有主题目录、滚动状态或
 * Minecraft 字体。生产窗口和离屏预览必须使用同一份 {@link Geometry}，避免绘制
 * 与点击/滚轮区域各自维护偏移。</p>
 */
public final class GuideWindowLayout {
    public static final int DEFAULT_W = 330;
    public static final int DEFAULT_H = 198;
    public static final int MIN_W = 250;
    public static final int MIN_H = 158;
    public static final int CONTENT_PAD = 8;
    public static final int EDGE_MARGIN = 8;
    public static final int TOP_GAP = 6;
    public static final int TITLE_TEXT_OFFSET_Y = 10;
    public static final int BODY_SCROLLBAR_RIGHT_INSET = 8;
    public static final int TOPIC_ROW_H = 18;
    public static final int TOPIC_ROW_STEP = 22;
    public static final int TOPIC_SCROLLBAR_GAP = 3;
    public static final int TOPIC_SCROLL_ROUTE_EXTRA_W = 8;
    public static final int TOPIC_ICON_CENTER_X = 10;
    public static final int TOPIC_ICON_CENTER_Y = 9;
    public static final int TOPIC_LABEL_INSET_X = 4;
    public static final int TOPIC_LABEL_TEXT_Y = 5;
    public static final int TOPIC_LABEL_BASELINE_Y = 13;
    public static final int TOPIC_LABEL_HORIZONTAL_PAD = 8;
    public static final int TEXT_LEFT_GAP = 18;
    public static final int TITLE_BASELINE_Y = 9;
    public static final int BODY_TOP_GAP = 16;
    public static final int BODY_LINE_H = 12;
    public static final int BODY_BASELINE_Y = 9;
    public static final int SCROLLBAR_W = 3;
    public static final int SCROLLBAR_MIN_KNOB_H = 10;
    private static final int OPENING_WIDTH_RESERVE = 28;
    private static final int OPENING_HEIGHT_RESERVE = 90;
    private static final int SETTINGS_MAX_W = 300;
    private static final int SETTINGS_HORIZONTAL_RESERVE = 24;
    private static final int SETTINGS_SIDE_MIN_W = 230;
    private static final int SETTINGS_COMPACT_MIN_W = 220;

    private GuideWindowLayout() {
    }

    public static int topicTabWidth(boolean bottomContext) { return bottomContext ? 92 : 20; }
    public static int topicAreaHeight(int panelHeight) { return Math.max(18, panelHeight - CONTENT_PAD * 2); }
    public static int visibleTopicRows(int panelHeight) {
        return Math.max(1, topicAreaHeight(panelHeight) / TOPIC_ROW_STEP);
    }
    public static int textAreaHeight(int panelHeight) { return Math.max(24, panelHeight - 36); }
    public static int textMaxWidth(int panelWidth, int tabWidth) { return Math.max(48, panelWidth - tabWidth - 42); }
    public static int visibleTextLines(int panelHeight) {
        return Math.max(1, textAreaHeight(panelHeight) / BODY_LINE_H);
    }

    public static Geometry geometry(UiRect content, boolean bottomContext) {
        if (content == null) {
            throw new IllegalArgumentException("content");
        }
        int tabWidth = topicTabWidth(bottomContext);
        double tabX = content.getX() + CONTENT_PAD;
        double tabY = content.getY() + CONTENT_PAD;
        int topicHeight = topicAreaHeight((int) content.getHeight());
        int maxTextWidth = textMaxWidth((int) content.getWidth(), tabWidth);
        double textX = content.getX() + tabWidth + TEXT_LEFT_GAP;
        double titleY = content.getY() + TITLE_TEXT_OFFSET_Y;
        double bodyY = titleY + BODY_TOP_GAP;
        int bodyHeight = textAreaHeight((int) content.getHeight());
        return new Geometry(
                content,
                new UiRect(tabX, tabY, tabWidth, topicHeight),
                new UiRect(tabX, tabY, tabWidth + TOPIC_SCROLL_ROUTE_EXTRA_W, topicHeight),
                new UiRect(tabX + tabWidth + TOPIC_SCROLLBAR_GAP, tabY,
                        SCROLLBAR_W, topicHeight),
                new UiRect(textX, titleY, maxTextWidth, TITLE_BASELINE_Y + 1),
                new UiRect(textX, bodyY, maxTextWidth, bodyHeight),
                new UiRect(content.right() - BODY_SCROLLBAR_RIGHT_INSET, bodyY,
                        SCROLLBAR_W, bodyHeight),
                tabWidth,
                visibleTopicRows((int) content.getHeight()),
                visibleTextLines((int) content.getHeight()));
    }

    public static Hit hitAt(Geometry geometry, double mouseX, double mouseY,
                            int topicScroll, int topicCount) {
        if (geometry == null) {
            throw new IllegalArgumentException("geometry");
        }
        if (!geometry.content.contains(mouseX, mouseY)) {
            return Hit.NONE;
        }
        int safeScroll = Math.max(0, topicScroll);
        int end = Math.min(Math.max(0, topicCount), safeScroll + geometry.visibleTopicRows);
        for (int index = safeScroll; index < end; index++) {
            if (geometry.topicRow(index, safeScroll).contains(mouseX, mouseY)) {
                return new Hit(Target.TOPIC, index);
            }
        }
        return geometry.topicScrollRoute.contains(mouseX, mouseY)
                ? new Hit(Target.TOPIC_SCROLL, -1)
                : new Hit(Target.TEXT_SCROLL, -1);
    }

    public static int openingWidth(int screenWidth) {
        return Math.min(DEFAULT_W, Math.max(MIN_W, screenWidth - OPENING_WIDTH_RESERVE));
    }

    public static int openingHeight(int screenHeight) {
        return Math.min(DEFAULT_H, Math.max(MIN_H, screenHeight - OPENING_HEIGHT_RESERVE));
    }

    /** 统一计算 TOP/BOTTOM/SETTINGS 三种上下文的首次开窗位置。 */
    public static Rect openingRect(GuideUiContext context,
                                   int screenWidth, int screenHeight,
                                   int panelWidth, int panelHeight,
                                   int anchorX, int anchorY,
                                   int topHeight, int bottomY, int settingsHeight) {
        if (context == null) throw new IllegalArgumentException("context");
        boolean anchored = anchorX >= 0 && anchorY >= 0;
        int x;
        int y;
        if (context == GuideUiContext.BOTTOM) {
            if (anchored) {
                x = clampX(anchorX - panelWidth + 20, panelWidth, screenWidth);
                y = clampY(anchorY - panelHeight - EDGE_MARGIN,
                        panelHeight, screenHeight, topHeight);
            } else {
                x = Math.max(EDGE_MARGIN, screenWidth - panelWidth - EDGE_MARGIN);
                y = Math.max(topHeight + TOP_GAP, bottomY - panelHeight - TOP_GAP);
            }
        } else if (context == GuideUiContext.SETTINGS) {
            int settingsWidth = Math.min(SETTINGS_MAX_W,
                    screenWidth - SETTINGS_HORIZONTAL_RESERVE);
            int settingsX = (screenWidth - settingsWidth) / 2;
            int settingsY = (screenHeight - settingsHeight) / 2;
            int gap = TOP_GAP;
            int leftSpace = Math.max(0, settingsX - EDGE_MARGIN - gap);
            int rightSpace = Math.max(0,
                    screenWidth - (settingsX + settingsWidth) - EDGE_MARGIN - gap);
            if (leftSpace >= SETTINGS_SIDE_MIN_W || rightSpace >= SETTINGS_SIDE_MIN_W) {
                boolean useLeft = leftSpace >= rightSpace;
                panelWidth = Math.min(DEFAULT_W, useLeft ? leftSpace : rightSpace);
                x = useLeft ? settingsX - gap - panelWidth : settingsX + settingsWidth + gap;
                y = clamp(settingsY, EDGE_MARGIN,
                        Math.max(EDGE_MARGIN, screenHeight - panelHeight - EDGE_MARGIN));
            } else {
                panelWidth = Math.min(DEFAULT_W,
                        Math.max(SETTINGS_COMPACT_MIN_W, screenWidth - EDGE_MARGIN * 2));
                x = Math.max(EDGE_MARGIN, (screenWidth - panelWidth) / 2);
                int belowY = settingsY + settingsHeight + gap;
                y = belowY + panelHeight <= screenHeight - EDGE_MARGIN
                        ? belowY
                        : Math.max(EDGE_MARGIN, settingsY - panelHeight - gap);
            }
        } else if (anchored) {
            x = clampX(anchorX - panelWidth / 2, panelWidth, screenWidth);
            y = clampY(anchorY + EDGE_MARGIN, panelHeight, screenHeight, topHeight);
        } else {
            x = EDGE_MARGIN;
            y = topHeight + TOP_GAP;
        }
        return new Rect(x, y, panelWidth, panelHeight);
    }

    private static int clampX(int x, int panelWidth, int screenWidth) {
        return clamp(x, EDGE_MARGIN,
                Math.max(EDGE_MARGIN, screenWidth - panelWidth - EDGE_MARGIN));
    }

    private static int clampY(int y, int panelHeight, int screenHeight, int topHeight) {
        int minY = topHeight + TOP_GAP;
        return clamp(y, minY, Math.max(minY, screenHeight - panelHeight - EDGE_MARGIN));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** 指南窗口/内容区的无平台整数矩形。 */
    public static final class Rect {
        public final int x, y, w, h;

        public Rect(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }

    public enum Target {
        NONE,
        TOPIC,
        TOPIC_SCROLL,
        TEXT_SCROLL
    }

    /** 一次半开区间命中结果；主题索引只在 {@link Target#TOPIC} 时有效。 */
    public static final class Hit {
        public static final Hit NONE = new Hit(Target.NONE, -1);

        public final Target target;
        public final int topicIndex;

        public Hit(Target target, int topicIndex) {
            if (target == null) {
                throw new IllegalArgumentException("target");
            }
            this.target = target;
            this.topicIndex = topicIndex;
        }
    }

    /** 单帧内容区几何；不含当前主题或滚动位置。 */
    public static final class Geometry {
        public final UiRect content;
        public final UiRect topicArea;
        public final UiRect topicScrollRoute;
        public final UiRect topicScrollbar;
        public final UiRect title;
        public final UiRect body;
        public final UiRect bodyScrollbar;
        public final int topicTabWidth;
        public final int visibleTopicRows;
        public final int visibleTextLines;

        private Geometry(UiRect content, UiRect topicArea, UiRect topicScrollRoute,
                         UiRect topicScrollbar, UiRect title, UiRect body,
                         UiRect bodyScrollbar, int topicTabWidth,
                         int visibleTopicRows, int visibleTextLines) {
            this.content = content;
            this.topicArea = topicArea;
            this.topicScrollRoute = topicScrollRoute;
            this.topicScrollbar = topicScrollbar;
            this.title = title;
            this.body = body;
            this.bodyScrollbar = bodyScrollbar;
            this.topicTabWidth = topicTabWidth;
            this.visibleTopicRows = visibleTopicRows;
            this.visibleTextLines = visibleTextLines;
        }

        public UiRect topicRow(int topicIndex, int topicScroll) {
            int relative = topicIndex - topicScroll;
            return new UiRect(topicArea.getX(),
                    topicArea.getY() + relative * TOPIC_ROW_STEP,
                    topicTabWidth, TOPIC_ROW_H);
        }
    }
}
