package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;

/**
 * 生产 QuickBuildPanel 的精确纯 Java 几何描述。
 *
 * <p>本类只拥有窗口、两种主模式、目录、两列形状/工具、右栏参数和底部信息区的坐标与半开命中。
 * 它不拥有玩法状态、Minecraft 控件或贴图绘制。生产界面和离屏预览共用这些规则后，绘制与输入不会
 * 各自重算边界，也不会把 Smart Fill 误投影成第三个主模式。</p>
 */
public final class QuickBuildWindowLayout {
    public static final int WINDOW_W = 144;
    public static final int BUILD_BASE_H = 208;
    public static final int DESTROY_BASE_H = 230;
    public static final int BOTTOM_INFO_H = 58;
    public static final int TITLE_H = 16;
    public static final int MODE_H = 14;
    public static final int MODE_GAP = 3;
    public static final int MODE_TOP = 4;
    public static final int SECTION_TOP = 25;
    public static final int CATALOG_TOP = SECTION_TOP;
    public static final int CATALOG_H = 14;
    public static final int CATALOG_GAP = 3;
    public static final int CATALOG_TOOLS_GAP = 6;
    public static final int CONTROL_LIST_TOP = SECTION_TOP + 12;
    public static final int CHAIN_LABEL_TOP = SECTION_TOP + 14;
    public static final int CHAIN_SLIDER_GAP = 11;
    public static final int CHAIN_SLIDER_H = 14;
    public static final int CHAIN_VALUE_GAP = 5;
    public static final int CHAIN_VALUE_Y_OFFSET = 1;
    public static final int CHAIN_SLIDER_MIN_W = 40;
    public static final int CHAIN_SLIDER_RIGHT_RESERVE = 32;
    public static final int SHAPE_SLOT = 26;
    public static final int SHAPE_GAP = 6;
    public static final int SHAPE_ROW_PITCH = 30;
    public static final int RIGHT_COL_X = 70;
    public static final int CONTROL_W = 68;
    public static final int CONTROL_H = 16;
    public static final int CONVENIENCE_TOOL_W = SHAPE_SLOT;
    public static final int CONVENIENCE_TOOL_H = SHAPE_SLOT;
    public static final int CONVENIENCE_PARAMETER_LABEL_GAP = 10;
    public static final int CONVENIENCE_PARAMETER_PITCH = 40;
    public static final int CONTROL_ICON_INSET = 2;
    public static final int CONTROL_ICON_SIZE = 12;
    public static final int SHAPE_SELECTED_INSET = 2;
    public static final int MODE_LABEL_MIN_INSET = 2;
    public static final int DIVIDER_INSET = 5;
    public static final int PROGRESS_INSET = 6;
    public static final int PROGRESS_TOP = 3;
    public static final int PROGRESS_H = 3;
    public static final int CONTENT_INSET = 6;
    public static final int SECTION_LABEL_INSET = 8;
    public static final int INFO_LINE_GAP = 3;
    public static final int INFO_FOLLOWUP_GAP = 2;
    public static final int ITEM_GAP = 3;
    public static final int ITEM_SIZE = 16;
    public static final int STATUS_TEXT_TOP = 10;
    public static final int STATUS_ITEM_Y_OFFSET = -3;
    public static final int STATUS_MISSING_TEXT_GAP = 6;
    public static final int STATUS_MISSING_ICON_GAP = 3;
    public static final int STATUS_TEXT_MAX_LINES = 3;
    public static final int DEFAULT_TOP_GAP = 32;
    public static final int WINDOW_RIGHT_GAP = 3;
    public static final int TOOLTIP_POINTER_GAP = 8;
    public static final int TOOLTIP_SCREEN_MARGIN = 4;

    private QuickBuildWindowLayout() {
    }

    /** Quick Build 在两种主模式下都保持同一外框，避免切换时窗口跳动。 */
    public static int windowHeight(boolean destroy) {
        return DESTROY_BASE_H + BOTTOM_INFO_H;
    }

    public static int windowHeight(QuickBuildUiMode mode) {
        return windowHeight(mode == QuickBuildUiMode.DESTROY);
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
        return geometry(windowX, windowY,
                destroy ? QuickBuildUiMode.DESTROY : QuickBuildUiMode.BUILD);
    }

