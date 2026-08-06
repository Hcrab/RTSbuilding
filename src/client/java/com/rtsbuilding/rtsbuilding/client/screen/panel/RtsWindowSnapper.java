package com.rtsbuilding.rtsbuilding.client.screen.panel;

import java.util.List;

/**
 * 浮窗拖拽期间的边缘吸附几何计算器。
 *
 * <p>本类只读取当前窗口矩形并返回新的坐标，不改变窗口层级、持久化边界、拖拽状态或面板关系。
 * 水平吸附必须存在垂直重叠，垂直吸附必须存在水平重叠；吸附只对本次拖拽坐标生效，
 * 不会建立窗口之间的永久父子关系。将纯几何循环放在这里，可让基础窗口继续专注于交互状态机。</p>
 */
final class RtsWindowSnapper {
    /** 相邻窗口边框之间保留一个像素，延续原有可辨识分隔线。 */
    private static final int EDGE_GAP = 1;

    static Result snap(RtsWindowPanel moving,
                       List<RtsWindowPanel> panels,
                       int threshold) {
        int x = moving.getWindowX();
        int y = moving.getWindowY();
        int width = moving.getWindowWidth();
        int height = moving.getWindowHeight();
        int originalX = x;
        int originalY = y;

        for (RtsWindowPanel other : panels) {
            if (other == moving || !other.isOpen()) continue;

            int otherX = other.getWindowX();
            int otherY = other.getWindowY();
            int otherWidth = other.getWindowWidth();
            int otherHeight = other.getWindowHeight();
            boolean verticalOverlap = overlap(
                    y, y + height, otherY, otherY + otherHeight) > 0;
            boolean horizontalOverlap = overlap(
                    x, x + width, otherX, otherX + otherWidth) > 0;

            if (verticalOverlap) {
                int otherRight = otherX + otherWidth;
                if (Math.abs(x - otherRight) < threshold) {
                    x = otherRight + EDGE_GAP;
                } else if (Math.abs(x + width - otherX) < threshold) {
                    x = otherX - width - EDGE_GAP;
                }
            }
            if (horizontalOverlap) {
                int otherBottom = otherY + otherHeight;
                if (Math.abs(y - otherBottom) < threshold) {
                    y = otherBottom + EDGE_GAP;
                } else if (Math.abs(y + height - otherY) < threshold) {
                    y = otherY - height - EDGE_GAP;
                }
            }
        }
        return new Result(x, y, x != originalX || y != originalY);
    }

    private static int overlap(int firstStart, int firstEnd,
                               int secondStart, int secondEnd) {
        return Math.max(0,
                Math.min(firstEnd, secondEnd) - Math.max(firstStart, secondStart));
    }

    static final class Result {
        final int x;
        final int y;
        final boolean snapped;

        private Result(int x, int y, boolean snapped) {
            this.x = x;
            this.y = y;
            this.snapped = snapped;
        }
    }

    private RtsWindowSnapper() {
    }
}
