package com.rtsbuilding.rtsbuilding.client.screen.layout;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.TOP_ICON_BUTTON_W;
import static com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarLayout.BUTTON_Y;
import static com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarLayout.RIGHT_MARGIN;

/**
 * 计算 Jade 面板在 RTS 屏幕中的坐标，并向顶部帮助文字发布短期占位。
 *
 * <p>本类不引用 Jade API；它只处理像素坐标，因此以后移植 UI Core 时可以直接复用。
 * Jade 未安装时不会发布占位，顶部栏也不会受到影响。
 */
public final class JadeOverlayLayout {
    private static final int CURSOR_OFFSET = 8;
    private static final int GEAR_GAP = 8;
    private static final long RESERVATION_LIFETIME_NANOS = 750_000_000L;

    private static volatile int reservedLeftVirtualX = -1;
    private static volatile long reservationExpiresAt;

    private JadeOverlayLayout() {
    }

    /**
     * 将面板固定到顶部栏齿轮按钮左侧。
     *
     * @param renderScale RTS 虚拟坐标到 Minecraft 当前 GUI 坐标的缩放倍率
     */
    public static Position anchored(int screenWidth, int screenHeight, int panelWidth, int panelHeight,
            double renderScale) {
        double safeScale = sanitizeScale(renderScale);
        int gearLeft = screenWidth - scaled(TOP_ICON_BUTTON_W + RIGHT_MARGIN, safeScale);
        int x = gearLeft - scaled(GEAR_GAP, safeScale) - panelWidth;
        int y = scaled(BUTTON_Y, safeScale);
        return clampToScreen(x, y, screenWidth, screenHeight, panelWidth, panelHeight);
    }

    /** 将面板放在鼠标右侧；右侧空间不足时自动翻到左侧。 */
    public static Position followingCursor(int screenWidth, int screenHeight, int panelWidth, int panelHeight,
            int mouseX, int mouseY) {
        int x = mouseX + CURSOR_OFFSET;
        if (x + panelWidth > screenWidth) {
            x = mouseX - panelWidth - CURSOR_OFFSET;
        }
        int y = mouseY - panelHeight / 2;
        return clampToScreen(x, y, screenWidth, screenHeight, panelWidth, panelHeight);
    }

    /**
     * 发布当前固定 Jade 面板的左边缘，让顶部帮助文字在下一帧主动避让。
     * 占位会自动过期，准星离开目标后不会永久浪费顶部栏空间。
     */
    public static void publishAnchoredReservation(int actualLeftX, double renderScale) {
        double safeScale = sanitizeScale(renderScale);
        reservedLeftVirtualX = Math.max(0, (int) Math.floor(actualLeftX / safeScale));
        reservationExpiresAt = System.nanoTime() + RESERVATION_LIFETIME_NANOS;
    }

    /** 清除固定模式占位；跟随鼠标和隐藏模式都不应挤压顶部帮助文字。 */
    public static void clearReservation() {
        reservedLeftVirtualX = -1;
        reservationExpiresAt = 0L;
    }

    /**
     * 返回仍然有效的 RTS 虚拟坐标左边缘；没有 Jade 面板时返回 -1。
     */
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
