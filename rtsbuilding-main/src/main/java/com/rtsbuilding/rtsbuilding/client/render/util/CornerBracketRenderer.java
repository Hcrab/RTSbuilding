package com.rtsbuilding.rtsbuilding.client.render.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class CornerBracketRenderer {

    
    private static final double BRACKET_THICKNESS = 0.04D;

    
    private static final double THICKNESS_SCALE_DISTANCE = 16.0D;

    
    private static final double MIN_THICKNESS_MULTIPLIER = 0.25D;

    private CornerBracketRenderer() {}

    
    public static float DEFAULT_NO_DEPTH_ALPHA = 0.10f;

    

    
    private static final double DASH_LEN = 0.3D;
    
    private static final double GAP_LEN = 0.2D;

    
    
    

    
    public static void renderDashedCornerBrackets(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float gapR, float gapG, float gapB, float a,
            double distance, double flowOffset) {
        double halfThick = BRACKET_THICKNESS
                * Math.max(MIN_THICKNESS_MULTIPLIER, 1.0D)
                * Math.max(1.0D, distance / THICKNESS_SCALE_DISTANCE) * 0.5D;

        
        drawDashedHorizontalRing(consumer, poseStack, minX, minZ, maxX, maxZ, minY, r, g, b, gapR, gapG, gapB, a, halfThick, flowOffset);
        drawDashedHorizontalRing(consumer, poseStack, minX, minZ, maxX, maxZ, maxY, r, g, b, gapR, gapG, gapB, a, halfThick, flowOffset);
        drawDashedVerticalEdges(consumer, poseStack, minX, minZ, maxX, maxZ, minY, maxY, r, g, b, gapR, gapG, gapB, a, halfThick, flowOffset);
    }

    
    public static void renderCornerBrackets(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b,
            double distance) {
        renderCornerBrackets(poseStack, consumer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 1.0F, distance);
    }

    
    public static void renderCornerBrackets(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float a,
            double distance) {
        renderCornerBrackets(poseStack, consumer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a, distance, 1.0D);
    }

    
    public static void renderCornerBrackets(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float a,
            double distance, double thicknessMultiplier) {
        double scaledThickness = BRACKET_THICKNESS
                * Math.max(MIN_THICKNESS_MULTIPLIER, thicknessMultiplier)
                * Math.max(1.0D, distance / THICKNESS_SCALE_DISTANCE);
        double halfThick = scaledThickness * 0.5D;

        
        drawHorizontalRing(consumer, poseStack, minX, minZ, maxX, maxZ, minY, r, g, b, a, halfThick);
        
        drawHorizontalRing(consumer, poseStack, minX, minZ, maxX, maxZ, maxY, r, g, b, a, halfThick);
        
        drawVerticalEdges(consumer, poseStack, minX, minZ, maxX, maxZ, minY, maxY, r, g, b, a, halfThick);
    }

    
    /**
     * 以当前项目边框风格（带厚度的粗线段）渲染一组外轮廓边线。
     * <p>用于连锁挖掘预览：通过 {@link UltimineBlockMerger#getEdgeLines} 提取的
     * 合并形状外轮廓边（内部边已被 VoxelShape 布尔合并消除），逐条绘制为粗线段，
     * 使整个连通区域呈现连续、完全合并的边框。</p>
     */
    public static void renderEdges(PoseStack poseStack, VertexConsumer consumer,
            List<UltimineBlockMerger.EdgeLine> edges,
            float r, float g, float b, float a, double distance) {
        if (edges == null || edges.isEmpty()) return;
        double scaledThickness = BRACKET_THICKNESS
                * Math.max(1.0D, distance / THICKNESS_SCALE_DISTANCE);
        double halfThick = scaledThickness * 0.5D;
        for (UltimineBlockMerger.EdgeLine edge : edges) {
            drawSegment(consumer, poseStack,
                    edge.x1(), edge.y1(), edge.z1(),
                    edge.x2(), edge.y2(), edge.z2(),
                    r, g, b, a, halfThick);
        }
    }

    
    public static void quad(VertexConsumer consumer, PoseStack poseStack,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double x3, double y3, double z3,
            double x4, double y4, double z4,
            float r, float g, float b, float a) {
        var pose = poseStack.last();
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
        consumer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(r, g, b, a);
        consumer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(r, g, b, a);
    }

    
    public static void renderFilledFaces(VertexConsumer consumer, PoseStack poseStack,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float a) {
        
        quad(consumer, poseStack, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        
        quad(consumer, poseStack, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, r, g, b, a);
        
        quad(consumer, poseStack, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        
        quad(consumer, poseStack, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        
        quad(consumer, poseStack, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        
        quad(consumer, poseStack, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, r, g, b, a);
    }

    
    
    

    private static void drawHorizontalRing(VertexConsumer consumer, PoseStack poseStack,
            double minX, double minZ, double maxX, double maxZ,
            double y, float r, float g, float b, float a, double t) {
        drawSegment(consumer, poseStack, minX, y, minZ, maxX, y, minZ, r, g, b, a, t);  
        drawSegment(consumer, poseStack, maxX, y, minZ, maxX, y, maxZ, r, g, b, a, t); 
        drawSegment(consumer, poseStack, maxX, y, maxZ, minX, y, maxZ, r, g, b, a, t);  
        drawSegment(consumer, poseStack, minX, y, maxZ, minX, y, minZ, r, g, b, a, t); 
    }

    private static void drawVerticalEdges(VertexConsumer consumer, PoseStack poseStack,
            double minX, double minZ, double maxX, double maxZ,
            double minY, double maxY, float r, float g, float b, float a, double t) {
        drawSegment(consumer, poseStack, minX, minY, minZ, minX, maxY, minZ, r, g, b, a, t);
        drawSegment(consumer, poseStack, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a, t);
        drawSegment(consumer, poseStack, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a, t);
        drawSegment(consumer, poseStack, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a, t);
    }

    
    private static void drawSegment(VertexConsumer consumer, PoseStack poseStack,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b, float a, double t) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-4) return;

        
        double nx = dx / len, ny = dy / len, nz = dz / len;

        
        double sx = x1 - nx * t, sy = y1 - ny * t, sz = z1 - nz * t;
        double ex = x2 + nx * t, ey = y2 + ny * t, ez = z2 + nz * t;

        
        double ux, uy, uz;
        double ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        if (ax <= ay && ax <= az) {
            ux = 0;  uy = nz;  uz = -ny;
        } else if (ay <= ax && ay <= az) {
            ux = -nz; uy = 0;   uz = nx;
        } else {
            ux = ny;  uy = -nx; uz = 0;
        }
        double uLen = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (uLen < 1.0e-8) return;
        ux /= uLen; uy /= uLen; uz /= uLen;

        
        double vx = ny * uz - nz * uy;
        double vy = nz * ux - nx * uz;
        double vz = nx * uy - ny * ux;

        
        
        double s1x = sx + ux * t + vx * t, s1y = sy + uy * t + vy * t, s1z = sz + uz * t + vz * t;
        double s2x = sx + ux * t - vx * t, s2y = sy + uy * t - vy * t, s2z = sz + uz * t - vz * t;
        double s3x = sx - ux * t - vx * t, s3y = sy - uy * t - vy * t, s3z = sz - uz * t - vz * t;
        double s4x = sx - ux * t + vx * t, s4y = sy - uy * t + vy * t, s4z = sz - uz * t + vz * t;
        
        double e1x = ex + ux * t + vx * t, e1y = ey + uy * t + vy * t, e1z = ez + uz * t + vz * t;
        double e2x = ex + ux * t - vx * t, e2y = ey + uy * t - vy * t, e2z = ez + uz * t - vz * t;
        double e3x = ex - ux * t - vx * t, e3y = ey - uy * t - vy * t, e3z = ez - uz * t - vz * t;
        double e4x = ex - ux * t + vx * t, e4y = ey - uy * t + vy * t, e4z = ez - uz * t + vz * t;

        
        quad(consumer, poseStack, s1x, s1y, s1z, s2x, s2y, s2z, s3x, s3y, s3z, s4x, s4y, s4z, r, g, b, a);
        quad(consumer, poseStack, e4x, e4y, e4z, e3x, e3y, e3z, e2x, e2y, e2z, e1x, e1y, e1z, r, g, b, a);
        quad(consumer, poseStack, s1x, s1y, s1z, e1x, e1y, e1z, e2x, e2y, e2z, s2x, s2y, s2z, r, g, b, a);
        quad(consumer, poseStack, s2x, s2y, s2z, e2x, e2y, e2z, e3x, e3y, e3z, s3x, s3y, s3z, r, g, b, a);
        quad(consumer, poseStack, s3x, s3y, s3z, e3x, e3y, e3z, e4x, e4y, e4z, s4x, s4y, s4z, r, g, b, a);
        quad(consumer, poseStack, s4x, s4y, s4z, e4x, e4y, e4z, e1x, e1y, e1z, s1x, s1y, s1z, r, g, b, a);
    }

    
    
    

    private static void drawDashedHorizontalRing(VertexConsumer consumer, PoseStack poseStack,
            double minX, double minZ, double maxX, double maxZ,
            double y, float r, float g, float b, float gapR, float gapG, float gapB, float a, double t, double flowOffset) {
        drawDashedSegment(consumer, poseStack, minX, y, minZ, maxX, y, minZ, r, g, b, gapR, gapG, gapB, a, t, flowOffset);
        drawDashedSegment(consumer, poseStack, maxX, y, minZ, maxX, y, maxZ, r, g, b, gapR, gapG, gapB, a, t, flowOffset);
        drawDashedSegment(consumer, poseStack, maxX, y, maxZ, minX, y, maxZ, r, g, b, gapR, gapG, gapB, a, t, flowOffset);
        drawDashedSegment(consumer, poseStack, minX, y, maxZ, minX, y, minZ, r, g, b, gapR, gapG, gapB, a, t, flowOffset);
    }

    private static void drawDashedVerticalEdges(VertexConsumer consumer, PoseStack poseStack,
            double minX, double minZ, double maxX, double maxZ,
            double minY, double maxY, float r, float g, float b, float gapR, float gapG, float gapB, float a, double t, double flowOffset) {
        drawDashedSegment(consumer, poseStack, minX, minY, minZ, minX, maxY, minZ, r, g, b, gapR, gapG, gapB, a, t, flowOffset);
        drawDashedSegment(consumer, poseStack, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, gapR, gapG, gapB, a, t, flowOffset);
        drawDashedSegment(consumer, poseStack, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, gapR, gapG, gapB, a, t, flowOffset);
        drawDashedSegment(consumer, poseStack, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, gapR, gapG, gapB, a, t, flowOffset);
    }

    
    private static void drawDashedSegment(VertexConsumer consumer, PoseStack poseStack,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b, float gapR, float gapG, float gapB, float a, double t, double flowOffset) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001) return;

        double step = DASH_LEN + GAP_LEN;
        double nx = dx / length;
        double ny = dy / length;
        double nz = dz / length;

        double pos = -flowOffset;
        while (pos < length) {
            
            double whiteEnd = Math.min(pos + DASH_LEN, length);
            if (whiteEnd > pos && whiteEnd > 0) {
                double realStart = Math.max(pos, 0.0);
                drawSegment(consumer, poseStack,
                        x1 + nx * realStart, y1 + ny * realStart, z1 + nz * realStart,
                        x1 + nx * whiteEnd, y1 + ny * whiteEnd, z1 + nz * whiteEnd,
                        r, g, b, a, t);
            }

            
            double blackStart = pos + DASH_LEN;
            double blackEnd = Math.min(pos + step, length);
            if (blackEnd > blackStart && blackEnd > 0) {
                double realStart = Math.max(blackStart, 0.0);
                drawSegment(consumer, poseStack,
                        x1 + nx * realStart, y1 + ny * realStart, z1 + nz * realStart,
                        x1 + nx * blackEnd, y1 + ny * blackEnd, z1 + nz * blackEnd,
                        gapR, gapG, gapB, a, t);
            }

            pos += step;
        }
    }

    

    
    public static final class Rgb {
        private int argb = Integer.MIN_VALUE;
        
        public float r;
        
        public float g;
        
        public float b;

        
        public void update(int newArgb) {
            if (this.argb == newArgb) return;
            this.argb = newArgb;
            this.r = ((newArgb >> 16) & 0xFF) / 255.0f;
            this.g = ((newArgb >> 8) & 0xFF) / 255.0f;
            this.b = (newArgb & 0xFF) / 255.0f;
        }
    }

    

    
    public static final class SmoothTarget {

        
        public static boolean enabled = true;

        private static final double LERP_SPEED = 0.15D;
        private static final double LERP_DISTANCE_FACTOR = 0.5D;

        private double animMinX, animMinY, animMinZ;
        private double animMaxX, animMaxY, animMaxZ;
        private boolean initialized;

        
        public void update(double targetMinX, double targetMinY, double targetMinZ,
                            double targetMaxX, double targetMaxY, double targetMaxZ) {
            if (!enabled || !initialized) {
                snapTo(targetMinX, targetMinY, targetMinZ, targetMaxX, targetMaxY, targetMaxZ);
                return;
            }

            double acx = (animMinX + animMaxX) / 2;
            double acy = (animMinY + animMaxY) / 2;
            double acz = (animMinZ + animMaxZ) / 2;
            double tcx = (targetMinX + targetMaxX) / 2;
            double tcy = (targetMinY + targetMaxY) / 2;
            double tcz = (targetMinZ + targetMaxZ) / 2;
            double dx = tcx - acx;
            double dy = tcy - acy;
            double dz = tcz - acz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double speed = Math.min(1.0, LERP_SPEED + dist * LERP_DISTANCE_FACTOR);

            animMinX += (targetMinX - animMinX) * speed;
            animMinY += (targetMinY - animMinY) * speed;
            animMinZ += (targetMinZ - animMinZ) * speed;
            animMaxX += (targetMaxX - animMaxX) * speed;
            animMaxY += (targetMaxY - animMaxY) * speed;
            animMaxZ += (targetMaxZ - animMaxZ) * speed;
        }

        public double minX() { return animMinX; }
        public double minY() { return animMinY; }
        public double minZ() { return animMinZ; }
        public double maxX() { return animMaxX; }
        public double maxY() { return animMaxY; }
        public double maxZ() { return animMaxZ; }

        
        public double centerDistanceTo(Vec3 point) {
            double cx = (animMinX + animMaxX) / 2;
            double cy = (animMinY + animMaxY) / 2;
            double cz = (animMinZ + animMaxZ) / 2;
            return point.distanceTo(new Vec3(cx, cy, cz));
        }

        
        public void reset() {
            this.initialized = false;
        }

        
        private void snapTo(double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ) {
            this.animMinX = minX; this.animMinY = minY; this.animMinZ = minZ;
            this.animMaxX = maxX; this.animMaxY = maxY; this.animMaxZ = maxZ;
            this.initialized = true;
        }
    }
}
