package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;

/**
 * Renders thickened corner brackets around an AABB using quad-based geometry.
 *
 * <p>Each bracket segment is drawn as two perpendicular quads forming a cross-shaped
 * cross-section, giving visible thickness from any viewing angle. The thickness
 * automatically scales with distance beyond {@link #THICKNESS_SCALE_DISTANCE} so
 * brackets remain clearly visible far away.
 *
 * <p>All methods are static; this class is never instantiated.
 */
public final class CornerBracketRenderer {

    /** Base half-thickness (in world units) of each bracket quad. */
    private static final double BRACKET_THICKNESS = 0.04D;

    /**
     * Distance threshold (in blocks) at which bracket thickness begins to scale up.
     * Beyond this distance the thickness grows proportionally so brackets stay visible.
     */
    private static final double THICKNESS_SCALE_DISTANCE = 16.0D;

    /** Axis along which a bracket segment extends. Used to determine which quad faces to draw. */
    private enum Axis { X, Y, Z }

    private CornerBracketRenderer() {
    }

    /**
     * Renders thickened corner brackets around an AABB using quad-based geometry.
     *
     * @param poseStack current transformation stack
     * @param consumer  vertex consumer for bracket quads
     * @param minX      AABB minimum X (world space)
     * @param minY      AABB minimum Y (world space)
     * @param minZ      AABB minimum Z (world space)
     * @param maxX      AABB maximum X (world space)
     * @param maxY      AABB maximum Y (world space)
     * @param maxZ      AABB maximum Z (world space)
     * @param r         red   colour component [0, 1]
     * @param g         green colour component [0, 1]
     * @param b         blue  colour component [0, 1]
     * @param distance  camera-to-target distance (used for thickness scaling)
     */
    public static void renderCornerBrackets(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b,
            double distance) {
        renderCornerBrackets(poseStack, consumer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 1.0F, distance);
    }

    /**
     * Renders thickened corner brackets with configurable alpha.
     *
     * @param poseStack current transformation stack
     * @param consumer  vertex consumer for bracket quads
     * @param minX      AABB minimum X (world space)
     * @param minY      AABB minimum Y (world space)
     * @param minZ      AABB minimum Z (world space)
     * @param maxX      AABB maximum X (world space)
     * @param maxY      AABB maximum Y (world space)
     * @param maxZ      AABB maximum Z (world space)
     * @param r         red   colour component [0, 1]
     * @param g         green colour component [0, 1]
     * @param b         blue  colour component [0, 1]
     * @param a         alpha component [0, 1]
     * @param distance  camera-to-target distance (used for thickness scaling)
     */
    public static void renderCornerBrackets(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float a,
            double distance) {

        double scaledThickness = BRACKET_THICKNESS * Math.max(1.0D, distance / THICKNESS_SCALE_DISTANCE);
        double halfThick = scaledThickness * 0.5D;

        // Bottom horizontal ring
        drawHorizontalRing(consumer, poseStack, minX, minZ, maxX, maxZ, minY, r, g, b, a, halfThick);
        // Top horizontal ring
        drawHorizontalRing(consumer, poseStack, minX, minZ, maxX, maxZ, maxY, r, g, b, a, halfThick);
        // Vertical edges at the four corners
        drawVerticalEdges(consumer, poseStack, minX, minZ, maxX, maxZ, minY, maxY, r, g, b, a, halfThick);
    }

    /**
     * Draws the four horizontal bracket segments at a given Y-level, forming a rectangular
     * ring. Each segment is a thickened quad extruded along the plane's dominant axis.
     */
    private static void drawHorizontalRing(VertexConsumer consumer, PoseStack poseStack,
            double minX, double minZ, double maxX, double maxZ,
            double y, float r, float g, float b, float a, double t) {
        // X-aligned segment at Z = minZ  (front)
        drawBracketSegment(consumer, poseStack, minX, y, minZ, maxX, y, minZ, r, g, b, a, Axis.X, t);
        // Z-aligned segment at X = maxX  (right)
        drawBracketSegment(consumer, poseStack, maxX, y, minZ, maxX, y, maxZ, r, g, b, a, Axis.Z, t);
        // X-aligned segment at Z = maxZ  (back)
        drawBracketSegment(consumer, poseStack, maxX, y, maxZ, minX, y, maxZ, r, g, b, a, Axis.X, t);
        // Z-aligned segment at X = minX  (left)
        drawBracketSegment(consumer, poseStack, minX, y, maxZ, minX, y, minZ, r, g, b, a, Axis.Z, t);
    }

    /**
     * Draws the four vertical bracket segments at the four corners of the AABB.
     */
    private static void drawVerticalEdges(VertexConsumer consumer, PoseStack poseStack,
            double minX, double minZ, double maxX, double maxZ,
            double minY, double maxY, float r, float g, float b, float a, double t) {
        // Y-aligned at (minX, minZ)
        drawBracketSegment(consumer, poseStack, minX, minY, minZ, minX, maxY, minZ, r, g, b, a, Axis.Y, t);
        // Y-aligned at (maxX, minZ)
        drawBracketSegment(consumer, poseStack, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a, Axis.Y, t);
        // Y-aligned at (maxX, maxZ)
        drawBracketSegment(consumer, poseStack, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a, Axis.Y, t);
        // Y-aligned at (minX, maxZ)
        drawBracketSegment(consumer, poseStack, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a, Axis.Y, t);
    }

    /**
     * Draws a single thickened bracket segment from (x1, y1, z1) to (x2, y2, z2).
     * The segment is rendered as two perpendicular quads that form a cross-shaped cross-section,
     * giving the line visible thickness from any viewing angle.
     */
    private static void drawBracketSegment(VertexConsumer consumer, PoseStack poseStack,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b, float a, Axis axis,
            double t) {
        switch (axis) {
            case X -> {
                // Quad expanding in Y: faces YZ-plane
                RenderingUtil.quad(consumer, poseStack,
                        x1, y1 - t, z1,
                        x1, y1 + t, z1,
                        x2, y2 + t, z2,
                        x2, y2 - t, z2, r, g, b, a);
                // Quad expanding in Z: faces XY-plane
                RenderingUtil.quad(consumer, poseStack,
                        x1, y1, z1 - t,
                        x1, y1, z1 + t,
                        x2, y2, z2 + t,
                        x2, y2, z2 - t, r, g, b, a);
            }
            case Y -> {
                // Quad expanding in Z: faces XZ-plane
                RenderingUtil.quad(consumer, poseStack,
                        x1, y1, z1 - t,
                        x1, y1, z1 + t,
                        x2, y2, z2 + t,
                        x2, y2, z2 - t, r, g, b, a);
                // Quad expanding in X: faces YZ-plane
                RenderingUtil.quad(consumer, poseStack,
                        x1 - t, y1, z1,
                        x1 + t, y1, z1,
                        x2 + t, y2, z2,
                        x2 - t, y2, z2, r, g, b, a);
            }
            case Z -> {
                // Quad expanding in X: faces YZ-plane
                RenderingUtil.quad(consumer, poseStack,
                        x1 - t, y1, z1,
                        x1 + t, y1, z1,
                        x2 + t, y2, z2,
                        x2 - t, y2, z2, r, g, b, a);
                // Quad expanding in Y: faces XZ-plane
                RenderingUtil.quad(consumer, poseStack,
                        x1, y1 - t, z1,
                        x1, y1 + t, z1,
                        x2, y2 + t, z2,
                        x2, y2 - t, z2, r, g, b, a);
            }
        }
    }
}
