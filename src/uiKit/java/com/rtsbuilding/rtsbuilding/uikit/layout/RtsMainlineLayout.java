package com.rtsbuilding.rtsbuilding.uikit.layout;

import java.util.Arrays;

/**
 * 1.21.1 主线 RTS 固定栏的纯 Java 布局描述。
 *
 * <p>本类只拥有生产界面已经采用的尺寸、间距和坐标推导，不认识 Minecraft
 * 类型，也不负责绘制。生产 UI 与离屏预览器共同调用它，避免预览器复制一套
 * 会随主线漂移的魔法数字。</p>
 */
public final class RtsMainlineLayout {
    public static final int TOP_H = 52;
    public static final int TOP_BUTTON_GAP = 5;
    public static final int TOP_BUTTON_H = 24;
    public static final int TOP_MODE_BUTTON_W = 32;
    public static final int TOP_ICON_BUTTON_W = 32;
    public static final int TOP_LEFT_MARGIN = 8;
    public static final int TOP_RIGHT_MARGIN = 8;
    public static final int TOP_BUTTON_Y = 4;
    public static final int TOP_MODE_ACTION_GROUP_GAP = 8;
    public static final int TOP_STATUS_X = 8;
    public static final int TOP_STATUS_RIGHT_MARGIN = 8;
    public static final int TOP_STATUS_ROW_1_Y = 33;
    public static final int TOP_STATUS_ROW_2_Y = 44;

    public static final int DEFAULT_BOTTOM_H = 110;
    public static final int MIN_BOTTOM_H = 72;
    public static final int MAX_BOTTOM_H = 320;
    public static final int BOTTOM_PANEL_PADDING = 8;
    public static final int BOTTOM_PANEL_HEADER_H = 18;
    public static final int SLOT = 22;
    public static final int HOTBAR_SLOT = 18;
    public static final int HOTBAR_PITCH = 20;
    public static final int TOOL_AREA_H = 18;
    public static final int CATEGORY_W = 124;
    public static final int CRAFT_PANEL_W = 126;
    public static final int CRAFT_PANEL_GAP = 6;
    public static final int CRAFT_PANEL_SEARCH_H = 12;
    public static final int CRAFT_PANEL_SLOT = 18;

    private RtsMainlineLayout() {
    }

    /** 生产顶部栏的确定性按钮 X 坐标，顺序与 main 的 TopBarPanel 一致。 */
    public static TopButtons topButtons(int screenWidth, boolean quickBuild,
                                        boolean questDetect, boolean rangeCulling,
                                        boolean developer) {
        int[] positions = new int[12];
        Arrays.fill(positions, -1);
        int x = TOP_LEFT_MARGIN;
        for (int id = 0; id < 4; id++) {
            positions[id] = x;
            x += TOP_MODE_BUTTON_W + TOP_BUTTON_GAP;
        }
        x += TOP_MODE_ACTION_GROUP_GAP;
        if (quickBuild) x = put(positions, 4, x, TOP_ICON_BUTTON_W);
        if (questDetect) x = put(positions, 5, x, TOP_ICON_BUTTON_W);
        x = put(positions, 6, x, TOP_ICON_BUTTON_W);
        if (rangeCulling) x = put(positions, 7, x, TOP_ICON_BUTTON_W);
        x = put(positions, 8, x, TOP_ICON_BUTTON_W);
        if (developer) x = put(positions, 9, x, TOP_ICON_BUTTON_W);
        positions[10] = Math.max(x + TOP_BUTTON_GAP,
                screenWidth - TOP_ICON_BUTTON_W - TOP_RIGHT_MARGIN);
        return new TopButtons(positions);
    }

    /** 生产顶栏两行状态文字的共享边界。 */
    public static TopStatus topStatus(int screenWidth) {
        return new TopStatus(TOP_STATUS_X,
                Math.max(40, screenWidth - TOP_STATUS_X - TOP_STATUS_RIGHT_MARGIN),
                TOP_STATUS_ROW_1_Y, TOP_STATUS_ROW_2_Y);
    }

    /** 左侧状态文字与右侧模式提示都能完整容纳时返回提示 X，否则隐藏。 */
    public static int contextualHintX(TopStatus status, int leftTextWidth,
                                      int hintWidth, int gap) {
        int hintX = status.x + status.width - hintWidth;
        return hintX >= status.x + leftTextWidth + gap ? hintX : -1;
    }

    /** 计算与生产 BottomPanel.resolveBottomPanelLayout 相同的全部子区域。 */
    public static BottomPanel bottomPanel(int screenWidth, int screenHeight, int requestedHeight) {
        int dynamicMaxH = Math.max(MIN_BOTTOM_H,
                Math.min(MAX_BOTTOM_H, screenHeight - TOP_H - 16));
        int minimumForTwoRows = BOTTOM_PANEL_HEADER_H + 4 + 17
                + TOOL_AREA_H + 4 + BOTTOM_PANEL_PADDING + 2 * SLOT;
        int minH = Math.min(dynamicMaxH, Math.max(MIN_BOTTOM_H, minimumForTwoRows));
        int panelH = clamp(requestedHeight, minH, Math.max(minH, dynamicMaxH));
        return bottomPanelAtHeight(screenWidth, screenHeight, panelH);
    }

