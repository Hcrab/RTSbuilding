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
    public static final int DESTROY_BASE_H = 286;
    public static final int BOTTOM_INFO_H = 72;
    public static final int TITLE_H = 20;
    public static final int MODE_H = 18;
    public static final int MODE_GAP = 4;
    public static final int MODE_TOP = 5;
    public static final int SECTION_TOP = 31;
    public static final int CATALOG_TOP = SECTION_TOP + 14;
    public static final int CATALOG_H = 18;
    public static final int CATALOG_GAP = 4;
    public static final int CATALOG_TOOLS_GAP = 8;
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
    public static final int CONVENIENCE_TOOL_W = 72;
    public static final int CONVENIENCE_TOOL_H = 22;
    public static final int CONVENIENCE_TOOL_PITCH = 30;
    public static final int CONVENIENCE_PARAMETER_LABEL_GAP = 13;
    public static final int CONVENIENCE_PARAMETER_PITCH = 50;
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

    private QuickBuildWindowLayout() {}

    public static int windowHeight(boolean destroy) {
        return (destroy ? DESTROY_BASE_H : BUILD_BASE_H) + BOTTOM_INFO_H;
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

    public static int smartFillSliderWidth(int windowWidth) {
        return Math.max(60, windowWidth - CONTENT_INSET * 2 - 40);
    }

    public static Geometry geometry(int windowX, int windowY, QuickBuildUiMode mode) {
        int bodyY = windowY + TITLE_H;
        int totalW = WINDOW_W - CONTENT_INSET * 2;
        int modeW = (totalW - MODE_GAP * 2) / 3;
        int buildX = windowX + CONTENT_INSET;
        int destroyX = buildX + modeW + MODE_GAP;
        int smartFillX = destroyX + modeW + MODE_GAP;
        boolean destroy = mode == QuickBuildUiMode.DESTROY;
        return new Geometry(windowX, windowY, bodyY,
                buildX, destroyX, smartFillX,
                bodyY + MODE_TOP, modeW,
                bodyY + SECTION_TOP,
                windowX + RIGHT_COL_X,
                windowY + (destroy ? DESTROY_BASE_H : BUILD_BASE_H),
                windowHeight(destroy), destroy);
    }

    public static final class Geometry {
        public final int windowX, windowY, bodyY;
        public final int buildModeX, destroyModeX, smartFillModeX, modeY, modeW;
        public final int sectionTitleY, rightX, dividerY, windowH;
        public final boolean destroy;
        public final int chainLabelY, chainSliderY, statusTextY, statusItemY;
        public final int catalogY, catalogW, convenienceContentY;
        public final int contentX, contentW, sectionLabelX;
        public final UiRect buildMode, destroyMode, smartFillMode, divider, progress;

        private Geometry(int windowX,int windowY,int bodyY,int buildModeX,int destroyModeX,int smartFillModeX,
                         int modeY,int modeW,int sectionTitleY,int rightX,int dividerY,int windowH,
                         boolean destroy) {
            this.windowX=windowX; this.windowY=windowY; this.bodyY=bodyY;
            this.buildModeX=buildModeX; this.destroyModeX=destroyModeX;
            this.smartFillModeX=smartFillModeX;
            this.modeY=modeY; this.modeW=modeW; this.sectionTitleY=sectionTitleY;
            this.rightX=rightX; this.dividerY=dividerY; this.windowH=windowH;
            this.destroy=destroy;
            this.chainLabelY = bodyY + CHAIN_LABEL_TOP;
            this.chainSliderY = chainLabelY + CHAIN_SLIDER_GAP;
            this.statusTextY = dividerY + STATUS_TEXT_TOP;
            this.statusItemY = statusTextY + STATUS_ITEM_Y_OFFSET;
            this.contentX = windowX + CONTENT_INSET;
            this.contentW = WINDOW_W - CONTENT_INSET * 2;
            this.sectionLabelX = windowX + SECTION_LABEL_INSET;
            this.catalogY = bodyY + CATALOG_TOP;
            this.catalogW = (contentW - CATALOG_GAP) / 2;
            this.convenienceContentY = catalogY + CATALOG_H + CATALOG_TOOLS_GAP;
            this.buildMode = new UiRect(buildModeX, modeY, modeW, MODE_H);
            this.destroyMode = new UiRect(destroyModeX, modeY, modeW, MODE_H);
            this.smartFillMode = new UiRect(smartFillModeX, modeY, modeW, MODE_H);
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
            return bodyY + CONTROL_LIST_TOP + (destroy ? 26 : 0)
                    + (index / 2) * SHAPE_ROW_PITCH;
        }

        public int controlY(int index) {
            return bodyY + CONTROL_LIST_TOP + (destroy ? 26 : 0)
                    + index * SHAPE_ROW_PITCH;
        }

        public int catalogX(int index) {
            return contentX + index * (catalogW + CATALOG_GAP);
        }

        public int convenienceToolY(int index) {
            return convenienceContentY + index * CONVENIENCE_TOOL_PITCH;
        }

        public int convenienceParameterLabelY(int index) {
            return convenienceContentY + index * CONVENIENCE_PARAMETER_PITCH;
        }

        public int convenienceParameterSliderY(int index) {
            return convenienceParameterLabelY(index) + CONVENIENCE_PARAMETER_LABEL_GAP;
        }

        public int smartFillParameterLabelY(int index) {
            return bodyY + CONTROL_LIST_TOP + index * CONVENIENCE_PARAMETER_PITCH;
        }

        public int smartFillParameterSliderY(int index) {
            return smartFillParameterLabelY(index) + CONVENIENCE_PARAMETER_LABEL_GAP;
        }

        public int smartFillValueX(int sliderWidth) {
            return contentX + sliderWidth + CHAIN_VALUE_GAP;
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
            if (mode == QuickBuildUiMode.DESTROY) {
                return destroyMode;
            }
            return mode == QuickBuildUiMode.SMART_FILL ? smartFillMode : buildMode;
        }

        /** 模式按钮使用半开边界；两个按钮之间的空隙不会误触任意模式。 */
        public QuickBuildUiMode modeAt(double mouseX, double mouseY) {
            if (buildMode.contains(mouseX, mouseY)) {
                return QuickBuildUiMode.BUILD;
            }
            if (destroyMode.contains(mouseX, mouseY)) {
                return QuickBuildUiMode.DESTROY;
            }
            return smartFillMode.contains(mouseX, mouseY)
                    ? QuickBuildUiMode.SMART_FILL : null;
        }
    }
}
