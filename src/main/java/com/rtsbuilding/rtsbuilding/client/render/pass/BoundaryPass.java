package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 边界渲染 pass——以 RTS 区域锚点为中心、固定不动的屏障围栏。
 * 使用原版世界边界同款的屏障纹理 + 对角线滚动动画，从地表向上延伸 5 格直达基岩。
 * <p>
 * 锚点位置和半径由 {@link RtsClientKernel} 统一管理，
 * 从服务端 {@code S2CRtsCameraAnchorPayload} 同步，与摄像机模块解耦。
 * <p>
 * <b>性能优化：</b>
 * <ul>
 *   <li>高度图扫描结果缓存，仅在边界坐标变化时重算（有锚点场景可节省约 99% 的高度图查询）</li>
 *   <li>滚动动画使用帧间增量时间驱动，替代每帧 {@code System.nanoTime()} 调用</li>
 * </ul>
 */
public final class BoundaryPass implements RenderPass {

    /** 后备半径（无锚点时用） */
    private static final double FALLBACK_RADIUS = 250.0;

    /** 纹理分块大小（方块），控制条纹重复频率 */
    private static final float TILE_SIZE = 2.0F;

    /** 纯白顶点颜色倍率——纹理提供实际颜色 */
    private static final float WHITE = 1.0F;

    /**
     * 屏障色调颜色（ABGR 格式，与 Minecraft setColor 一致），默认黄色 0xFFFFCC00。
     * <p>由 {@link com.rtsbuilding.rtsbuilding.client.screen.panel.gear.RenderingSection} 设置面板控制。
     */
    public static int barrierColor = 0xFFFFCC00;

    /** 屏障透明度 */
    private static final float BARRIER_ALPHA = 0.80F;

    /** 满亮度光图值（方块=15, 天空=15, 各左移 4 位） */
    private static final int FULL_BRIGHT = 0xF0;

    /** 滚动速度（UV 单位/秒） */
    private static final float SCROLL_SPEED = 0.5F;

    /** 回退模式下高度图重算节流间隔（毫秒）——半秒一次绰绰有余 */
    private static final long FALLBACK_RECALC_MS = 500;

    /** 增量时间上限（毫秒），避免长时间暂停后滚偏跳帧 */
    private static final float MAX_DELTA_MS = 200.0F;

    /** 滚动偏移模数，防止浮点数溢出 */
    private static final float SCROLL_MOD = 256.0F;

    // ======================== 滚动动画状态 ========================

    /** 累计滚动偏移（帧间累积，永不重置） */
    private float scrollOffset;

    /** 上一帧时间戳（毫秒） */
    private long lastFrameMillis = -1;

    // ======================== 高度图缓存 ========================

    /** 缓存的边界整型坐标（用于检测变化） */
    private int cachedMinX = Integer.MIN_VALUE;
    private int cachedMinZ = Integer.MIN_VALUE;
    private int cachedMaxX = Integer.MIN_VALUE;
    private int cachedMaxZ = Integer.MIN_VALUE;

    /** 缓存的最高地表 Y */
    private int cachedHighestY = Integer.MIN_VALUE;

    /** 回退模式上次重算时间戳 */
    private long fallbackLastRecalc;

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.player != null && mc.screen instanceof com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (mc.player == null) return;
        RtsClientKernel kernel = RtsClientKernel.get();
        double r, cx, cy, cz;
        boolean useFallback;
        if (kernel.isRegionValid()) {
            cx = kernel.getRegionAnchorX();
            cy = kernel.getRegionAnchorY();
            cz = kernel.getRegionAnchorZ();
            r  = kernel.getRegionMaxRadius();
            useFallback = false;
        } else {
            cx = mc.player.getX();
            cy = mc.player.getY();
            cz = mc.player.getZ();
            r  = FALLBACK_RADIUS;
            useFallback = true;
        }

        // ---- 更新滚动偏移（基于时间增量，避免 System.nanoTime()） ----
        long now = System.currentTimeMillis();
        if (this.lastFrameMillis < 0) {
            this.lastFrameMillis = now;
        } else {
            float deltaMs = (float) (now - this.lastFrameMillis);
            if (deltaMs > MAX_DELTA_MS) deltaMs = MAX_DELTA_MS; // 防跳帧
            this.scrollOffset = (this.scrollOffset + deltaMs * SCROLL_SPEED / 1000.0F) % SCROLL_MOD;
            this.lastFrameMillis = now;
        }

