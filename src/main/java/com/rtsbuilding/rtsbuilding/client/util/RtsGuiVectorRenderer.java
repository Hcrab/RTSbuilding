package com.rtsbuilding.rtsbuilding.client.util;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * RTS 界面的轻量几何图形绘制器。
 *
 * <p>这里有意只通过 {@link GuiGraphics#fill(int, int, int, int, int)} 绘制，不接管 Minecraft
 * 的共享渲染缓冲，也不主动 flush。圆形先在两倍坐标空间中生成，再缩回 GUI 坐标，并用一层
 * 半透明羽化边缘减轻整数扫描线造成的锯齿。它负责视觉几何，不负责轮盘状态或输入命中。</p>
 */
public final class RtsGuiVectorRenderer {
    private static final int SUBPIXEL_SCALE = 2;
    private static final float INVERSE_SUBPIXEL_SCALE = 1.0F / SUBPIXEL_SCALE;

    private RtsGuiVectorRenderer() {
    }

    /**
     * 绘制带亚像素羽化边缘的实心圆盘。
     */
    public static void fillDisc(
            GuiGraphics graphics,
            float centerX,
            float centerY,
            float radius,
            int color) {
        if (radius <= 0.0F || ((color >>> 24) & 0xFF) == 0) {
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0.0F);
        pose.scale(INVERSE_SUBPIXEL_SCALE, INVERSE_SUBPIXEL_SCALE, 1.0F);
        int scaledRadius = Math.max(1, Math.round(radius * SUBPIXEL_SCALE));
        drawDiscAtOrigin(graphics, scaledRadius + 1, multiplyAlpha(color, 0.24F));
        drawDiscAtOrigin(graphics, scaledRadius, color);
        pose.popPose();
    }

    /**
     * 绘制透明中心的圆环；低透明度的外层用于让细线边缘保持平滑。
     */
    public static void drawRing(
            GuiGraphics graphics,
            float centerX,
            float centerY,
            float radius,
            float thickness,
            int color) {
        if (radius <= 0.0F || thickness <= 0.0F || ((color >>> 24) & 0xFF) == 0) {
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0.0F);
        pose.scale(INVERSE_SUBPIXEL_SCALE, INVERSE_SUBPIXEL_SCALE, 1.0F);
        int scaledRadius = Math.max(1, Math.round(radius * SUBPIXEL_SCALE));
        int scaledThickness = Math.max(1, Math.round(thickness * SUBPIXEL_SCALE));
        drawRingAtOrigin(
                graphics,
                scaledRadius + 1,
                scaledThickness + 2,
                multiplyAlpha(color, 0.22F));
        drawRingAtOrigin(graphics, scaledRadius, scaledThickness, color);
        pose.popPose();
    }

    /**
     * 绘制胶囊形文字底板，供轮盘标签使用。
     */
    public static void fillCapsule(
            GuiGraphics graphics,
            int left,
            int right,
            float centerY,
            float height,
            int color) {
        if (right <= left || height <= 0.0F) {
            return;
        }
        float radius = height * 0.5F;
        float leftCenter = left + radius;
        float rightCenter = right - radius;
        if (rightCenter <= leftCenter) {
            fillDisc(graphics, (left + right) * 0.5F, centerY, (right - left) * 0.5F, color);
            return;
        }
        graphics.fill(
                Math.round(leftCenter),
                Math.round(centerY - radius),
                Math.round(rightCenter),
                Math.round(centerY + radius),
                color);
        fillDisc(graphics, leftCenter, centerY, radius, color);
        fillDisc(graphics, rightCenter, centerY, radius, color);
    }

    private static void drawDiscAtOrigin(GuiGraphics graphics, int radius, int color) {
        int radiusSquared = radius * radius;
        for (int y = -radius; y <= radius; y++) {
            int halfWidth = (int) Math.floor(Math.sqrt(Math.max(0, radiusSquared - y * y)));
            graphics.fill(-halfWidth, y, halfWidth + 1, y + 1, color);
        }
    }

    private static void drawRingAtOrigin(
            GuiGraphics graphics,
            int outerRadius,
            int thickness,
            int color) {
        int innerRadius = Math.max(0, outerRadius - thickness);
        int outerSquared = outerRadius * outerRadius;
        int innerSquared = innerRadius * innerRadius;
        for (int y = -outerRadius; y <= outerRadius; y++) {
            int outerHalf = (int) Math.floor(
                    Math.sqrt(Math.max(0, outerSquared - y * y)));
            if (Math.abs(y) >= innerRadius || innerRadius == 0) {
                graphics.fill(-outerHalf, y, outerHalf + 1, y + 1, color);
                continue;
            }
            int innerHalf = (int) Math.floor(
                    Math.sqrt(Math.max(0, innerSquared - y * y)));
            graphics.fill(-outerHalf, y, -innerHalf, y + 1, color);
            graphics.fill(innerHalf + 1, y, outerHalf + 1, y + 1, color);
        }
    }

    private static int multiplyAlpha(int color, float multiplier) {
        int alpha = Math.round(
                ((color >>> 24) & 0xFF) * Mth.clamp(multiplier, 0.0F, 1.0F));
        return color & 0x00FFFFFF | alpha << 24;
    }
}
