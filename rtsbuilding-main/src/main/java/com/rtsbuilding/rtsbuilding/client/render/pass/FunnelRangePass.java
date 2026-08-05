package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.PerformanceConfig;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 漏斗（物品拾取）收集范围显示 pass。
 *
 * <p>当处于<b>交互模式</b>、已启用<b>点击模式</b>且开启<b>物品拾取（漏斗）</b>时，
 * 以鼠标指针指向的方块为中心绘制球形收集范围，与服务端 {@code RtsFunnelService}
 * 的球心吸取范围（半径 2 格）保持一致；指针移动时球体平滑跟随。
 *
 * <p>球体渲染：三条两两垂直的赤道大圆线框（LINES 细线）
 * + 半透明球壳面片（QUADS，增强体积感）。
 */
public final class FunnelRangePass implements RenderPass {

    /** 球体半径（格），与服务端 RtsFunnelService 的吸取半径保持一致。 */
    public static final double SPHERE_RADIUS = 2.0D;

    /** 每条大圆的分段数（7.5° 步长，弦长约 0.26 格，视觉上足够圆滑）。 */
    private static final int SEGMENTS = 48;

    /** 球壳表面经度细分段数。 */
    private static final int SHELL_LON_SEGMENTS = 24;

    /** 球壳表面纬度细分段数。 */
    private static final int SHELL_LAT_SEGMENTS = 12;

    /** 经纬网格线 alpha（LINES，带深度）。 */
    private static final float GRID_ALPHA = 0.9f;

    /** 球壳填充 alpha（QUADS，带深度）。 */
    private static final float SHELL_DEPTH_ALPHA = 0.10f;

    /** 球壳填充 alpha（QUADS，穿透无深度）。 */
    private static final float SHELL_NO_DEPTH_ALPHA = 0.05f;

    /** 球体线框颜色（拾取绿，可被外部主题调整）。 */
    public static int rangeColor = 0xFF4CAF50;

    private static final CornerBracketRenderer.Rgb rangeRgb = new CornerBracketRenderer.Rgb();
    private static final CornerBracketRenderer.SmoothTarget smoothTarget = new CornerBracketRenderer.SmoothTarget();

    @Override
    public boolean shouldRender(Minecraft mc) {
        if (!(mc.screen instanceof BuilderScreen screen)) return false;
        // 交互模式 + 点击模式 + 物品拾取（漏斗）启用 + RTS 相机激活
        // （相机激活与服务端 RtsFunnelService.validate 的 RtsCameraManager.isActive 一致，
        //  避免“球体可见但服务端静默拒绝”的假象）
        if (!screen.isCameraActive()) return false;
        if (!screen.isInteractiveMode()) return false;
        if (!screen.isClickButtonSelected()) return false;
        if (!screen.isItemPickupActive()) return false;
        return isConfigSafe() && PerformanceConfig.shouldRenderInteractionHighlights();
    }

    private boolean isConfigSafe() {
        try {
            PerformanceConfig.shouldRenderInteractionHighlights();
            return true;
        } catch (IllegalStateException e) {
            // 配置尚未加载时按默认行为处理
            return true;
        }
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (mc.level == null || mc.getCameraEntity() == null) return;
        if (!(mc.screen instanceof BuilderScreen)) return;

        var ray = alloc.cursorRay();
        if (ray == null) return;
        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        // 以目标方块中心为球心（与服务端 Vec3.atCenterOf(center) 一致），平滑跟随指针
        double cx = hit.getBlockPos().getX() + 0.5D;
        double cy = hit.getBlockPos().getY() + 0.5D;
        double cz = hit.getBlockPos().getZ() + 0.5D;
        double r = SPHERE_RADIUS;
        smoothTarget.update(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);

        cx = (smoothTarget.minX() + smoothTarget.maxX()) / 2;
        cy = (smoothTarget.minY() + smoothTarget.maxY()) / 2;
        cz = (smoothTarget.minZ() + smoothTarget.maxZ()) / 2;

        // 距离剔除
        double distance = smoothTarget.centerDistanceTo(ray.origin());
        try {
            if (PerformanceConfig.shouldEnableRenderDistanceCulling()
                    && distance > PerformanceConfig.getMaxRenderDistance()) {
                return;
            }
        } catch (IllegalStateException e) {
            // 配置未加载时跳过剔除
        }

        rangeRgb.update(rangeColor);
        float rr = rangeRgb.r, rg = rangeRgb.g, rb = rangeRgb.b;

        // 1) 半透明球壳（先画，作为体积底衬）
        renderSphereShell(alloc.brackets(), poseStack, cx, cy, cz, r, rr, rg, rb, SHELL_DEPTH_ALPHA);
        if (BoxSelectionPass.depthTestEnabled) {
            renderSphereShell(alloc.noDepth(), poseStack, cx, cy, cz, r,
                    rr, rg, rb, SHELL_NO_DEPTH_ALPHA);
        }

        // 2) 三条两两垂直的赤道大圆线框（细线，保证任意视角轮廓圆润完整）
        renderSphereGrid(alloc.lines(), poseStack, cx, cy, cz, r, rr, rg, rb, GRID_ALPHA);
    }

