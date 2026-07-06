package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar;

/**
 * 顶部栏布局坐标计算——集中管理按钮位置，消除多处重复计算。
 *
 * <p>所有常量与 {@link TopBarPanel} 中的定义保持一致，
 * 通过 {@code screenWidth} 和 {@code rightSidebarWidth} 参数计算各按钮的准确矩形区域，
 * 一处定义，三处引用（render + mouseClicked + 弹窗位置）。</p>
 *
 * <p>布局计算方法已从静态方法改为实例方法，由 {@link TopBarPanel} 创建实例使用。
 * 常量保持静态以兼容广泛的跨类引用。</p>
 */
public final class TopBarLayoutHelper {

    // ======================== 常量（保持静态，跨类引用广泛） ========================

    /** 顶部栏上半部分绘制高度 */
    public static final int TOP_BAR_HEIGHT = 24;
    /** 顶部栏上下部分间隔 */
    public static final int TOP_BAR_GAP = 3;
    /** 下半部分源/绘制高度 */
    public static final int BOTTOM_SRC_H = 16;

    /** 区块显示/右侧按钮绘制尺寸 */
    public static final int BTN_SIZE = 14;
    /** 按钮距右侧内容区边缘间距 */
    public static final int BTN_MARGIN_R = 4;

    /** Logo 绘制尺寸 */
    public static final int LOGO_SIZE = 24;

    /** 上下部分间隔像素 */
    public static final int SCREEN_BORDER = 2;

    // ======================== 按钮组间距 ========================

    /** 组内按钮间距（同组按钮之间的空隙） */
    public static final int INNER_GAP = 0;
    /** 组间间距（不同组之间的空隙） */
    public static final int GROUP_GAP = 4;

    public TopBarLayoutHelper() {}

    // ======================== 按钮组（保持静态嵌套类型） ========================

    /**
     * 按钮组——管理从右向左排列的一组尺寸相同的按钮。
     */
    public static final class ButtonGroup {
        private final int groupGap;
        private final Rect[] rects;

        private ButtonGroup(int groupGap, Rect[] rects) {
            this.groupGap = groupGap;
            this.rects = rects;
        }

        /**
         * 从右向左创建按钮组布局。
         */
        public static ButtonGroup fromRight(int anchorRight, int anchorY, int size, int count, int groupGap, int innerGap) {
            Rect[] r = new Rect[count];
            int x = anchorRight;
            for (int i = 0; i < count; i++) {
                x -= size;
                r[i] = new Rect(x, anchorY, size, size);
                x -= innerGap;
            }
            return new ButtonGroup(groupGap, r);
        }

        public Rect rect(int index) { return rects[index]; }
        public int leftEdge() { return rects[rects.length - 1].x(); }
        public int rightEdge() { return rects[0].x() + rects[0].width(); }
        public int groupGap() { return groupGap; }
    }

    // ======================== 组布局 ========================

    /**
     * 当前所有按钮分组布局。
     */
    public record GroupLayout(ButtonGroup modeGroup, ButtonGroup utilityGroup) {

        /**
         * 从右向左创建完整组布局：
         * <ol>
         *   <li><b>utilityGroup</b>（右侧工具组）</li>
         *   <li><b>modeGroup</b>（相机模式组）</li>
         * </ol>
         */
        public static GroupLayout create(int screenWidth, int rightSidebarWidth) {
            int anchorRight = effectiveRightEdge(screenWidth, rightSidebarWidth) - BTN_MARGIN_R;
            int anchorY = TOP_BAR_HEIGHT + SCREEN_BORDER + (BOTTOM_SRC_H - BTN_SIZE) / 2;

            var utility = ButtonGroup.fromRight(anchorRight, anchorY, BTN_SIZE, 2, 0, INNER_GAP);
            var mode = ButtonGroup.fromRight(utility.leftEdge() - GROUP_GAP, anchorY, BTN_SIZE, 2, GROUP_GAP, INNER_GAP);

            return new GroupLayout(mode, utility);
        }
    }

    // ======================== Logo ========================

    /**
     * Logo 矩形（左上角）。
     */
    public Rect logoRect() {
        return new Rect(0, 0, LOGO_SIZE, LOGO_SIZE);
    }

    // ======================== 便利方法 ========================

    /**
     * 顶部栏有效右边缘（减去右边框宽度，避免按钮被右边框遮挡）。
     */
    private static int effectiveRightEdge(int screenWidth, int rightSidebarWidth) {
        return screenWidth - rightSidebarWidth;
    }

    // ======================== 不可变矩形 ========================

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
}
