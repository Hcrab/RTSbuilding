package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.rtsbuilding.rtsbuilding.platform.render.BufferBuilder;

/**
 * 1.7.10 私有顶点缓冲使用的盒体几何生成器。
 *
 * <p>后续版本把这两个方法放在 RenderGlobal/LevelRenderer 上；1.7.10 没有对应 API。
 * 这里明确输出独立的六个四边形和十二条边，调用方统一使用 GL_QUADS/GL_LINES，
 * 不依赖跨盒连续 strip，也就不会因为批量预览中断而产生贯穿画面的斜线。
 */
public final class LegacyRenderGeometry {
    private LegacyRenderGeometry() {}

    public static void addChainedFilledBoxVertices(BufferBuilder buffer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float red, float green, float blue, float alpha) {
        quad(buffer, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        quad(buffer, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, red, green, blue, alpha);
        quad(buffer, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        quad(buffer, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        quad(buffer, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);
        quad(buffer, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, red, green, blue, alpha);
    }

    public static void drawBoundingBox(BufferBuilder buffer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float red, float green, float blue, float alpha) {
        line(buffer, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        line(buffer, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha);
        line(buffer, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        line(buffer, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha);
        line(buffer, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(buffer, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(buffer, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        line(buffer, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);
        line(buffer, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        line(buffer, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(buffer, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(buffer, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
    }

    private static void quad(BufferBuilder buffer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            double x3, double y3, double z3, double x4, double y4, double z4,
            float red, float green, float blue, float alpha) {
        vertex(buffer, x1, y1, z1, red, green, blue, alpha);
        vertex(buffer, x2, y2, z2, red, green, blue, alpha);
        vertex(buffer, x3, y3, z3, red, green, blue, alpha);
        vertex(buffer, x4, y4, z4, red, green, blue, alpha);
    }

    private static void line(BufferBuilder buffer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            float red, float green, float blue, float alpha) {
        vertex(buffer, x1, y1, z1, red, green, blue, alpha);
        vertex(buffer, x2, y2, z2, red, green, blue, alpha);
    }

    private static void vertex(BufferBuilder buffer, double x, double y, double z,
            float red, float green, float blue, float alpha) {
        buffer.pos(x, y, z).color(red, green, blue, alpha).endVertex();
    }
}
