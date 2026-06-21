package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Draws thick quad-based corner brackets around a world-space AABB.
 *
 * <p>This utility owns only bracket geometry. It does not decide which target is
 * selected, whether the target is inside the RTS build area, or which render
 * buffer should be used. Keeping those decisions in the overlay renderers lets
 * Forge 1.20.1 share the same player-facing highlight language as main while
 * still using the older BufferBuilder API.
 */
public final class CornerBracketRenderer {
    private static final double BRACKET_THICKNESS = 0.04D;
    private static final double THICKNESS_SCALE_DISTANCE = 16.0D;

    private enum Axis {
        X,
        Y,
        Z
    }

    private CornerBracketRenderer() {
    }

    public static void renderCornerBrackets(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b,
            double distance) {
        renderCornerBrackets(poseStack, consumer, minX, minY, minZ, maxX, maxY, maxZ,
                r, g, b, 1.0F, distance);
    }

    public static void renderCornerBrackets(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float a,
            double distance) {
        double scaledThickness = BRACKET_THICKNESS * Math.max(1.0D, distance / THICKNESS_SCALE_DISTANCE);
        double halfThickness = scaledThickness * 0.5D;

        drawHorizontalRing(consumer, poseStack, minX, minZ, maxX, maxZ, minY, r, g, b, a, halfThickness);
        drawHorizontalRing(consumer, poseStack, minX, minZ, maxX, maxZ, maxY, r, g, b, a, halfThickness);
        drawVerticalEdges(consumer, poseStack, minX, minZ, maxX, maxZ, minY, maxY, r, g, b, a, halfThickness);
    }

    private static void drawHorizontalRing(VertexConsumer consumer, PoseStack poseStack,
            double minX, double minZ, double maxX, double maxZ,
            double y, float r, float g, float b, float a, double t) {
        drawBracketSegment(consumer, poseStack, minX, y, minZ, maxX, y, minZ, r, g, b, a, Axis.X, t);
        drawBracketSegment(consumer, poseStack, maxX, y, minZ, maxX, y, maxZ, r, g, b, a, Axis.Z, t);
        drawBracketSegment(consumer, poseStack, maxX, y, maxZ, minX, y, maxZ, r, g, b, a, Axis.X, t);
        drawBracketSegment(consumer, poseStack, minX, y, maxZ, minX, y, minZ, r, g, b, a, Axis.Z, t);
    }

    private static void drawVerticalEdges(VertexConsumer consumer, PoseStack poseStack,
            double minX, double minZ, double maxX, double maxZ,
            double minY, double maxY, float r, float g, float b, float a, double t) {
        drawBracketSegment(consumer, poseStack, minX, minY, minZ, minX, maxY, minZ, r, g, b, a, Axis.Y, t);
        drawBracketSegment(consumer, poseStack, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a, Axis.Y, t);
        drawBracketSegment(consumer, poseStack, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a, Axis.Y, t);
        drawBracketSegment(consumer, poseStack, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a, Axis.Y, t);
    }

    private static void drawBracketSegment(VertexConsumer consumer, PoseStack poseStack,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b, float a, Axis axis, double t) {
        switch (axis) {
            case X -> {
                RenderingUtil.quad(consumer, poseStack,
                        x1, y1 - t, z1,
                        x1, y1 + t, z1,
                        x2, y2 + t, z2,
                        x2, y2 - t, z2, r, g, b, a);
                RenderingUtil.quad(consumer, poseStack,
                        x1, y1, z1 - t,
                        x1, y1, z1 + t,
                        x2, y2, z2 + t,
                        x2, y2, z2 - t, r, g, b, a);
            }
            case Y -> {
                RenderingUtil.quad(consumer, poseStack,
                        x1, y1, z1 - t,
                        x1, y1, z1 + t,
                        x2, y2, z2 + t,
                        x2, y2, z2 - t, r, g, b, a);
                RenderingUtil.quad(consumer, poseStack,
                        x1 - t, y1, z1,
                        x1 + t, y1, z1,
                        x2 + t, y2, z2,
                        x2 - t, y2, z2, r, g, b, a);
            }
            case Z -> {
                RenderingUtil.quad(consumer, poseStack,
                        x1 - t, y1, z1,
                        x1 + t, y1, z1,
                        x2 + t, y2, z2,
                        x2 - t, y2, z2, r, g, b, a);
                RenderingUtil.quad(consumer, poseStack,
                        x1, y1 - t, z1,
                        x1, y1 + t, z1,
                        x2, y2 + t, z2,
                        x2, y2 - t, z2, r, g, b, a);
            }
        }
    }
}
