package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/** 漏斗缓存右侧面板的正式几何与半开命中入口。 */
public final class FunnelBufferLayout {
    public static final int PANEL_W = 132;
    public static final int ROW_H = 22;
    public static final int TOGGLE_W = 60;
    public static final int TOGGLE_H = 16;
    public static final int MIN_PANEL_H = 20;
    public static final int LIST_TOP = 16;
    public static final int ROW_SIDE_INSET = 4;
    public static final int ROW_BOTTOM_GAP = 2;
    public static final int SLOT_SIZE = 18;
    public static final int SLOT_INSET = 2;

    private FunnelBufferLayout() {
    }

    public static int toggleX(int screenWidth) {
        return screenWidth - TOGGLE_W - 8;
    }

    public static int toggleY(int topHeight) {
        return topHeight + 6;
    }

    public static int panelX(int screenWidth) {
        return screenWidth - PANEL_W - 8;
    }

    public static int panelY(int topHeight) {
        return topHeight + 26;
    }

    public static int visibleRows(int panelHeight) {
        return Math.max(1, (panelHeight - MIN_PANEL_H) / ROW_H);
    }

    public static Geometry geometry(int screenWidth, int topHeight, int panelHeight) {
        int safeHeight = Math.max(0, panelHeight);
        return new Geometry(
                new UiRect(toggleX(screenWidth), toggleY(topHeight), TOGGLE_W, TOGGLE_H),
                new UiRect(panelX(screenWidth), panelY(topHeight), PANEL_W, safeHeight),
                safeHeight >= MIN_PANEL_H);
    }

    public enum Target {
        NONE,
        TOGGLE,
        ROW,
        PANEL
    }

    public static final class Hit {
        public final Target target;
        public final int visibleRowIndex;

        private Hit(Target target, int visibleRowIndex) {
            this.target = target;
            this.visibleRowIndex = visibleRowIndex;
        }

        public static Hit none() {
            return new Hit(Target.NONE, -1);
        }
    }

    public static final class Geometry {
        public final UiRect toggle;
        public final UiRect panel;
        public final boolean panelRenderable;

        private Geometry(UiRect toggle, UiRect panel, boolean panelRenderable) {
            this.toggle = toggle;
            this.panel = panel;
            this.panelRenderable = panelRenderable;
        }

        public UiRect row(int visibleIndex) {
            if (visibleIndex < 0) {
                throw new IllegalArgumentException("visibleIndex must be non-negative");
            }
            return new UiRect(
                    panel.getX() + ROW_SIDE_INSET,
                    panel.getY() + LIST_TOP + visibleIndex * ROW_H,
                    PANEL_W - ROW_SIDE_INSET * 2,
                    ROW_H - ROW_BOTTOM_GAP);
        }

        public UiRect slot(int visibleIndex) {
            UiRect row = row(visibleIndex);
            return new UiRect(
                    row.getX() + SLOT_INSET,
                    row.getY() + SLOT_INSET,
                    SLOT_SIZE,
                    SLOT_SIZE);
        }

        public Hit hitAt(double mouseX, double mouseY, int visibleRowCount, boolean panelVisible) {
            if (toggle.contains(mouseX, mouseY)) {
                return new Hit(Target.TOGGLE, -1);
            }
            if (!panelVisible || !panelRenderable || !panel.contains(mouseX, mouseY)) {
                return Hit.none();
            }
            int safeRows = Math.max(0, visibleRowCount);
            for (int index = 0; index < safeRows; index++) {
                if (row(index).contains(mouseX, mouseY)) {
                    return new Hit(Target.ROW, index);
                }
            }
            return new Hit(Target.PANEL, -1);
        }
    }
}
