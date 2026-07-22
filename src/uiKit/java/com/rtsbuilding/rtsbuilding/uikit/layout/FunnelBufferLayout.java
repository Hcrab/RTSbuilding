package com.rtsbuilding.rtsbuilding.uikit.layout;

/** 漏斗缓存右侧面板的正式几何。 */
public final class FunnelBufferLayout {
    public static final int PANEL_W = 132;
    public static final int ROW_H = 22;
    public static final int TOGGLE_W = 60;
    public static final int TOGGLE_H = 16;

    private FunnelBufferLayout() {
    }

    public static int toggleX(int screenWidth) { return screenWidth - TOGGLE_W - 8; }
    public static int toggleY(int topHeight) { return topHeight + 6; }
    public static int panelX(int screenWidth) { return screenWidth - PANEL_W - 8; }
    public static int panelY(int topHeight) { return topHeight + 26; }
    public static int visibleRows(int panelHeight) { return Math.max(1, (panelHeight - 20) / ROW_H); }
}
