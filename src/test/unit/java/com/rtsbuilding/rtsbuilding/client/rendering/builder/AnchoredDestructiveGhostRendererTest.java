package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 记录真实顶点，防止飞船 plot 大坐标在减锚点之前被 float 舍入。 */
class AnchoredDestructiveGhostRendererTest {
    private static final BlockPos ANCHOR = new BlockPos(28_000_001, 64, -27_000_003);
    private static final double EPSILON = 0.00001D;

    @AfterEach
    void clearSkeletonCache() {
        MergedSkeletonRenderer.clearCache();
    }

    @Test
    void anchoredCellsRetainFractionalInsetsAndEnvelopeIncludesEmptyTargets() throws Exception {
        RecordingVertices fill = new RecordingVertices();
        BlockPos cell = ANCHOR.offset(2, 4, 6);
        ShapeDataRecords.GhostPreview preview = new ShapeDataRecords.GhostPreview(
                List.of(cell), true, true, List.of(ANCHOR.offset(5, 7, 9)));

        renderFillOnly(preview, fill.consumer, null, ANCHOR);

        assertBounds(fill.vertices, 1.98D, 3.98D, 5.98D, 6.02D, 8.02D, 10.02D);
        assertVertex(fill.vertices, 2.03D, 4.03D, 6.03D);
        assertVertex(fill.vertices, 2.97D, 4.97D, 6.97D);
    }

    @Test
    void anchoredEnvelopeOverrideUsesTheSameLocalFrameAsCells() throws Exception {
        RecordingVertices fill = new RecordingVertices();
        ShapeDataRecords.GhostPreview preview = new ShapeDataRecords.GhostPreview(
                List.of(ANCHOR.offset(2, 4, 6)), true, true);
        AABB override = new AABB(
                ANCHOR.getX() - 0.25D, ANCHOR.getY() + 1.25D, ANCHOR.getZ() + 2.25D,
                ANCHOR.getX() + 8.75D, ANCHOR.getY() + 9.75D, ANCHOR.getZ() + 10.75D);

        renderFillOnly(preview, fill.consumer, override, ANCHOR);

        assertBounds(fill.vertices, -0.27D, 1.23D, 2.23D, 8.77D, 9.77D, 10.77D);
        assertVertex(fill.vertices, 2.03D, 4.03D, 6.03D);
    }

    @Test
    void unanchoredRenderingRetainsOrdinaryWorldCoordinates() {
        RecordingVertices fill = new RecordingVertices();
        ShapeDataRecords.GhostPreview preview = new ShapeDataRecords.GhostPreview(
                List.of(new BlockPos(12, 64, -8)), true, true);

        DestructiveGhostRenderer.render(preview, new PoseStack(), mock(VertexConsumer.class), fill.consumer,
                0.0F, 1.0F, true, false);

        assertBounds(fill.vertices, 11.98D, 63.98D, -8.02D, 13.02D, 65.02D, -6.98D);
        assertVertex(fill.vertices, 12.03D, 64.03D, -7.97D);
        assertVertex(fill.vertices, 12.97D, 64.97D, -7.03D);
    }

    @Test
    void mergedSkeletonAndFillSubtractAnchorBeforeFloatConversion() {
        RecordingVertices lines = new RecordingVertices();
        RecordingVertices fill = new RecordingVertices();
        ShapeDataRecords.GhostPreview preview = new ShapeDataRecords.GhostPreview(
                List.of(ANCHOR.offset(2, 4, 6), ANCHOR.offset(3, 4, 6)), true, true);

        // 仅替换依赖 GL 的透地描边，保留骨架生成、缓存、普通线顶点及填充的生产实现。
        try (MockedStatic<UltimineGhostRenderer> rendering = withoutNoDepthPass()) {
            MergedSkeletonRenderer.renderMergedSkeletonFast(preview, new PoseStack(), lines.consumer, fill.consumer,
                    0.0F, 0.3F, 0.035F, 1.0F, ANCHOR);
            assertBounds(lines.vertices, 2, 4, 6, 4, 5, 7);
            assertBounds(fill.vertices, 2, 4, 6, 4, 5, 7);
            assertVertex(lines.vertices, 2, 4, 6);
        }
    }

