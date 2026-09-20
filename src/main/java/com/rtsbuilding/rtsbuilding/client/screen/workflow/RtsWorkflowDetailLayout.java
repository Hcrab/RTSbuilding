package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/**
 * 工作流详情窗口正文的边界计算。
 *
 * <p>详情正文、滚轮命中和关闭按钮必须共享同一视口；这个纯布局接点不读取工作流
 * 状态，也不绘制文本，方便验证高缩放/小窗口下不会把内容命中区算到窗口外。</p>
 */
final class RtsWorkflowDetailLayout {
    private static final int MIN_VIEWPORT_SIZE = 1;
    private static final int FOOTER_GAP = 5;
    private RtsWorkflowDetailLayout() {
    }

    static int adaptiveMinimum(int preferred, int screenSize) {
        return Math.min(preferred, Math.max(1, screenSize - 8));
    }

    static Geometry content(
            int contentX, int contentY, int contentWidth, int contentHeight,
            int padding, int footerHeight) {
        int x = contentX + padding;
        int y = contentY + padding;
        int width = Math.max(1, contentWidth - padding * 2);
        int footerY = contentY + contentHeight - padding - footerHeight;
        int bottom = Math.max(y + MIN_VIEWPORT_SIZE, footerY - FOOTER_GAP);
        return new Geometry(
                new UiRect(x, y, width, Math.max(1, bottom - y)), footerY, width);
    }

    static final class Geometry {
        final UiRect viewport;
        final int footerY;
        final int textWidth;

        private Geometry(UiRect viewport, int footerY, int textWidth) {
            this.viewport = viewport;
            this.footerY = footerY;
            this.textWidth = textWidth;
        }
    }
}
