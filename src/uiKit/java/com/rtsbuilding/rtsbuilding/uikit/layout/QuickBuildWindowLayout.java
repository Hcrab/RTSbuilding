package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;

/**
 * 生产 QuickBuildPanel 的精确纯 Java 几何描述。
 *
 * <p>只拥有窗口/模式行/两列形状/右栏控件和底部信息区坐标与半开命中，不拥有玩法状态、
 * Minecraft 控件或贴图绘制。生产和离屏共同使用后，预览不再用错误的四列形状布局，
 * 模式按钮也不会在绘制和输入侧各自重算边界。</p>
 */
public final class QuickBuildWindowLayout {
    public static final int WINDOW_W = 178;
    public static final int BUILD_BASE_H = 260;
    public static final int DESTROY_BASE_H = 260;
    public static final int BOTTOM_INFO_H = 72;
    public static final int TITLE_H = 20;
    public static final int MODE_H = 18;
    public static final int MODE_GAP = 4;
    public static final int MODE_TOP = 5;
    public static final int SECTION_TOP = 31;
    public static final int CONTROL_LIST_TOP = SECTION_TOP + 15;
    public static final int CHAIN_LABEL_TOP = SECTION_TOP + 17;
    public static final int CHAIN_SLIDER_GAP = 14;
    public static final int CHAIN_SLIDER_H = 18;
    public static final int CHAIN_VALUE_GAP = 6;
    public static final int CHAIN_VALUE_Y_OFFSET = 2;
    public static final int CHAIN_SLIDER_MIN_W = 50;
    public static final int CHAIN_SLIDER_RIGHT_RESERVE = 40;
    public static final int SHAPE_SLOT = 32;
    public static final int SHAPE_GAP = 8;
    public static final int SHAPE_ROW_PITCH = 38;
    public static final int RIGHT_COL_X = 88;
    public static final int CONTROL_W = 84;
    public static final int CONTROL_H = 20;
    public static final int CONTROL_ICON_INSET = 2;
    public static final int CONTROL_ICON_SIZE = 16;
    public static final int SHAPE_SELECTED_INSET = 2;
    public static final int MODE_LABEL_MIN_INSET = 2;
    public static final int DIVIDER_INSET = 6;
    public static final int PROGRESS_INSET = 8;
    public static final int PROGRESS_TOP = 4;
    public static final int PROGRESS_H = 4;
    public static final int CONTENT_INSET = 8;
    public static final int SECTION_LABEL_INSET = 10;
    public static final int INFO_LINE_GAP = 4;
    public static final int INFO_FOLLOWUP_GAP = 3;
    public static final int ITEM_GAP = 4;
    public static final int ITEM_SIZE = 16;
    public static final int STATUS_TEXT_TOP = 12;
    public static final int STATUS_ITEM_Y_OFFSET = -4;
    public static final int STATUS_MISSING_TEXT_GAP = 8;
    public static final int STATUS_MISSING_ICON_GAP = 4;
    public static final int STATUS_TEXT_MAX_LINES = 3;
    public static final int DEFAULT_TOP_GAP = 40;
    public static final int WINDOW_RIGHT_GAP = 4;
    public static final int TOOLTIP_POINTER_GAP = 10;
    public static final int TOOLTIP_SCREEN_MARGIN = 5;

    private QuickBuildWindowLayout() {}

    public static int windowHeight(boolean destroy) {
        return (destroy ? DESTROY_BASE_H : BUILD_BASE_H) + BOTTOM_INFO_H;
    }

    public static int chainSliderWidth(int windowWidth) {
        return Math.max(CHAIN_SLIDER_MIN_W,
                windowWidth - RIGHT_COL_X - CHAIN_SLIDER_RIGHT_RESERVE);
    }

    public static int defaultX(int screenWidth) {
        return screenWidth - WINDOW_W - WINDOW_RIGHT_GAP;
    }

    public static int defaultY(int topBarBottom) {
        return topBarBottom + DEFAULT_TOP_GAP;
    }

    public static Geometry geometry(int windowX, int windowY, boolean destroy) {
        int bodyY = windowY + TITLE_H;
        int totalW = WINDOW_W - CONTENT_INSET * 2;
        int modeW = (totalW - MODE_GAP) / 2;
        return new Geometry(windowX, windowY, bodyY,
                windowX + CONTENT_INSET,
                windowX + CONTENT_INSET + modeW + MODE_GAP,
                bodyY + MODE_TOP, modeW,
                bodyY + SECTION_TOP,
                windowX + RIGHT_COL_X,
                windowY + (destroy ? DESTROY_BASE_H : BUILD_BASE_H),
                windowHeight(destroy));
    }

