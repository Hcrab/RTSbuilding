package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar;

/**
 * 顶部栏布局坐标计算——集中管理按钮位置，消除多处重复计算。
 *
 * <p>所有常量与 {@link TopBarPanel} 中的定义保持一致，
 * 通过 {@code screenWidth} 和 {@code rightSidebarWidth} 参数计算各按钮的准确矩形区域，
 * 一处定义，三处引用（render + mouseClicked + 弹窗位置）。</p>
 *
 * <p>右侧按钮位于顶部栏下半部分区域内，定位动态减去右边框宽度
 * {@code rightSidebarWidth}，避免被右边框遮挡。</p>
 *
 * <p>使用 {@link ButtonGroup} 管理按钮组布局：
 * <ul>
 *   <li>组内按钮通过 {@link #INNER_GAP} 控制间距</li>
 *   <li>组间按钮通过 {@link #GROUP_GAP} 控制间距</li>
 *   <li>通过 {@link #createGroupLayout(int, int)} 一次性计算所有组</li>
 * </ul></p>
 */
public final class TopBarLayoutHelper {

    // ======================== 常量（与 TopBarPanel 同步） ========================

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

    // ======================== 按钮组 ========================

    /**
     * 按钮组——管理从右向左排列的一组尺寸相同的按钮。
     * <p>组内按钮间距由 {@link #INNER_GAP} 指定，
     * 与右边相邻组的间距由 {@link #groupGap} 指定。</p>
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
         *
         * @param anchorRight 右锚点（最右边按钮的右边缘）
         * @param anchorY     垂直居中 Y 坐标
         * @param size        按钮尺寸（宽高相等）
         * @param count       按钮数量
         * @param groupGap    与右侧相邻组的间距
         * @param innerGap    组内按钮间距
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

        /** 获取组内第 i 个按钮的矩形（0 = 最右侧） */
        public Rect rect(int index) { return rects[index]; }

        /** 组的左边缘（最左边按钮的 X），供左侧组锚定 */
        public int leftEdge() { return rects[rects.length - 1].x(); }

        /** 组的右边缘（最右边按钮的右边界），供 debug 弹窗锚定 */
        public int rightEdge() { return rects[0].x() + rects[0].width(); }

        /** 与右侧相邻组的间距 */
        public int groupGap() { return groupGap; }
    }

    // ======================== 组布局 ========================

    /**
     * 当前所有按钮分组布局。
     * <p>一次性计算所有按钮组，避免重复链式计算。</p>
     */
    public record GroupLayout(ButtonGroup modeGroup, ButtonGroup utilityGroup) {

        /**
         * 从右向左创建完整组布局：
         * <ol>
         *   <li><b>utilityGroup</b>（右侧工具组）：btn_right（索引0）、chunk_display（索引1）</li>
         *   <li><b>modeGroup</b>（相机模式组）：free_mode（索引0）、surround_mode（索引1）</li>
         * </ol>
         *
         * @param screenWidth       屏幕宽度
         * @param rightSidebarWidth 当前右边框实际宽度
         */
        public static GroupLayout create(int screenWidth, int rightSidebarWidth) {
            int anchorRight = effectiveRightEdge(screenWidth, rightSidebarWidth) - BTN_MARGIN_R;
            int anchorY = TOP_BAR_HEIGHT + SCREEN_BORDER + (BOTTOM_SRC_H - BTN_SIZE) / 2;

            // 右侧工具组（2个按钮）：btn_right | chunk_display
            var utility = ButtonGroup.fromRight(anchorRight, anchorY, BTN_SIZE, 2, 0, INNER_GAP);

            // 相机模式组（2个按钮）：free_mode | surround_mode
            var mode = ButtonGroup.fromRight(utility.leftEdge() - GROUP_GAP, anchorY, BTN_SIZE, 2, GROUP_GAP, INNER_GAP);

            return new GroupLayout(mode, utility);
        }
    }

    // ======================== 按钮矩形（便捷方法） ========================

    /**
     * 右侧按钮矩形（最右边，触发 Debug 弹出菜单）。
     * 等价于 {@code createGroupLayout(screenWidth, rightSidebarWidth).utilityGroup().rect(0)}。
     */
    public static Rect btnRightRect(int screenWidth, int rightSidebarWidth) {
        return GroupLayout.create(screenWidth, rightSidebarWidth).utilityGroup().rect(0);
    }

    /**
     * 区块显示按钮矩形（右侧按钮左边）。
     * 等价于 {@code createGroupLayout(screenWidth, rightSidebarWidth).utilityGroup().rect(1)}。
     */
    public static Rect chunkBtnRect(int screenWidth, int rightSidebarWidth) {
        return GroupLayout.create(screenWidth, rightSidebarWidth).utilityGroup().rect(1);
    }

    /**
     * 自由模式按钮矩形（相机模式组右侧）。
     * 等价于 {@code createGroupLayout(screenWidth, rightSidebarWidth).modeGroup().rect(0)}。
     */
    public static Rect freeModeBtnRect(int screenWidth, int rightSidebarWidth) {
        return GroupLayout.create(screenWidth, rightSidebarWidth).modeGroup().rect(0);
    }

    /**
     * 环绕玩家模式按钮矩形（自由模式按钮左边）。
     * 等价于 {@code createGroupLayout(screenWidth, rightSidebarWidth).modeGroup().rect(1)}。
     */
    public static Rect surroundModeBtnRect(int screenWidth, int rightSidebarWidth) {
        return GroupLayout.create(screenWidth, rightSidebarWidth).modeGroup().rect(1);
    }

    /**
     * 顶部栏有效右边缘（减去右边框宽度，避免按钮被右边框遮挡）。
     */
    private static int effectiveRightEdge(int screenWidth, int rightSidebarWidth) {
        return screenWidth - rightSidebarWidth;
    }

    // ======================== Logo ========================

    /**
     * Logo 矩形（左上角）。
     */
    public static Rect logoRect() {
        return new Rect(0, 0, LOGO_SIZE, LOGO_SIZE);
    }

    // ======================== 不可变矩形 ========================

    /**
     * 不可变整数矩形，用于按钮命中检测和位置描述。
     */
    public record Rect(int x, int y, int width, int height) {
        /** 检测点 (px, py) 是否在此矩形内 */
        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private TopBarLayoutHelper() {}
}
