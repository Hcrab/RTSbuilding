package com.rtsbuilding.rtsbuilding.uikit.layout;

/**
 * RTS 左侧固定 Tooltip 的锚点几何。
 *
 * <p>本类只根据底栏左边缘、底栏顶部和顶部保留区计算主 Tooltip 与补充说明的坐标；
 * 不选择物品、不测量 Minecraft 字体，也不决定 Tooltip 内容。生产与离屏回放可据此保持
 * 同一避让规则，而不把偏移常量留在主 Screen。</p>
 */
public final class LeftDockedTooltipLayout {
    public static final int X_OFFSET = 8;
    public static final int Y_OFFSET = 24;
    public static final int TOP_GAP = 8;
    public static final int DETAIL_X_OFFSET = 10;
    public static final int DETAIL_Y_OFFSET = 18;

    public static Geometry resolve(int panelX, int bottomPanelY, int reservedTop) {
        if (reservedTop < 0) {
            throw new IllegalArgumentException("reserved top must not be negative");
        }
        int anchorX = panelX + X_OFFSET;
        int anchorY = Math.max(reservedTop + TOP_GAP, bottomPanelY - Y_OFFSET);
        return new Geometry(anchorX, anchorY,
                anchorX + DETAIL_X_OFFSET,
                anchorY + DETAIL_Y_OFFSET);
    }

    public static final class Geometry {
        private final int anchorX;
        private final int anchorY;
        private final int detailX;
        private final int detailY;

        private Geometry(int anchorX, int anchorY, int detailX, int detailY) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.detailX = detailX;
            this.detailY = detailY;
        }

        public int anchorX() {
            return anchorX;
        }

        public int anchorY() {
            return anchorY;
        }

        public int detailX() {
            return detailX;
        }

        public int detailY() {
            return detailY;
        }
    }

    private LeftDockedTooltipLayout() {
    }
}