    @Test
    void mergedSkeletonWithoutAnchorKeepsWorldCoordinates() {
        RecordingVertices lines = new RecordingVertices();
        RecordingVertices fill = new RecordingVertices();
        ShapeDataRecords.GhostPreview preview = new ShapeDataRecords.GhostPreview(
                List.of(new BlockPos(12, 64, -8)), true, true);

        try (MockedStatic<UltimineGhostRenderer> rendering = withoutNoDepthPass()) {
            MergedSkeletonRenderer.renderMergedSkeletonFast(preview, new PoseStack(), lines.consumer, fill.consumer,
                    0.0F, 0.3F, 0.035F);
            assertBounds(lines.vertices, 12, 64, -8, 13, 65, -7);
            assertBounds(fill.vertices, 12, 64, -8, 13, 65, -7);
        }
    }

    private static MockedStatic<UltimineGhostRenderer> withoutNoDepthPass() {
        MockedStatic<UltimineGhostRenderer> rendering = mockStatic(UltimineGhostRenderer.class, CALLS_REAL_METHODS);
        rendering.when(() -> UltimineGhostRenderer.renderPass2(
                anyList(), any(Matrix4f.class), anyFloat(), anyFloat(), anyFloat(), anyFloat(),
                anyDouble(), anyDouble(), anyDouble())).thenAnswer(invocation -> null);
        return rendering;
    }

    /** 私有入口允许只走真实填充路径，避免单元测试创建 OpenGL 上下文。 */
    private static void renderFillOnly(ShapeDataRecords.GhostPreview preview, VertexConsumer fill,
            AABB override, BlockPos anchor) throws Exception {
        Method render = DestructiveGhostRenderer.class.getDeclaredMethod(
                "renderDestructiveGhost", ShapeDataRecords.GhostPreview.class, PoseStack.class,
                VertexConsumer.class, VertexConsumer.class, float.class, float.class,
                boolean.class, boolean.class, AABB.class, BlockPos.class);
        render.setAccessible(true);
        render.invoke(null, preview, new PoseStack(), mock(VertexConsumer.class), fill,
                0.0F, 1.0F, true, false, override, anchor);
    }

    private static void assertBounds(List<float[]> vertices,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        assertFalse(vertices.isEmpty(), "应当实际发射渲染顶点");
        double[] expectedMin = {minX, minY, minZ};
        double[] expectedMax = {maxX, maxY, maxZ};
        for (int axis = 0; axis < 3; axis++) {
            double actualMin = Double.POSITIVE_INFINITY;
            double actualMax = Double.NEGATIVE_INFINITY;
            for (float[] vertex : vertices) {
                actualMin = Math.min(actualMin, vertex[axis]);
                actualMax = Math.max(actualMax, vertex[axis]);
            }
            assertEquals(expectedMin[axis], actualMin, EPSILON, "最小坐标轴 " + axis);
            assertEquals(expectedMax[axis], actualMax, EPSILON, "最大坐标轴 " + axis);
        }
    }

    private static void assertVertex(List<float[]> vertices, double x, double y, double z) {
        assertTrue(vertices.stream().anyMatch(vertex ->
                        Math.abs(vertex[0] - x) < EPSILON
                                && Math.abs(vertex[1] - y) < EPSILON
                                && Math.abs(vertex[2] - z) < EPSILON),
                () -> "缺少精确顶点 (" + x + ", " + y + ", " + z + ")");
    }

    /** 执行接口的矩阵变换默认方法，仅记录最终三个 float 坐标并接受颜色/法线链。 */
    private static final class RecordingVertices {
        private final List<float[]> vertices = new ArrayList<>();
        private final VertexConsumer consumer = mock(VertexConsumer.class, invocation -> {
            Method method = invocation.getMethod();
            if (method.isDefault()) {
                return invocation.callRealMethod();
            }
            if (method.getName().equals("addVertex") && method.getParameterCount() == 3) {
                vertices.add(new float[]{invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)});
            }
            return method.getReturnType() == VertexConsumer.class
                    ? invocation.getMock() : RETURNS_DEFAULTS.answer(invocation);
        });
    }
}
