package com.rtsbuilding.rtsbuilding.client.screen.input;

/**
 * 鼠标点击与拖动的纯数学判定器。
 *
 * <p>本类不读取 Minecraft 或 LWJGL 状态，只比较按下起点和当前位置。这样旧版
 * {@code GuiScreen} 高频上报的细小来回抖动不会被累计成镜头拖动，也便于在没有游戏
 * 客户端的单元测试里锁定右键“点击可交互、拖动才旋转”的边界。</p>
 */
public final class PointerGestureClassifier {
    private PointerGestureClassifier() {
    }

    public static double distanceFromPress(
            double startX, double startY, double currentX, double currentY) {
        if (!Double.isFinite(startX) || !Double.isFinite(startY)
                || !Double.isFinite(currentX) || !Double.isFinite(currentY)) {
            return 0.0D;
        }
        return Math.hypot(currentX - startX, currentY - startY);
    }

    public static boolean isIntentionalDrag(
            double startX, double startY, double currentX, double currentY,
            double threshold) {
        return threshold >= 0.0D
                && distanceFromPress(startX, startY, currentX, currentY) > threshold;
    }
}