    /**
     * 按已经持久化/捕获的面板高度计算子区域，不再次改写高度。
     *
     * <p>生产入口应调用 {@link #bottomPanel(int, int, int)} 应用当前最小行数；
     * 截图回放可用本方法忠实重现旧版本允许保存的较矮边界，同时仍共享所有
     * 分类、搜索、分页、快捷槽和合成区域坐标。</p>
     */
    public static BottomPanel bottomPanelAtHeight(int screenWidth, int screenHeight, int capturedHeight) {
        int panelH = clamp(capturedHeight, 1, Math.max(1, screenHeight));
        int panelX = 0;
        int panelY = screenHeight - panelH;
        int panelW = screenWidth;
        int contentX = BOTTOM_PANEL_PADDING;
        int contentY = panelY + BOTTOM_PANEL_HEADER_H + 4;
        int sortX = contentX;
        int sortY = contentY + 2;
        int categoryX = sortX + 58;
        int categoryY = contentY;
        int categoryH = Math.max(24, panelY + panelH - BOTTOM_PANEL_PADDING - categoryY);
        int storageX = categoryX + CATEGORY_W + 10;
        int storageY = contentY;
        int storageW = Math.max(120, panelW - BOTTOM_PANEL_PADDING - storageX);
        int craftPanelX = storageX + Math.max(120, storageW - CRAFT_PANEL_W);
        int mainStorageW = Math.max(120, craftPanelX - storageX - CRAFT_PANEL_GAP);
        int searchW = Math.max(72, mainStorageW - 82);
        int pagerX = Math.min(storageX + searchW + 4, craftPanelX - 80);
        searchW = Math.max(56, pagerX - storageX - 4);
        int toolY = storageY + 17;
        int gridY = toolY + TOOL_AREA_H + 4;
        int gridH = Math.max(SLOT, panelY + panelH - BOTTOM_PANEL_PADDING - gridY);
        int craftPanelY = storageY;
        int craftPanelH = Math.max(CRAFT_PANEL_SEARCH_H + CRAFT_PANEL_SLOT + 27,
                panelY + panelH - BOTTOM_PANEL_PADDING - craftPanelY);
        return new BottomPanel(panelX, panelY, panelW, panelH, sortX, sortY,
                categoryX, categoryY, categoryH, storageX, storageY, storageW,
                craftPanelX, mainStorageW, searchW, pagerX, toolY, gridY, gridH,
                Math.max(1, gridH / SLOT), craftPanelY, craftPanelH);
    }

    private static int put(int[] positions, int id, int x, int width) {
        positions[id] = x;
        return x + width + TOP_BUTTON_GAP;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class TopButtons {
        private final int[] positions;

        private TopButtons(int[] positions) {
            this.positions = positions;
        }

        public int x(int id) {
            if (id < 0 || id >= positions.length || positions[id] < 0) {
                throw new IllegalArgumentException("top button is not present: " + id);
            }
            return positions[id];
        }

        public boolean isPresent(int id) {
            return id >= 0 && id < positions.length && positions[id] >= 0;
        }
    }

    public static final class TopStatus {
        public final int x, width, row1Y, row2Y;

        private TopStatus(int x, int width, int row1Y, int row2Y) {
            this.x = x;
            this.width = width;
            this.row1Y = row1Y;
            this.row2Y = row2Y;
        }
    }

    public static final class BottomPanel {
        public final int panelX, panelY, panelW, panelH;
        public final int sortX, sortY, categoryX, categoryY, categoryH;
        public final int storageX, storageY, storageW, craftPanelX;
        public final int mainStorageW, searchW, pagerX, toolY, gridY, gridH;
        public final int storageRows, craftPanelY, craftPanelH;

        private BottomPanel(int panelX, int panelY, int panelW, int panelH,
                            int sortX, int sortY, int categoryX, int categoryY,
                            int categoryH, int storageX, int storageY, int storageW,
                            int craftPanelX, int mainStorageW, int searchW, int pagerX,
                            int toolY, int gridY, int gridH, int storageRows,
                            int craftPanelY, int craftPanelH) {
            this.panelX = panelX;
            this.panelY = panelY;
            this.panelW = panelW;
            this.panelH = panelH;
            this.sortX = sortX;
            this.sortY = sortY;
            this.categoryX = categoryX;
            this.categoryY = categoryY;
            this.categoryH = categoryH;
            this.storageX = storageX;
            this.storageY = storageY;
            this.storageW = storageW;
            this.craftPanelX = craftPanelX;
            this.mainStorageW = mainStorageW;
            this.searchW = searchW;
            this.pagerX = pagerX;
            this.toolY = toolY;
            this.gridY = gridY;
            this.gridH = gridH;
            this.storageRows = storageRows;
            this.craftPanelY = craftPanelY;
            this.craftPanelH = craftPanelH;
        }
    }
}