    @Override
    public int requiredBuffers() {
        return 1 | 4 | 8; // lines + brackets + noDepth
    }

    /**
     * 绘制三条两两垂直的赤道大圆：水平环（XZ 平面，y = cy）、
     * 垂直环（XY 平面，z = cz）与垂直环（YZ 平面，x = cx），
     * 各自按 {@link #SEGMENTS} 段细分。
     */
    private static void renderSphereGrid(VertexConsumer consumer, PoseStack poseStack,
            double cx, double cy, double cz, double r,
            float red, float green, float blue, float alpha) {
        double step = Math.PI * 2 / SEGMENTS;
        // 第一条：水平赤道（XZ 平面，y = cy），半径 r
        for (int s = 0; s < SEGMENTS; s++) {
            double t0 = s * step;
            double t1 = t0 + step;
            double x0 = cx + r * Math.cos(t0);
            double z0 = cz + r * Math.sin(t0);
            double x1 = cx + r * Math.cos(t1);
            double z1 = cz + r * Math.sin(t1);
            lineSegment(consumer, poseStack, x0, cy, z0, x1, cy, z1, red, green, blue, alpha);
        }
        // 第二条：垂直赤道（XY 平面，z = cz），与水平赤道互相垂直
        for (int s = 0; s < SEGMENTS; s++) {
            double t0 = s * step;
            double t1 = t0 + step;
            double x0 = cx + r * Math.cos(t0);
            double y0 = cy + r * Math.sin(t0);
            double x1 = cx + r * Math.cos(t1);
            double y1 = cy + r * Math.sin(t1);
            lineSegment(consumer, poseStack, x0, y0, cz, x1, y1, cz, red, green, blue, alpha);
        }
        // 第三条：垂直赤道（YZ 平面，x = cx），与前两条两两垂直
        for (int s = 0; s < SEGMENTS; s++) {
            double t0 = s * step;
            double t1 = t0 + step;
            double y0 = cy + r * Math.cos(t0);
            double z0 = cz + r * Math.sin(t0);
            double y1 = cy + r * Math.cos(t1);
            double z1 = cz + r * Math.sin(t1);
            lineSegment(consumer, poseStack, cx, y0, z0, cx, y1, z1, red, green, blue, alpha);
        }
    }

    /**
     * 绘制半透明球壳：按经纬度细分成小四边形面片，营造完整的体积感。
     */
    private static void renderSphereShell(VertexConsumer consumer, PoseStack poseStack,
            double cx, double cy, double cz, double r,
            float red, float green, float blue, float alpha) {
        double lonStep = Math.PI * 2 / SHELL_LON_SEGMENTS;
        double latStep = Math.PI / SHELL_LAT_SEGMENTS;
        for (int lat = 0; lat < SHELL_LAT_SEGMENTS; lat++) {
            double phi0 = -Math.PI / 2 + lat * latStep;
            double phi1 = phi0 + latStep;
            double cos0 = Math.cos(phi0), sin0 = Math.sin(phi0);
            double cos1 = Math.cos(phi1), sin1 = Math.sin(phi1);
            for (int lon = 0; lon < SHELL_LON_SEGMENTS; lon++) {
                double lam0 = lon * lonStep;
                double lam1 = lam0 + lonStep;
                double cosA = Math.cos(lam0), sinA = Math.sin(lam0);
                double cosB = Math.cos(lam1), sinB = Math.sin(lam1);

                double x00 = cx + r * cos0 * cosA, y00 = cy + r * sin0, z00 = cz + r * cos0 * sinA;
                double x10 = cx + r * cos0 * cosB, y10 = cy + r * sin0, z10 = cz + r * cos0 * sinB;
                double x11 = cx + r * cos1 * cosB, y11 = cy + r * sin1, z11 = cz + r * cos1 * sinB;
                double x01 = cx + r * cos1 * cosA, y01 = cy + r * sin1, z01 = cz + r * cos1 * sinA;

                var pose = poseStack.last();
                consumer.addVertex(pose, (float) x00, (float) y00, (float) z00).setColor(red, green, blue, alpha);
                consumer.addVertex(pose, (float) x10, (float) y10, (float) z10).setColor(red, green, blue, alpha);
                consumer.addVertex(pose, (float) x11, (float) y11, (float) z11).setColor(red, green, blue, alpha);
                consumer.addVertex(pose, (float) x01, (float) y01, (float) z01).setColor(red, green, blue, alpha);
            }
        }
    }

    /**
     * 绘制一条线段（LINES 格式，POSITION_COLOR_NORMAL）。
     */
    private static void lineSegment(VertexConsumer consumer, PoseStack poseStack,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b, float a) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6) return;
        float nx = (float) (dx / len);
        float ny = (float) (dy / len);
        float nz = (float) (dz / len);

        var pose = poseStack.last();
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(r, g, b, a)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(r, g, b, a)
                .setNormal(pose, nx, ny, nz);
    }
}
