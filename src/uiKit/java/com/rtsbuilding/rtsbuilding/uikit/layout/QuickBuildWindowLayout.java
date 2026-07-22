package com.rtsbuilding.rtsbuilding.uikit.layout;

/**
 * 生产 QuickBuildPanel 的精确纯 Java 几何描述。
 *
 * <p>只拥有窗口/模式行/两列形状/右栏控件和底部信息区坐标，不拥有玩法状态、
 * Minecraft 控件或贴图绘制。生产和离屏共同使用后，预览不再用错误的四列形状布局。</p>
 */
public final class QuickBuildWindowLayout {
    public static final int WINDOW_W = 178;
    public static final int BUILD_BASE_H = 222;
    public static final int DESTROY_BASE_H = 260;
    public static final int BOTTOM_INFO_H = 72;
    public static final int TITLE_H = 20;
    public static final int MODE_H = 18;
    public static final int MODE_GAP = 4;
    public static final int MODE_TOP = 5;
    public static final int SECTION_TOP = 31;
    public static final int SHAPE_SLOT = 32;
    public static final int SHAPE_GAP = 8;
    public static final int SHAPE_ROW_PITCH = 38;
    public static final int RIGHT_COL_X = 88;
    public static final int CONTROL_W = 84;
    public static final int CONTROL_H = 20;

    private QuickBuildWindowLayout() {}

    public static int windowHeight(boolean destroy) {
        return (destroy ? DESTROY_BASE_H : BUILD_BASE_H) + BOTTOM_INFO_H;
    }

    public static Geometry geometry(int windowX, int windowY, boolean destroy) {
        int bodyY = windowY + TITLE_H;
        int totalW = WINDOW_W - 16;
        int modeW = (totalW - MODE_GAP) / 2;
        return new Geometry(windowX, windowY, bodyY,
                windowX + 8, windowX + 8 + modeW + MODE_GAP,
                bodyY + MODE_TOP, modeW,
                bodyY + SECTION_TOP,
                windowX + RIGHT_COL_X,
                windowY + (destroy ? DESTROY_BASE_H : BUILD_BASE_H),
                windowHeight(destroy));
    }

    public static final class Geometry {
        public final int windowX, windowY, bodyY;
        public final int buildModeX, destroyModeX, modeY, modeW;
        public final int sectionTitleY, rightX, dividerY, windowH;
        private Geometry(int windowX,int windowY,int bodyY,int buildModeX,int destroyModeX,
                         int modeY,int modeW,int sectionTitleY,int rightX,int dividerY,int windowH) {
            this.windowX=windowX; this.windowY=windowY; this.bodyY=bodyY;
            this.buildModeX=buildModeX; this.destroyModeX=destroyModeX;
            this.modeY=modeY; this.modeW=modeW; this.sectionTitleY=sectionTitleY;
            this.rightX=rightX; this.dividerY=dividerY; this.windowH=windowH;
        }
        public int shapeX(int index){return windowX + 8 + (index % 2) * (SHAPE_SLOT + SHAPE_GAP);}
        public int shapeY(int index){return bodyY + SECTION_TOP + 15 + (index / 2) * SHAPE_ROW_PITCH;}
        public int controlY(int index){return bodyY + SECTION_TOP + 15 + index * SHAPE_ROW_PITCH;}
    }
}
