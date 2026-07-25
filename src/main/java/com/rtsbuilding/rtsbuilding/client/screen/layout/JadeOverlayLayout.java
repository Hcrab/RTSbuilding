package com.rtsbuilding.rtsbuilding.client.screen.layout;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.TOP_ICON_BUTTON_W;

/**
 * 计算 Jade 面板在 RTS 屏幕中的坐标，并向顶部帮助文字发布短期占位。
 *
 * <p>本类刻意不引用 Jade 或 Forge API，只处理像素坐标。这样 1.20.1 与主线能够
 * 共用同一套玩家语义，版本差异仅停留在兼容入口。</p>
 */
public final class JadeOverlayLayout {
    private static final int TOP_BUTTON_Y = 4;
    private static final int TOP_RIGHT_MARGIN = 8;
    private static final int CURSOR_OFFSET = 8;
    private static final int GEAR_GAP = 8;
    private static final long RESERVATION_LIFETIME_NANOS = 750_000_000L;

    private static volatile int reservedLeftVirtualX = -1;
    private static volatile long reservationExpiresAt;

    private JadeOverlayLayout() {
    }

    /** 将面板固定到顶部栏设置按钮左侧。 */
    public static Position anchored(int screenWidth, int screenHeight, int panelWidth, int panelHeight,
            double renderScale) {
        double safeScale = sanitizeScale(renderScale);
        int gearLeft = screenWidth - scaled(TOP_ICON_BUTTON_W + TOP_RIGHT_MARGIN, safeScale);
        int x = gearLeft - scaled(GEAR_GAP, safeScale) - panelWidth;
        int y = scaled(TOP_BUTTON_Y, safeScale);
        return clampToScreen(x, y, screenWidth, screenHeight, panelWidth, panelHeight);
    }

    /** 将面板放在鼠标右侧；空间不足时自动翻到左侧。 */
    public static Position followingCursor(int screenWidth, int screenHeight, int panelWidth, int panelHeight,
            int mouseX, int mouseY) {
        int x = mouseX + CURSOR_OFFSET;
        if (x + panelWidth > screenWidth) {
            x = mouseX - panelWidth - CURSOR_OFFSET;
        }
        int y = mouseY - panelHeight / 2;
        return clampToScreen(x, y, screenWidth, screenHeight, panelWidth, panelHeight);
    }

    /** 发布固定面板的左边缘，让顶部帮助文字在下一帧主动避让。 */
    public static void publishAnchoredReservation(int actualLeftX, double renderScale) {
        double safeScale = sanitizeScale(renderScale);
        reservedLeftVirtualX = Math.max(0, (int) Math.floor(actualLeftX / safeScale));
        reservationExpiresAt = System.nanoTime() + RESERVATION_LIFETIME_NANOS;
    }

    /** 跟随鼠标、隐藏或离开 RTS 时都必须清除占位。 */
    public static void clearReservation() {
        reservedLeftVirtualX = -1;
        reservationExpiresAt = 0L;
    }

    /** 返回仍有效的 RTS 虚拟坐标左边缘；没有占位时返回 -1。 */
    public static int currentReservedLeftVirtualX() {
        if (reservedLeftVirtualX < 0 || System.nanoTime() > reservationExpiresAt) {
            clearReservation();
            return -1;
        }
        return reservedLeftVirtualX;
    }

    private static Position clampToScreen(int x, int y, int screenWidth, int screenHeight,
            int panelWidth, int panelHeight) {
        int maxX = Math.max(0, screenWidth - Math.max(0, panelWidth));
        int maxY = Math.max(0, screenHeight - Math.max(0, panelHeight));
        return new Position(clamp(x, 0, maxX), clamp(y, 0, maxY));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int scaled(int value, double scale) {
        return (int) Math.round(value * scale);
    }

    private static double sanitizeScale(double scale) {
        return scale > 0.0D && Double.isFinite(scale) ? scale : 1.0D;
    }

    /** Jade 面板左上角的 Minecraft GUI 坐标。 */
    public record Position(int x, int y) {
    }
}