    /**
     * Smart Fill 是 Build 的工具页，不是第三种主模式；传入该内部状态时仍复用 Build 的主框架。
     */
    public static Geometry geometry(int windowX, int windowY, QuickBuildUiMode mode) {
        int bodyY = windowY + TITLE_H;
        int totalW = WINDOW_W - CONTENT_INSET * 2;
        int modeW = (totalW - MODE_GAP) / 2;
        int buildX = windowX + CONTENT_INSET;
        int destroyX = buildX + modeW + MODE_GAP;
        boolean destroy = mode == QuickBuildUiMode.DESTROY;
        return new Geometry(windowX, windowY, bodyY,
                buildX, destroyX,
                bodyY + MODE_TOP, modeW,
                bodyY + SECTION_TOP,
                windowX + RIGHT_COL_X,
                windowY + DESTROY_BASE_H,
                windowHeight(destroy), destroy);
    }

    public static final class Geometry {
        public final int windowX;
        public final int windowY;
        public final int bodyY;
        public final int buildModeX;
        public final int destroyModeX;
        public final int modeY;
        public final int modeW;
        public final int sectionTitleY;
        public final int rightX;
        public final int dividerY;
        public final int windowH;
        public final boolean destroy;
        public final int chainLabelY;
        public final int chainSliderY;
        public final int statusTextY;
        public final int statusItemY;
        public final int catalogY;
        public final int catalogW;
        public final int convenienceContentY;
        public final int contentX;
        public final int contentW;
        public final int sectionLabelX;
        public final UiRect buildMode;
        public final UiRect destroyMode;
        public final UiRect divider;
        public final UiRect progress;

        private Geometry(int windowX, int windowY, int bodyY, int buildModeX,
                int destroyModeX, int modeY, int modeW, int sectionTitleY,
                int rightX, int dividerY, int windowH, boolean destroy) {
            this.windowX = windowX;
            this.windowY = windowY;
            this.bodyY = bodyY;
            this.buildModeX = buildModeX;
            this.destroyModeX = destroyModeX;
            this.modeY = modeY;
            this.modeW = modeW;
            this.sectionTitleY = sectionTitleY;
            this.rightX = rightX;
            this.dividerY = dividerY;
            this.windowH = windowH;
            this.destroy = destroy;
            this.contentX = windowX + CONTENT_INSET;
            this.contentW = WINDOW_W - CONTENT_INSET * 2;
            this.sectionLabelX = windowX + SECTION_LABEL_INSET;
            this.catalogY = bodyY + CATALOG_TOP;
            this.catalogW = (contentW - CATALOG_GAP) / 2;
            this.convenienceContentY = catalogY + CATALOG_H + CATALOG_TOOLS_GAP;
            this.chainLabelY = convenienceContentY;
            this.chainSliderY = chainLabelY + CHAIN_SLIDER_GAP;
            this.statusTextY = dividerY + STATUS_TEXT_TOP;
            this.statusItemY = statusTextY + STATUS_ITEM_Y_OFFSET;
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

        public int shapeY(int index) {
            return convenienceContentY + (index / 2) * SHAPE_ROW_PITCH;
        }

        public int controlY(int index) {
            return convenienceContentY + index * SHAPE_ROW_PITCH;
        }

        public int catalogX(int index) {
            return contentX + index * (catalogW + CATALOG_GAP);
        }

        /** 便利工具与形状共用两列网格，避免第三项挤占右栏参数。 */
        public int convenienceToolY(int index) {
            return convenienceContentY + (index / 2) * SHAPE_ROW_PITCH;
        }

        public int convenienceToolX(int index) {
            return contentX + (index % 2) * (CONVENIENCE_TOOL_W + SHAPE_GAP);
        }

        public int convenienceParameterLabelY(int index) {
            return convenienceContentY + index * CONVENIENCE_PARAMETER_PITCH;
        }

        public int convenienceParameterSliderY(int index) {
            return convenienceParameterLabelY(index) + CONVENIENCE_PARAMETER_LABEL_GAP;
        }

        public int smartFillParameterLabelY(int index) {
            return convenienceParameterLabelY(index);
        }

        public int smartFillParameterSliderY(int index) {
            return smartFillParameterLabelY(index) + CONVENIENCE_PARAMETER_LABEL_GAP;
        }

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

        /** 模式按钮使用半开边界；按钮间的空隙不会误触任何主模式。 */
        public QuickBuildUiMode modeAt(double mouseX, double mouseY) {
            if (buildMode.contains(mouseX, mouseY)) {
                return QuickBuildUiMode.BUILD;
            }
            if (destroyMode.contains(mouseX, mouseY)) {
                return QuickBuildUiMode.DESTROY;
            }
            return null;
        }
    }

    /**
     * Tooltip 直接在 RTS 虚拟视口中定位并夹紧，不能交由外层 Minecraft GUI 再换算一次。
     */
    public static UiRect tooltipBounds(int screenWidth, int screenHeight,
            int pointerX, int pointerY, int tooltipWidth, int tooltipHeight) {
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
}
