package com.rtsbuilding.rtsbuilding.client.render.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/**
 * 加厚角支架渲染器——在 AABB 的 12 条棱上画 L 型粗线框。
 *
 * <p>每条棱用六个四边形渲染成完整六面体棱柱（参考 effortless 的 {@code renderAACuboidLine}），
 * 从任何角度都保持均匀厚度。
 * 厚度随距离自动缩放（超过 {@link #THICKNESS_SCALE_DISTANCE} 格后线性增长），
 * 确保远处依然清晰可见。
 *
 * <p>所有方法均为静态，可直接复用。推荐用法：
 * <pre>{@code
 * // 深度检测层
 * CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
 *         minX, minY, minZ, maxX, maxY, maxZ,
 *         0.965f, 0.608f, 0.192f, 0.9f, distance);
 * // 无深度穿墙层
 * CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
 *         minX, minY, minZ, maxX, maxY, maxZ,
 *         0.965f, 0.608f, 0.192f, 0.32f, distance);
 * }</pre>
 */
public final class CornerBracketRenderer {

    /** 基础半厚度（世界单位） */
    private static final double BRACKET_THICKNESS = 0.04D;

    /**
     * 厚度开始缩放的距离阈值（格）。
     * 超过此距离后厚度线性增长，保持远处清晰。
     */
    private static final double THICKNESS_SCALE_DISTANCE = 16.0D;

    /** 厚度缩放最小值倍数，避免近处消失 */
    private static final double MIN_THICKNESS_MULTIPLIER = 0.25D;

    private CornerBracketRenderer() {}

    /** 无深度（穿墙）层的默认透明度，比深度层更淡避免遮挡视野 */
    public static float DEFAULT_NO_DEPTH_ALPHA = 0.10f;

    // ======================== 虚线渲染常量 ========================

    /** 虚线长度 */
    private static final double DASH_LEN = 0.3D;
    /** 虚线间隔 */
    private static final double GAP_LEN = 0.2D;

    // ──────────────────────────────────────────────
    //  公开 API
    // ──────────────────────────────────────────────

    /**
     * 渲染虚线角支架——在 12 条棱上交替绘制主色段和间隙色段，形成虚线效果。
     * <p>与 {@link #renderCornerBrackets} 相同的厚四边形风格，但每条棱由短段虚线组成。
     * 间隙段颜色由 gapR/gapG/gapB 指定。
     *
     * @param gapR       间隙段红色 [0,1]
     * @param gapG       间隙段绿色 [0,1]
     * @param gapB       间隙段蓝色 [0,1]
     * @param flowOffset 流动偏移量（块），随时间递增使虚线沿棱边流动
     */
    public static void renderDashedCornerBrackets(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float gapR, float gapG, float gapB, float a,
            double distance, double flowOffset) {
        double halfThick = BRACKET_THICKNESS
                * Math.max(MIN_THICKNESS_MULTIPLIER, 1.0D)
                * Math.max(1.0D, distance / THICKNESS_SCALE_DISTANCE) * 0.5D;

        // 12 条棱，每条虚线绘制
        drawDashedHorizontalRing(consumer, poseStack, minX, minZ, maxX, maxZ, minY, r, g, b, gapR, gapG, gapB, a, halfThick, flowOffset);
        drawDashedHorizontalRing(consumer, poseStack, minX, minZ, maxX, maxZ, maxY, r, g, b, gapR, gapG, gapB, a, halfThick, flowOffset);
        drawDashedVerticalEdges(consumer, poseStack, minX, minZ, maxX, maxZ, minY, maxY, r, g, b, gapR, gapG, gapB, a, halfThick, flowOffset);
    }

    /**
     * 渲染角支架（默认 alpha = 1.0）。
     *
     * @param poseStack 变换栈
     * @param consumer  顶点消费者
     * @param minX      AABB 最小 X
     * @param minY      AABB 最小 Y
     * @param minZ      AABB 最小 Z
     * @param maxX      AABB 最大 X
     * @param maxY      AABB 最大 Y
     * @param maxZ      AABB 最大 Z
     * @param r         红色 [0,1]
     * @param g         绿色 [0,1]
     * @param b         蓝色 [0,1]
     * @param distance  相机到目标距离（用于厚度缩放）
     */
    public static void renderCornerBrackets(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b,
            double distance) {
        renderCornerBrackets(poseStack, consumer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 1.0F, distance);
    }