    /** Tooltip 使用 RTS 虚拟视口坐标定位，不能交给外层 Minecraft GUI 视口再次换算。 */
    public static UiRect tooltipBounds(int screenWidth, int screenHeight,
                                       int pointerX, int pointerY,
                                       int tooltipWidth, int tooltipHeight) {
        int width = Math.max(1, tooltipWidth);
        int height = Math.max(1, tooltipHeight);
        int x = pointerX + TOOLTIP_POINTER_GAP;
        int y = pointerY + TOOLTIP_POINTER_GAP;
        if (x + width > screenWidth - TOOLTIP_SCREEN_MARGIN) {
            x = pointerX - TOOLTIP_POINTER_GAP - width;
        }
        if (y + height > screenHeight - TOOLTIP_SCREEN_MARGIN) {
            y = pointerY - TOOLTIP_POINTER_GAP - height;
        }
        x = Math.max(TOOLTIP_SCREEN_MARGIN,
                Math.min(x, Math.max(TOOLTIP_SCREEN_MARGIN,
                        screenWidth - TOOLTIP_SCREEN_MARGIN - width)));
        y = Math.max(TOOLTIP_SCREEN_MARGIN,
                Math.min(y, Math.max(TOOLTIP_SCREEN_MARGIN,
                        screenHeight - TOOLTIP_SCREEN_MARGIN - height)));
        return new UiRect(x, y, width, height);
    }

    public static final class Geometry {
        public final int windowX, windowY, bodyY;
        public final int buildModeX, destroyModeX, modeY, modeW;
        public final int sectionTitleY, rightX, dividerY, windowH;
        public final int chainLabelY, chainSliderY, statusTextY, statusItemY;
        public final int contentX, contentW, sectionLabelX;
        public final UiRect buildMode, destroyMode, divider, progress;

        private Geometry(int windowX,int windowY,int bodyY,int buildModeX,int destroyModeX,
                         int modeY,int modeW,int sectionTitleY,int rightX,int dividerY,int windowH) {
            this.windowX=windowX; this.windowY=windowY; this.bodyY=bodyY;
            this.buildModeX=buildModeX; this.destroyModeX=destroyModeX;
            this.modeY=modeY; this.modeW=modeW; this.sectionTitleY=sectionTitleY;
            this.rightX=rightX; this.dividerY=dividerY; this.windowH=windowH;
            this.chainLabelY = bodyY + CHAIN_LABEL_TOP;
            this.chainSliderY = chainLabelY + CHAIN_SLIDER_GAP;
            this.statusTextY = dividerY + STATUS_TEXT_TOP;
            this.statusItemY = statusTextY + STATUS_ITEM_Y_OFFSET;
            this.contentX = windowX + CONTENT_INSET;
            this.contentW = WINDOW_W - CONTENT_INSET * 2;
            this.sectionLabelX = windowX + SECTION_LABEL_INSET;
            this.buildMode = new UiRect(buildModeX, modeY, modeW, MODE_H);
            this.destroyMode = new UiRect(destroyModeX, modeY, modeW, MODE_H);
            this.divider = new UiRect(
                    windowX + DIVIDER_INSET, dividerY - 1,
                    WINDOW_W - DIVIDER_INSET * 2, 1);
            this.progress = new UiRect(
                    windowX + PROGRESS_INSET, dividerY + PROGRESS_TOP,
                    WINDOW_W - PROGRESS_INSET * 2, PROGRESS_H);
        }

        public int shapeX(int index) {
            return contentX + (index % 2) * (SHAPE_SLOT + SHAPE_GAP);
        }
        public int shapeY(int index){return bodyY + CONTROL_LIST_TOP + (index / 2) * SHAPE_ROW_PITCH;}
        public int controlY(int index){return bodyY + CONTROL_LIST_TOP + index * SHAPE_ROW_PITCH;}

        public int chainValueX(int sliderWidth) {
            return rightX + sliderWidth + CHAIN_VALUE_GAP;
        }

        public int missingTextX(int contentRightEdge) {
            return contentRightEdge + STATUS_MISSING_TEXT_GAP;
        }

        public int missingIconX(int missingTextX, int missingTextWidth) {
            return missingTextX + missingTextWidth + STATUS_MISSING_ICON_GAP;
        }

        public UiRect modeArea(QuickBuildUiMode mode) {
            return mode == QuickBuildUiMode.DESTROY ? destroyMode : buildMode;
        }

        /** 模式按钮使用半开边界；两个按钮之间的空隙不会误触任意模式。 */
        public QuickBuildUiMode modeAt(double mouseX, double mouseY) {
            if (buildMode.contains(mouseX, mouseY)) {
                return QuickBuildUiMode.BUILD;
            }
            return destroyMode.contains(mouseX, mouseY)
                    ? QuickBuildUiMode.DESTROY
                    : null;
        }
    }
}
