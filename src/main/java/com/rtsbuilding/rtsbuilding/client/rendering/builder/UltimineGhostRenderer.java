package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * 连锁破坏预览和合并骨架的共享线框渲染器。
 *
 * <p>Forge 1.20.1 使用旧版 {@link BufferBuilder} 生命周期，所以这里保留
 * 1.20.1 可编译的 buffer 写法，同时同步 1.21.1 的二段式策略：先画受深度测试的
 * 清晰实线，再画低透明度 no-depth 线作为遮挡兜底，避免地形边缘看起来断断续续。
 */
public final class UltimineGhostRenderer extends RenderStateShard {
    private static final float BREATH_SPEED = 0.2F;
    private static final float BREATH_MIN_FACTOR = 0.7F;
    private static final LineStateShard NO_DEPTH_LINE =
            new LineStateShard(OptionalDouble.of(SkeletonRenderStyle.NO_DEPTH_LINE_WIDTH));
    private static final List<NoDepthLineBatch> PENDING_NO_DEPTH_BATCHES = new ArrayList<>();

    private static final RenderType LINES_NO_DEPTH = RenderType.create(
            "rtsbuilding_ultimine_lines_no_depth",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            512,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(NO_DEPTH_LINE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private static final BufferBuilder LINES_NO_DEPTH_BUFFER = new BufferBuilder(LINES_NO_DEPTH.bufferSize());

    private UltimineGhostRenderer() {
        super("rtsbuilding_ultimine_ghost_renderer", () -> {}, () -> {});
    }

    static void render(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        List<BlockPos> blocks = preview.blocks();
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        Set<Long> blockKeys = MergedSkeletonSupport.blockKeySet(blocks);
        List<BlockPos> outerBlocks = MergedSkeletonSupport.outerBlocks(blocks, blockKeys);
        if (outerBlocks.isEmpty()) {
            return;
        }
        List<UltimineBlockMerger.EdgeLine> edges = UltimineBlockMerger.getEdgeLines(outerBlocks);
        if (edges.isEmpty()) {
            return;
        }

        float breath = RenderingUtil.getBreathFactor(BREATH_SPEED, BREATH_MIN_FACTOR);
        float r = 1.00F * breath;
        float g = 0.72F * breath;
        float b = 0.24F * breath;

        renderPass1(edges, poseStack.last().pose(), lineBuffer, r, g, b, 0.95F);
        renderPass2(edges, poseStack.last().pose(), r, g, b, 0.34F);
        renderFill(outerBlocks, poseStack, fillBuffer, r, g, b, 0.08F);
    }

    static void renderPass1(List<UltimineBlockMerger.EdgeLine> edges, Matrix4f matrix,
            VertexConsumer lineBuffer, float r, float g, float b, float alpha) {
        int red = channel(r);
        int green = channel(g);
        int blue = channel(b);
        int a = channel(alpha);
        for (UltimineBlockMerger.EdgeLine edge : edges) {
            lineBuffer.vertex(matrix, (float) edge.x1(), (float) edge.y1(), (float) edge.z1())
                    .color(red, green, blue, a)
                    .normal(edge.xn(), edge.yn(), edge.zn())
                    .endVertex();
            lineBuffer.vertex(matrix, (float) edge.x2(), (float) edge.y2(), (float) edge.z2())
                    .color(red, green, blue, a)
                    .normal(edge.xn(), edge.yn(), edge.zn())
                    .endVertex();
        }
    }

    static void renderPass2(List<UltimineBlockMerger.EdgeLine> edges, Matrix4f matrix,
            float r, float g, float b, float alpha) {
        if (edges == null || edges.isEmpty() || alpha <= 0.0F) {
            return;
        }
        PENDING_NO_DEPTH_BATCHES.add(new NoDepthLineBatch(
                List.copyOf(edges), new Matrix4f(matrix), r, g, b, alpha));
    }

    static void clearDeferredNoDepthPasses() {
        PENDING_NO_DEPTH_BATCHES.clear();
    }

    static void flushDeferredNoDepthPasses() {
        if (PENDING_NO_DEPTH_BATCHES.isEmpty()) {
            return;
        }
        for (NoDepthLineBatch batch : PENDING_NO_DEPTH_BATCHES) {
            drawNoDepthBatch(batch);
        }
        PENDING_NO_DEPTH_BATCHES.clear();
    }

    private static void drawNoDepthBatch(NoDepthLineBatch batch) {
        if (batch == null || batch.edges().isEmpty() || batch.alpha() <= 0.0F) {
            return;
        }
        if (LINES_NO_DEPTH_BUFFER.building()) {
            LINES_NO_DEPTH_BUFFER.discard();
        }
        LINES_NO_DEPTH_BUFFER.begin(LINES_NO_DEPTH.mode(), LINES_NO_DEPTH.format());
        renderPass1(batch.edges(), batch.matrix(), LINES_NO_DEPTH_BUFFER,
                batch.r(), batch.g(), batch.b(), batch.alpha());
        if (LINES_NO_DEPTH_BUFFER.isCurrentBatchEmpty()) {
            LINES_NO_DEPTH_BUFFER.endOrDiscardIfEmpty();
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        LINES_NO_DEPTH.end(LINES_NO_DEPTH_BUFFER, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
    }

    static void renderFill(List<BlockPos> outerBlocks, PoseStack poseStack,
            VertexConsumer fillBuffer, float r, float g, float b, float fillA) {
        for (BlockPos pos : outerBlocks) {
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                    r, g, b, fillA);
        }
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F)));
    }

    private record NoDepthLineBatch(
            List<UltimineBlockMerger.EdgeLine> edges,
            Matrix4f matrix,
            float r,
            float g,
            float b,
            float alpha) {
    }

    /**
     * 只给渲染器共享的小型几何 helper，避免把这部分职责重新塞回
     * {@link ShapeGhostRenderer}。
     */
    private static final class MergedSkeletonSupport {
        private MergedSkeletonSupport() {
        }

        private static Set<Long> blockKeySet(List<BlockPos> blocks) {
            Set<Long> keys = new java.util.HashSet<>();
            for (BlockPos pos : blocks) {
                if (pos != null) {
                    keys.add(pos.asLong());
                }
            }
            return keys;
        }

        private static List<BlockPos> outerBlocks(List<BlockPos> blocks, Set<Long> blockKeys) {
            List<BlockPos> outer = new java.util.ArrayList<>();
            for (BlockPos pos : blocks) {
                if (pos == null) {
                    continue;
                }
                int x = pos.getX();
                int y = pos.getY();
                int z = pos.getZ();
                if (!blockKeys.contains(BlockPos.asLong(x + 1, y, z))
                        || !blockKeys.contains(BlockPos.asLong(x - 1, y, z))
                        || !blockKeys.contains(BlockPos.asLong(x, y + 1, z))
                        || !blockKeys.contains(BlockPos.asLong(x, y - 1, z))
                        || !blockKeys.contains(BlockPos.asLong(x, y, z + 1))
                        || !blockKeys.contains(BlockPos.asLong(x, y, z - 1))) {
                    outer.add(pos);
                }
            }
            return outer;
        }
    }
}
