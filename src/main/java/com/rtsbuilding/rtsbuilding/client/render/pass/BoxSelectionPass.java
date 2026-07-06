package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 框选渲染 pass——绘制三点框选的白色虚线预览框。
 *
 * <p>内部委托 {@link CornerBracketRenderer#renderDashedCornerBrackets} 渲染，
 * 与 {@link InteractionTargetPass} 共用同一套渲染逻辑。</p>
 */
public final class BoxSelectionPass implements RenderPass {

    /** 外扩偏移（避免与方块面 Z-fighting） */
    private static final double OFFSET = 0.01D;
    /** 透明覆盖层外扩偏移，比线框略大确保覆盖层在线框内侧 */
    private static final double OVERLAY_OFFSET = 0.02D;
    private static final float DEPTH_ALPHA = 0.9f;
    /** 流动速度（块/帧），仅 COMPLETE 阶段生效 */
    private static final double FLOW_SPEED = 0.02D;

    /** 深度测试全局开关（默认开启），由渲染设置面板控制 */
    public static boolean depthTestEnabled = true;

    /** 线框流动动画开关（默认开启），由渲染设置面板控制 */
    public static boolean flowAnimationEnabled = true;

    // ======================== 可自定义颜色 ========================

    /** 框选虚线角支架颜色（ARGB），默认白色 */
    public static int selectionColor = 0xFFFFFFFF;
    /** 框选预览半透明覆盖层颜色（ARGB），默认蓝色 */
    public static int previewOverlayColor = 0xFF4D80FF;
    /** 框选虚线间隙颜色（ARGB），默认黑色 */
    public static int selectionGapColor = 0xFF000000;
    /** 框选实体角支架颜色（ARGB），使用与交互目标不同的颜色以示区分，默认亮绿色 */
    public static int entitySelectionColor = 0xFF4CAF50;

    private final BoxSelector selector;
    private final CornerBracketRenderer.SmoothTarget smoothTarget = new CornerBracketRenderer.SmoothTarget();

    /** AABB 计算结果 */
    private record BoxAABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    // ======================== 方块覆盖层缓存 ========================

    /** 缓存的分组渲染数据：颜色 + 外扩后的包围盒，由 scanGroups() 生成 */
    private record CachedGroup(float r, float g, float b,
                               double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ) {}

    /** 上次扫描的选区角坐标，用于缓存失效检测 */
    private BlockPos cachedScanMin;
    private BlockPos cachedScanMax;
    /** 缓存渲染列表，选区不变时直接复用 */
    private List<CachedGroup> cachedRenderData = List.of();

    public BoxSelectionPass(BoxSelector selector) {
        this.selector = selector;
    }

    /** 清除方块扫描缓存，下次 COMPLETE 时重新扫描 */
    public void clearCache() {
        cachedScanMin = null;
        cachedScanMax = null;
        cachedRenderData = List.of();
        smoothTarget.reset();
    }

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.screen instanceof com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (!(mc.screen instanceof BuilderScreen screen)) return;

        // 点击模式下：清理所有框选状态 + 缓存，并跳过渲染
        if (screen.isClickButtonSelected()) {
            selector.reset();
            clearCache();
            return;
        }


        BlockPos hover = selector.getHoverPos();
        BoxSelector.Phase phase = selector.getPhase();

        BoxAABB box = computeBoxAABB(phase, hover);
        if (box == null) return;

        double flowOffset = (phase == BoxSelector.Phase.COMPLETE && flowAnimationEnabled)
                ? (frameIndex * FLOW_SPEED) % 0.5D : 0;

        // 计算目标包围盒（外扩避免 Z-fighting）
        double tMinX = box.minX() - OFFSET;
        double tMinY = box.minY() - OFFSET;
        double tMinZ = box.minZ() - OFFSET;
        double tMaxX = box.maxX() + OFFSET;
        double tMaxY = box.maxY() + OFFSET;
        double tMaxZ = box.maxZ() + OFFSET;

        // 平滑过渡
        smoothTarget.update(tMinX, tMinY, tMinZ, tMaxX, tMaxY, tMaxZ);

        // 计算距离（用于厚度缩放）
        var camera = mc.getCameraEntity();
        if (camera == null) return;
        Vec3 cameraPos = camera.getEyePosition(partialTick);
        double distance = smoothTarget.centerDistanceTo(cameraPos);

        // 深度检测层 + 无深度穿墙层
        float selR = ((selectionColor >> 16) & 0xFF) / 255.0f;
        float selG = ((selectionColor >> 8) & 0xFF) / 255.0f;
        float selB = (selectionColor & 0xFF) / 255.0f;
        float gapR = ((selectionGapColor >> 16) & 0xFF) / 255.0f;
        float gapG = ((selectionGapColor >> 8) & 0xFF) / 255.0f;
        float gapB = (selectionGapColor & 0xFF) / 255.0f;
        CornerBracketRenderer.renderDashedCornerBrackets(poseStack, alloc.brackets(),
                smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ(),
                selR, selG, selB, gapR, gapG, gapB, DEPTH_ALPHA, distance, flowOffset);
        if (depthTestEnabled) {
            CornerBracketRenderer.renderDashedCornerBrackets(poseStack, alloc.noDepth(),
                    smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                    smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ(),
                    selR, selG, selB, gapR, gapG, gapB, CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, distance, flowOffset);
        }

        // 蓝色半透明覆盖层（选定 A 点后、选中完成前的预览阶段展示）
        if (phase != BoxSelector.Phase.IDLE && phase != BoxSelector.Phase.COMPLETE) {
            float ovR = ((previewOverlayColor >> 16) & 0xFF) / 255.0f;
            float ovG = ((previewOverlayColor >> 8) & 0xFF) / 255.0f;
            float ovB = (previewOverlayColor & 0xFF) / 255.0f;
            // 深度层——正常遮挡时可见
            CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                    smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                    smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ(),
                    ovR, ovG, ovB, 0.18f);
            if (depthTestEnabled) {
                // 穿墙层——透过方块可见
                CornerBracketRenderer.renderFilledFaces(alloc.noDepth(), poseStack,
                        smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                        smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ(),
                        ovR, ovG, ovB, 0.06f);
            }
        }

        // ====== COMPLETE 阶段：方块覆盖层 + 实体青蓝线框 + 容器标记 ======
        if (phase == BoxSelector.Phase.COMPLETE) {
            renderBlockOverlay(mc, alloc, poseStack, box);
            renderEntityBrackets(mc, alloc, poseStack, partialTick);
            // 绑定模式激活时，在选区内渲染容器方块角支架线框
            if (screen.isBindModeActive()) {
                renderContainerBrackets(mc, alloc, poseStack, box);
            }
        }
    }

    /** 按方块类型分组计算包围盒，每种方块用不同颜色的透明覆盖层渲染。
     *  选区内方块扫描结果会缓存，选区不变时不重复扫描。 */
    private void renderBlockOverlay(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, BoxAABB box) {
        var level = mc.level;
        if (level == null) return;

        BlockPos minCorner = selector.getMinCorner();
        BlockPos maxCorner = selector.getMaxCorner();
        if (minCorner == null || maxCorner == null) return;

        // 选区变化时重新扫描
        if (!minCorner.equals(cachedScanMin) || !maxCorner.equals(cachedScanMax)) {
            cachedScanMin = minCorner.immutable();
            cachedScanMax = maxCorner.immutable();
            cachedRenderData = scanGroups(level, minCorner, maxCorner);
        }

        // 用缓存数据直接渲染
        for (var g : cachedRenderData) {
            // 深度层始终渲染
            CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                    g.minX(), g.minY(), g.minZ(), g.maxX(), g.maxY(), g.maxZ(),
                    g.r(), g.g(), g.b(), 0.12f);
            // 穿墙层仅深度测试开启时渲染
            if (depthTestEnabled) {
                CornerBracketRenderer.renderFilledFaces(alloc.noDepth(), poseStack,
                        g.minX(), g.minY(), g.minZ(), g.maxX(), g.maxY(), g.maxZ(),
                        g.r(), g.g(), g.b(), CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA);
            }
        }
    }

    /** 扫描选区内所有非空气方块，按方块类型分组并返回渲染数据。
     *  只在选区变化时调用一次，后续帧复用缓存。 */
    private List<CachedGroup> scanGroups(net.minecraft.world.level.Level level, BlockPos minCorner, BlockPos maxCorner) {
        int minX = minCorner.getX();
        int minY = minCorner.getY();
        int minZ = minCorner.getZ();
        int maxX = maxCorner.getX();
        int maxY = maxCorner.getY();
        int maxZ = maxCorner.getZ();

        // 用方块注册 ID（整数）分组，避免每帧构建字符串
        var reg = BuiltInRegistries.BLOCK;
        Map<Integer, GroupBounds> groups = new HashMap<>();

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    var state = level.getBlockState(new BlockPos(x, y, z));
                    if (state.isAir()) continue;
                    int id = reg.getId(state.getBlock());
                    var group = groups.computeIfAbsent(id, k -> new GroupBounds());
                    group.expand(x, y, z);
                    group.recordState(state);
                }
            }
        }

        if (groups.isEmpty()) return List.of();

        double off = OVERLAY_OFFSET;
        var result = new ArrayList<CachedGroup>(groups.size());

        for (var entry : groups.entrySet()) {
            var bounds = entry.getValue();
            float[] rgb = bounds.getMapColorRGB(level);

            result.add(new CachedGroup(rgb[0], rgb[1], rgb[2],
                    bounds.minX - off, bounds.minY - off, bounds.minZ - off,
                    bounds.maxX + off, bounds.maxY + off, bounds.maxZ + off));
        }

        return result;
    }

    /** 每类方块包围盒，附带首个方块的 BlockState 用于提取地图颜色 */
    private static final class GroupBounds {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        BlockState firstState;

        void expand(int x, int y, int z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x + 1);
            maxY = Math.max(maxY, y + 1);
            maxZ = Math.max(maxZ, z + 1);
        }

        void recordState(BlockState state) {
            if (firstState == null) firstState = state;
        }

        /** 用组内首个方块的 BlockState 提取地图颜色并转为 float RGB */
        float[] getMapColorRGB(net.minecraft.world.level.Level level) {
            if (firstState == null) return new float[]{0.3f, 0.5f, 1.0f}; // fallback 蓝色
            int rgb = firstState.getMapColor(level, BlockPos.ZERO).col;
            return new float[]{
                    ((rgb >> 16) & 0xFF) / 255.0f,
                    ((rgb >> 8) & 0xFF) / 255.0f,
                    (rgb & 0xFF) / 255.0f
            };
        }
    }

    // ======================== 生物线框角支架 ========================

    /** 给框选范围内所有生物画上与 InteractionTargetPass 一致的青蓝色角支架线框 */
    private void renderEntityBrackets(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick) {
        if (mc.level == null) return;

        BlockPos min = selector.getMinCorner();
        BlockPos max = selector.getMaxCorner();
        if (min == null || max == null) return;

        AABB selectionBox = new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX(), max.getY(), max.getZ());

        List<Entity> entities = mc.level.getEntities((Entity) null, selectionBox, e -> true);
        for (Entity entity : entities) {
            if (!entity.getBoundingBox().intersects(selectionBox)) continue;

            // 用 partialTick 插值实体位置，避免 tick 更新间的卡顿
            double renderX = Mth.lerp(partialTick, entity.xo, entity.getX());
            double renderY = Mth.lerp(partialTick, entity.yo, entity.getY());
            double renderZ = Mth.lerp(partialTick, entity.zo, entity.getZ());
            var bounds = entity.getBoundingBox()
                    .move(renderX - entity.getX(), renderY - entity.getY(), renderZ - entity.getZ())
                    .inflate(0.03D);

            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                    bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ,
                    ((entitySelectionColor >> 16) & 0xFF) / 255.0f,
                    ((entitySelectionColor >> 8) & 0xFF) / 255.0f,
                    (entitySelectionColor & 0xFF) / 255.0f,
                    0.9f, 0);
            if (depthTestEnabled) {
                CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                        bounds.minX, bounds.minY, bounds.minZ,
                        bounds.maxX, bounds.maxY, bounds.maxZ,
                        ((entitySelectionColor >> 16) & 0xFF) / 255.0f,
                        ((entitySelectionColor >> 8) & 0xFF) / 255.0f,
                        (entitySelectionColor & 0xFF) / 255.0f,
                        CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, 0);
            }
        }
    }

    // ======================== 框选内容器高亮线框 ========================

    /**
     * 绑定模式下，在选区内为每个带 BlockEntity 的容器方块渲染蓝色角支架线框。
     * <p>颜色风格与 {@link com.rtsbuilding.rtsbuilding.client.render.pass.LinkedStoragePass} 的双向模式一致（蓝色），
     * 让玩家在批量绑定前清楚看到哪些方框会被链接。
     */
    private void renderContainerBrackets(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, BoxAABB box) {
        if (mc.level == null) return;
        // 复用 scanBlockOverlay 的缓存选区
        BlockPos min = selector.getMinCorner();
        BlockPos max = selector.getMaxCorner();
        if (min == null || max == null) return;

        // 绑定模式蓝色（与 LinkedStoragePass 双向模式一致）
        float r = 0.24F, g = 0.55F, b = 1.00F;

        for (int x = min.getX(); x < max.getX(); x++) {
            for (int y = min.getY(); y < max.getY(); y++) {
                for (int z = min.getZ(); z < max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir() || !state.hasBlockEntity()) continue;

                    double distance = mc.getCameraEntity() != null
                            ? mc.getCameraEntity().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                            : 64.0;
                    double camDist = Math.sqrt(distance);

                    // 深度检测层
                    CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                            pos.getX() - 0.01, pos.getY() - 0.01, pos.getZ() - 0.01,
                            pos.getX() + 1.01, pos.getY() + 1.01, pos.getZ() + 1.01,
                            r, g, b, 0.6f, camDist);
                    // 穿墙层
                    if (depthTestEnabled) {
                        CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                                pos.getX() - 0.01, pos.getY() - 0.01, pos.getZ() - 0.01,
                                pos.getX() + 1.01, pos.getY() + 1.01, pos.getZ() + 1.01,
                                r, g, b, CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, camDist);
                    }
                }
            }
        }
    }

    // ======================== AABB 计算 ========================

    /** 根据当前阶段计算应渲染的 AABB，无有效结果时返回 null */
    private BoxAABB computeBoxAABB(BoxSelector.Phase phase, BlockPos hover) {
        return switch (phase) {
            case IDLE -> {
                if (hover == null) yield null;
                yield new BoxAABB(hover.getX(), hover.getY(), hover.getZ(),
                        hover.getX() + 1, hover.getY() + 1, hover.getZ() + 1);
            }
            case AWAITING_B -> {
                BlockPos a = selector.getPointA();
                if (a == null) yield null;
                if (hover != null) {
                    yield new BoxAABB(
                            Math.min(a.getX(), hover.getX()), Math.min(a.getY(), hover.getY()), Math.min(a.getZ(), hover.getZ()),
                            Math.max(a.getX() + 1, hover.getX() + 1), Math.max(a.getY() + 1, hover.getY() + 1), Math.max(a.getZ() + 1, hover.getZ() + 1));
                } else {
                    yield new BoxAABB(a.getX(), a.getY(), a.getZ(), a.getX() + 1, a.getY() + 1, a.getZ() + 1);
                }
            }
            case AWAITING_C -> {
                BlockPos a = selector.getPointA();
                BlockPos b = selector.getPointB();
                if (a == null || b == null) yield null;
                int offset = selector.getScrollHeightOffset();
                double baseBottom = Math.min(a.getY(), b.getY());
                double baseTop = Math.max(a.getY() + 1, b.getY() + 1);
                double previewMinY, previewMaxY;
                if (offset >= 0) {
                    // 往上滚：顶部向上延伸，底部不动
                    previewMinY = baseBottom;
                    previewMaxY = baseTop + offset;
                } else {
                    // 往下滚：底部向下延伸，顶部不动
                    previewMinY = baseBottom + offset;
                    previewMaxY = baseTop;
                }
                if (previewMaxY < previewMinY) previewMaxY = previewMinY;
                yield new BoxAABB(
                        Math.min(a.getX(), b.getX()), previewMinY, Math.min(a.getZ(), b.getZ()),
                        Math.max(a.getX() + 1, b.getX() + 1), previewMaxY, Math.max(a.getZ() + 1, b.getZ() + 1));
            }
            case COMPLETE -> {
                BlockPos min = selector.getMinCorner();
                BlockPos max = selector.getMaxCorner();
                if (min == null || max == null) yield null;
                yield new BoxAABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
            }
        };
    }
}
