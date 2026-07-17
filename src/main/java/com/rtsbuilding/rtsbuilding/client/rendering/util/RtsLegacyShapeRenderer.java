package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 承接 26.1 移除的方框顶点帮助方法。
 *
 * <p>这里只生成调用方已经选择好的线/面缓冲顶点，不创建或结束缓冲，
 * 也不决定深度、混合和提交阶段。这样旧预览状态可以先迁移，而真正的
 * {@code SubmitCustomGeometryEvent} 所有权仍由更上层的渲染桥负责。
 */
public final class RtsLegacyShapeRenderer {
    private RtsLegacyShapeRenderer() {
    }

    public static void renderLineBox(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            float red, float green, float blue, float alpha) {
        line(poseStack, consumer, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        line(poseStack, consumer, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha);
        line(poseStack, consumer, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        line(poseStack, consumer, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha);
        line(poseStack, consumer, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(poseStack, consumer, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(poseStack, consumer, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        line(poseStack, consumer, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);
        line(poseStack, consumer, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        line(poseStack, consumer, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(poseStack, consumer, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(poseStack, consumer, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
    }

    public static void addChainedFilledBoxVertices(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            float red, float green, float blue, float alpha) {
        quad(poseStack, consumer, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ,
                0, -1, 0, red, green, blue, alpha);
        quad(poseStack, consumer, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ,
                0, 1, 0, red, green, blue, alpha);
        quad(poseStack, consumer, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ,
                -1, 0, 0, red, green, blue, alpha);
        quad(poseStack, consumer, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ,
                1, 0, 0, red, green, blue, alpha);
        quad(poseStack, consumer, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ,
                0, 0, -1, red, green, blue, alpha);
        quad(poseStack, consumer, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ,
                0, 0, 1, red, green, blue, alpha);
    }

    private static void line(PoseStack poseStack, VertexConsumer consumer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            float red, float green, float blue, float alpha) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 0.0F) {
            dx /= length;
            dy /= length;
            dz /= length;
        }
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(red, green, blue, alpha).setNormal(pose, dx, dy, dz).setLineWidth(1.0F);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(red, green, blue, alpha).setNormal(pose, dx, dy, dz).setLineWidth(1.0F);
    }

    private static void quad(PoseStack poseStack, VertexConsumer consumer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            double x3, double y3, double z3, double x4, double y4, double z4,
            float nx, float ny, float nz, float red, float green, float blue, float alpha) {
        vertex(poseStack, consumer, x1, y1, z1, nx, ny, nz, red, green, blue, alpha);
        vertex(poseStack, consumer, x2, y2, z2, nx, ny, nz, red, green, blue, alpha);
        vertex(poseStack, consumer, x3, y3, z3, nx, ny, nz, red, green, blue, alpha);
        vertex(poseStack, consumer, x4, y4, z4, nx, ny, nz, red, green, blue, alpha);
    }

    private static void vertex(PoseStack poseStack, VertexConsumer consumer,
            double x, double y, double z, float nx, float ny, float nz,
            float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha).setNormal(pose, nx, ny, nz);
    }
}
