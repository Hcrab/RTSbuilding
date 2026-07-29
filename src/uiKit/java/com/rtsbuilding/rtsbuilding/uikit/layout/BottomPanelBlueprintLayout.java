package com.rtsbuilding.rtsbuilding.uikit.layout;

/**
 * 底栏蓝图库内容区的纯 Java 几何快照。
 *
 * <p>本类只负责从底栏外框解析蓝图库可用内容区，使生产绘制、点击和滚轮使用同一组
 * 边距与最小尺寸。它不读取蓝图数据，也不执行选择、刷新或放置动作。</p>
 */
public final class BottomPanelBlueprintLayout {
    public static final int HORIZONTAL_PADDING = RtsMainlineLayout.BOTTOM_PANEL_PADDING;
    public static final int CONTENT_TOP_GAP = 4;
    public static final int CONTENT_BOTTOM_GAP = 4;
    public static final int MIN_WIDTH = 80;
    public static final int MIN_HEIGHT = 24;

    public final BottomPanelHeaderLayout.Area content;

    private BottomPanelBlueprintLayout(BottomPanelHeaderLayout.Area content) {
        this.content = content;
    }

    public static BottomPanelBlueprintLayout resolve(
            int panelX, int panelY, int panelWidth, int panelHeight) {
        if (panelWidth <= 0 || panelHeight <= 0) {
            throw new IllegalArgumentException("panel dimensions must be positive");
        }
        int x = panelX + HORIZONTAL_PADDING;
        int y = panelY + BottomPanelHeaderLayout.HEADER_HEIGHT + CONTENT_TOP_GAP;
        int width = Math.max(MIN_WIDTH, panelWidth - HORIZONTAL_PADDING * 2);
        int height = Math.max(MIN_HEIGHT,
                panelHeight - BottomPanelHeaderLayout.HEADER_HEIGHT
                        - CONTENT_TOP_GAP - CONTENT_BOTTOM_GAP);
        return new BottomPanelBlueprintLayout(
                BottomPanelHeaderLayout.area(x, y, width, height));
    }
}