        renderBarrierWalls(alloc, mc.level, poseStack, cx, cy, cz, r, useFallback, now);
    }

    /**
     * 在边界 4 边各渲染一面垂直屏障墙。
     * 墙壁从边界线上最高地表方块上方 5 格延伸到基岩层，
     * 使用世界边界同款屏障纹理 + 对角线滚动动画。
     */
    private void renderBarrierWalls(BufferAllocator alloc, Level level, PoseStack poseStack,
                                     double ax, double ay, double az, double r,
                                     boolean useFallback, long now) {
        // 确保屏障颜色缓存在本帧有效（颜色不变时零开销）
        ensureBarrierColor();
        float minX = (float) (ax - r);
        float minZ = (float) (az - r);
        float maxX = (float) (ax + r);
        float maxZ = (float) (az + r);

        // 获取最高地表 Y（缓存优化）
        int highest = resolveHighestY(level, minX, minZ, maxX, maxZ, useFallback, now);
        float yMax = (highest > Integer.MIN_VALUE)
                ? highest + 5.0F
                : (float) ay + 3.0F;
        float yMin = (float) level.getMinBuildHeight();
        float wallH = yMax - yMin;

        var pose = poseStack.last();
        VertexConsumer barrier = alloc.barrier();

        float wallWX = maxX - minX;
        float wallWZ = maxZ - minZ;
        float scroll = this.scrollOffset;

        // North wall（z = minZ）——朝正 Z
        addTexturedQuad(pose, barrier, minX, yMin, minZ, maxX, yMax, minZ,
                wallWX / TILE_SIZE, wallH / TILE_SIZE,
                0.0F, 0.0F, 1.0F, scroll);

        // South wall（z = maxZ）——朝负 Z
        addTexturedQuad(pose, barrier, maxX, yMin, maxZ, minX, yMax, maxZ,
                wallWX / TILE_SIZE, wallH / TILE_SIZE,
                0.0F, 0.0F, -1.0F, scroll);

        // West wall（x = minX）——朝正 X
        addTexturedQuad(pose, barrier, minX, yMin, minZ, minX, yMax, maxZ,
                wallWZ / TILE_SIZE, wallH / TILE_SIZE,
                1.0F, 0.0F, 0.0F, scroll);

        // East wall（x = maxX）——朝负 X
        addTexturedQuad(pose, barrier, maxX, yMin, maxZ, maxX, yMax, minZ,
                wallWZ / TILE_SIZE, wallH / TILE_SIZE,
                -1.0F, 0.0F, 0.0F, scroll);
    }

    /**
     * 获取最高地表 Y 值，利用坐标缓存避免每帧重算。
     * <p>
     * <b>有锚点时</b>：边界固定，只在坐标首次或锚点更新时重算一次。
     * <b>回退模式</b>（以玩家为中心）：边界随玩家移动，每 {@link #FALLBACK_RECALC_MS} 毫秒最多重算一次。
     */
    private int resolveHighestY(Level level, float minX, float minZ, float maxX, float maxZ,
                                boolean useFallback, long now) {
        int iminX = (int) Math.floor(minX);
        int iminZ = (int) Math.floor(minZ);
        int imaxX = (int) Math.floor(maxX);
        int imaxZ = (int) Math.floor(maxZ);

        if (!useFallback) {
            // ---- 有锚点：仅当坐标变化时重算（绝大多数帧命中缓存） ----
            if (iminX != cachedMinX || iminZ != cachedMinZ ||
                imaxX != cachedMaxX || imaxZ != cachedMaxZ) {
                cachedHighestY = findHighestBoundaryBlock(level, minX, minZ, maxX, maxZ);
                cachedMinX = iminX;
                cachedMinZ = iminZ;
                cachedMaxX = imaxX;
                cachedMaxZ = imaxZ;
            }
            return cachedHighestY;
        } else {
            // ---- 回退模式：节流重算 ----
            if (now - fallbackLastRecalc >= FALLBACK_RECALC_MS) {
                cachedHighestY = findHighestBoundaryBlock(level, minX, minZ, maxX, maxZ);
                fallbackLastRecalc = now;
            }
            return cachedHighestY;
        }
    }

    /**
     * 扫描 4 条边界边上所有方块，返回最高地表方块的高度。
     * 使用世界高度图进行高效逐块查找。
     *
     * @return 最高地表 Y；若区域未加载则返回 Integer.MIN_VALUE
     */
    private static int findHighestBoundaryBlock(Level level, float minX, float minZ, float maxX, float maxZ) {
        int highest = Integer.MIN_VALUE;
        int x1 = (int) Math.floor(minX);
        int x2 = (int) Math.floor(maxX);
        int z1 = (int) Math.floor(minZ);
        int z2 = (int) Math.floor(maxZ);

        // North edge（z = z1）
        for (int x = x1; x <= x2; x++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z1);
            if (h > highest) highest = h;
        }
        // South edge（z = z2）
        for (int x = x1; x <= x2; x++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z2);
            if (h > highest) highest = h;
        }
        // West edge（x = x1，跳过已扫描的角点）
        for (int z = z1 + 1; z < z2; z++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x1, z);
            if (h > highest) highest = h;
        }
        // East edge（x = x2，跳过已扫描的角点）
        for (int z = z1 + 1; z < z2; z++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x2, z);
            if (h > highest) highest = h;
        }

        return highest;
    }

    /**
     * 向屏障缓冲区添加一个纹理四边形。
     * <p>
     * 四边形从 {@code (x1, yMin, z1)} 延伸到 {@code (x2, yMax, z2)}，
     * 使用实体半透明顶点格式，纹理按 {@code tileU × tileV} 重复。
     * 基于时间的 {@code scroll} 偏移同时叠加到 U 和 V，
     * 产生连续对角线条纹动画，匹配原版世界边界效果。
     */
    // ======================== 屏障颜色缓存 ========================

    private static final CornerBracketRenderer.Rgb barrierRgb = new CornerBracketRenderer.Rgb();

    private static void ensureBarrierColor() {
        barrierRgb.update(barrierColor);
    }

    private static void addTexturedQuad(PoseStack.Pose pose, VertexConsumer buffer,
                                         float x1, float yMin, float z1,
                                         float x2, float yMax, float z2,
                                         float tileU, float tileV,
                                         float nx, float ny, float nz,
                                         float scroll) {
        // bottom-left
        buffer.addVertex(pose, x1, yMin, z1).setUv(scroll, scroll)
                .setUv1(0, 10)
                .setUv2(FULL_BRIGHT, FULL_BRIGHT)
                .setColor(barrierRgb.r, barrierRgb.g, barrierRgb.b, BARRIER_ALPHA)
                .setNormal(nx, ny, nz);
        // bottom-right
        buffer.addVertex(pose, x2, yMin, z2).setUv(tileU + scroll, scroll)
                .setUv1(0, 10)
                .setUv2(FULL_BRIGHT, FULL_BRIGHT)
                .setColor(barrierRgb.r, barrierRgb.g, barrierRgb.b, BARRIER_ALPHA)
                .setNormal(nx, ny, nz);
        // top-right
        buffer.addVertex(pose, x2, yMax, z2).setUv(tileU + scroll, tileV + scroll)
                .setUv1(0, 10)
                .setUv2(FULL_BRIGHT, FULL_BRIGHT)
                .setColor(barrierRgb.r, barrierRgb.g, barrierRgb.b, BARRIER_ALPHA)
                .setNormal(nx, ny, nz);
        // top-left
        buffer.addVertex(pose, x1, yMax, z1).setUv(scroll, tileV + scroll)
                .setUv1(0, 10)
                .setUv2(FULL_BRIGHT, FULL_BRIGHT)
                .setColor(barrierRgb.r, barrierRgb.g, barrierRgb.b, BARRIER_ALPHA)
                .setNormal(nx, ny, nz);
    }

    @Override
    public int requiredBuffers() {
        return 16; // BARRIER
    }
}