    /**
     * 渲染角支架（带透明度）。
     *
     * @param poseStack 变换栈
     * @param consumer  顶点消费者
     * @param minX      AABB 最小 X
     * @param minY      AABB 最小 Y
     * @param minZ      AABB 最小 Z
     * @param maxX      AABB 最大 X
     * @param maxY      AABB 最大 Y
     * @param maxZ      AABB 最大 Z
     * @param r         红色 [0,1]
     * @param g         绿色 [0,1]
     * @param b         蓝色 [0,1]
     * @param a         透明度 [0,1]
     * @param distance  相机到目标距离（用于厚度缩放）
     */
    public static void renderCornerBrackets(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float a,
            double distance) {
        renderCornerBrackets(poseStack, consumer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a, distance, 1.0D);
    }

    /**
     * 渲染角支架（完整参数版）。
     *
     * @param poseStack  变换栈
     * @param consumer   顶点消费者
     * @param minX       AABB 最小 X
     * @param minY       AABB 最小 Y
     * @param minZ       AABB 最小 Z
     * @param maxX       AABB 最大 X
     * @param maxY       AABB 最大 Y
     * @param maxZ       AABB 最大 Z
     * @param r          红色 [0,1]
     * @param g          绿色 [0,1]
     * @param b          蓝色 [0,1]
     * @param a          透明度 [0,1]
     * @param distance   相机到目标距离（用于厚度缩放）
     * @param thicknessMultiplier 厚度倍率（>1 加粗，<1 变细，最小 clamp 到 0.25）
     */
    public static void renderCornerBrackets(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float a,
            double distance, double thicknessMultiplier) {

        double scaledThickness = BRACKET_THICKNESS
                * Math.max(MIN_THICKNESS_MULTIPLIER, thicknessMultiplier)
                * Math.max(1.0D, distance / THICKNESS_SCALE_DISTANCE);
        double halfThick = scaledThickness * 0.5D;

        // 底部水平环
        drawHorizontalRing(consumer, poseStack, minX, minZ, maxX, maxZ, minY, r, g, b, a, halfThick);
        // 顶部水平环
        drawHorizontalRing(consumer, poseStack, minX, minZ, maxX, maxZ, maxY, r, g, b, a, halfThick);
        // 四个垂直边
        drawVerticalEdges(consumer, poseStack, minX, minZ, maxX, maxZ, minY, maxY, r, g, b, a, halfThick);
    }

    /**
     * 提交一个四边形到 VertexConsumer（通用工具）。
     * 四个顶点按顺序连接，形成单面四边形。
     */
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

    /**
     * 渲染 AABB 的六个面，使用半透明颜色填充。
     * 用于框选模式的蓝色透明覆盖层，直观展示选区范围。
     *
     * @param consumer  顶点消费者
     * @param poseStack 变换栈
     * @param minX      AABB 最小 X
     * @param minY      AABB 最小 Y
     * @param minZ      AABB 最小 Z
     * @param maxX      AABB 最大 X
     * @param maxY      AABB 最大 Y
     * @param maxZ      AABB 最大 Z
     * @param r         红色 [0,1]
     * @param g         绿色 [0,1]
     * @param b         蓝色 [0,1]
     * @param a         透明度 [0,1]
     */
    public static void renderFilledFaces(VertexConsumer consumer, PoseStack poseStack,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float a) {
        // 顶面 (y = maxY)
        quad(consumer, poseStack, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        // 底面 (y = minY)
        quad(consumer, poseStack, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, r, g, b, a);
        // 正面 (z = maxZ)
        quad(consumer, poseStack, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        // 背面 (z = minZ)
        quad(consumer, poseStack, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        // 右面 (x = maxX)
        quad(consumer, poseStack, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        // 左面 (x = minX)
        quad(consumer, poseStack, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, r, g, b, a);
    }

    // ──────────────────────────────────────────────
    //  内部渲染逻辑
    // ──────────────────────────────────────────────

    private static void drawHorizontalRing(VertexConsumer consumer, PoseStack poseStack,
            double minX, double minZ, double maxX, double maxZ,
            double y, float r, float g, float b, float a, double t) {
        drawSegment(consumer, poseStack, minX, y, minZ, maxX, y, minZ, r, g, b, a, t);  // 前
        drawSegment(consumer, poseStack, maxX, y, minZ, maxX, y, maxZ, r, g, b, a, t); // 右
        drawSegment(consumer, poseStack, maxX, y, maxZ, minX, y, maxZ, r, g, b, a, t);  // 后
        drawSegment(consumer, poseStack, minX, y, maxZ, minX, y, minZ, r, g, b, a, t); // 左
    }

    private static void drawVerticalEdges(VertexConsumer consumer, PoseStack poseStack,
            double minX, double minZ, double maxX, double maxZ,
            double minY, double maxY, float r, float g, float b, float a, double t) {
        drawSegment(consumer, poseStack, minX, minY, minZ, minX, maxY, minZ, r, g, b, a, t);
        drawSegment(consumer, poseStack, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a, t);
        drawSegment(consumer, poseStack, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a, t);
        drawSegment(consumer, poseStack, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a, t);
    }

    /**
     * 绘制粗线段——用六个四边形渲染完整六面体棱柱（匹配 effortless 的 renderAACuboidLine 风格）。
     * <p>从线段方向自动计算垂直截面，首尾两端各延伸半厚度形成端盖，
     * 确保从任何角度观察都呈现均匀的实体厚度。
     */
    private static void drawSegment(VertexConsumer consumer, PoseStack poseStack,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b, float a, double t) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-4) return;

        // 单位方向向量
        double nx = dx / len, ny = dy / len, nz = dz / len;

        // 首尾两端按半厚度延伸（参照 effortless 的 extension 逻辑）
        double sx = x1 - nx * t, sy = y1 - ny * t, sz = z1 - nz * t;
        double ex = x2 + nx * t, ey = y2 + ny * t, ez = z2 + nz * t;

        // 计算垂直于线段方向的 u（up）向量：叉积取最小的轴
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

        // v = 方向 × u（已归一化）
        double vx = ny * uz - nz * uy;
        double vy = nz * ux - nx * uz;
        double vz = nx * uy - ny * ux;

        // 四个角点 = 中心 ± u*t ± v*t
        // 起始端截面
        double s1x = sx + ux * t + vx * t, s1y = sy + uy * t + vy * t, s1z = sz + uz * t + vz * t;
        double s2x = sx + ux * t - vx * t, s2y = sy + uy * t - vy * t, s2z = sz + uz * t - vz * t;
        double s3x = sx - ux * t - vx * t, s3y = sy - uy * t - vy * t, s3z = sz - uz * t - vz * t;
        double s4x = sx - ux * t + vx * t, s4y = sy - uy * t + vy * t, s4z = sz - uz * t + vz * t;
        // 结束端截面
        double e1x = ex + ux * t + vx * t, e1y = ey + uy * t + vy * t, e1z = ez + uz * t + vz * t;
        double e2x = ex + ux * t - vx * t, e2y = ey + uy * t - vy * t, e2z = ez + uz * t - vz * t;
        double e3x = ex - ux * t - vx * t, e3y = ey - uy * t - vy * t, e3z = ez - uz * t - vz * t;
        double e4x = ex - ux * t + vx * t, e4y = ey - uy * t + vy * t, e4z = ez - uz * t + vz * t;

        // 六个面（start cap, end cap, 4 个侧面）
        quad(consumer, poseStack, s1x, s1y, s1z, s2x, s2y, s2z, s3x, s3y, s3z, s4x, s4y, s4z, r, g, b, a);
        quad(consumer, poseStack, e4x, e4y, e4z, e3x, e3y, e3z, e2x, e2y, e2z, e1x, e1y, e1z, r, g, b, a);
        quad(consumer, poseStack, s1x, s1y, s1z, e1x, e1y, e1z, e2x, e2y, e2z, s2x, s2y, s2z, r, g, b, a);
        quad(consumer, poseStack, s2x, s2y, s2z, e2x, e2y, e2z, e3x, e3y, e3z, s3x, s3y, s3z, r, g, b, a);
        quad(consumer, poseStack, s3x, s3y, s3z, e3x, e3y, e3z, e4x, e4y, e4z, s4x, s4y, s4z, r, g, b, a);
        quad(consumer, poseStack, s4x, s4y, s4z, e4x, e4y, e4z, e1x, e1y, e1z, s1x, s1y, s1z, r, g, b, a);
    }

    // ──────────────────────────────────────────────
    //  虚线渲染
    // ──────────────────────────────────────────────

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

    /** 绘制一条虚线边——主色段和间隙色段交替 */
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
            // 白色段
            double whiteEnd = Math.min(pos + DASH_LEN, length);
            if (whiteEnd > pos && whiteEnd > 0) {
                double realStart = Math.max(pos, 0.0);
                drawSegment(consumer, poseStack,
                        x1 + nx * realStart, y1 + ny * realStart, z1 + nz * realStart,
                        x1 + nx * whiteEnd, y1 + ny * whiteEnd, z1 + nz * whiteEnd,
                        r, g, b, a, t);
            }

            // 间隙段
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

    // ======================== ARGB 颜色缓存 ========================

    /**
     * ARGB 颜色→float RGB 分量缓存。颜色值不变时跳过位运算，零分配。
     * <p>各 Pass 中每帧调用一次 {@link #update(int)}，后续直接读取 {@link #r}、{@link #g}、{@link #b}。
     * <pre>{@code
     * private static final Rgb selColor = new Rgb();
     * // in render():
     * selColor.update(selectionColor);
     * // use selColor.r, selColor.g, selColor.b
     * }</pre>
     */
    public static final class Rgb {
        private int argb = Integer.MIN_VALUE;
        /** 红色分量 [0,1] */
        public float r;
        /** 绿色分量 [0,1] */
        public float g;
        /** 蓝色分量 [0,1] */
        public float b;

        /**
         * 更新缓存。如果 argb 与上次相同则跳过位运算。
         * @param newArgb 新的 ARGB 颜色值
         */
        public void update(int newArgb) {
            if (this.argb == newArgb) return;
            this.argb = newArgb;
            this.r = ((newArgb >> 16) & 0xFF) / 255.0f;
            this.g = ((newArgb >> 8) & 0xFF) / 255.0f;
            this.b = (newArgb & 0xFF) / 255.0f;
        }
    }

    // ======================== 平滑插值目标 ========================

    /**
     * 平滑插值目标——对包围盒做帧间指数平滑过渡，避免目标切换时瞬间跳跃。
     * 距离越远插值速度越快，保证快速移动时跟手；近距离平滑收尾。
     * <p>使用方各自维护独立的 {@code SmoothTarget} 实例。</p>
     */
    public static final class SmoothTarget {

        /** 平滑动画全局开关（默认开启），由渲染设置面板控制 */
        public static boolean enabled = true;

        private static final double LERP_SPEED = 0.15D;
        private static final double LERP_DISTANCE_FACTOR = 0.5D;

        private double animMinX, animMinY, animMinZ;
        private double animMaxX, animMaxY, animMaxZ;
        private boolean initialized;

        /** 更新目标包围盒并执行一帧插值，之后通过 getter 获取动画位置 */
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

        /** 计算动画中心到指定点的距离，用于渲染厚度缩放 */
        public double centerDistanceTo(Vec3 point) {
            double cx = (animMinX + animMaxX) / 2;
            double cy = (animMinY + animMaxY) / 2;
            double cz = (animMinZ + animMaxZ) / 2;
            return point.distanceTo(new Vec3(cx, cy, cz));
        }

        /** 重置为未初始化状态，下次 update() 直接跳到目标位置 */
        public void reset() {
            this.initialized = false;
        }

        /** 直接跳到目标包围盒（不经过插值） */
        private void snapTo(double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ) {
            this.animMinX = minX; this.animMinY = minY; this.animMinZ = minZ;
            this.animMaxX = maxX; this.animMaxY = maxY; this.animMaxZ = maxZ;
            this.initialized = true;
        }
    }
}
