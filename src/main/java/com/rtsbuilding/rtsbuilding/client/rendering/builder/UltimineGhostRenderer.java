package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.*;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RaycastHelper;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders the chain-mining (ultimine) ghost preview in FTB Ultimine style,
 * and provides shared two-pass edge rendering primitives used by
 * {@link MergedSkeletonRenderer}.
 */
public final class UltimineGhostRenderer {

    // ── Custom no-depth translucent line render type ──

    // ── Custom no-depth translucent quad render type (for entity brackets) ──

    // ── Custom opaque depth-tested quad render type (for entity brackets) ──

    // ── Breathing colour parameters ──

    private static final float BREATH_SPEED = 0.2F;
    private static final float BREATH_MIN_FACTOR = 0.7F;

    private UltimineGhostRenderer() {
    }

    // ===== Public API (called from ShapeGhostRenderer) =====

    static void render(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress) {
        // If the player's mouse is hovering over an entity, render entity selection
        // brackets instead of the chain-destroy (ultimine) ghost preview.
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null && mc.getCameraEntity() != null
                && mc.screen instanceof com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen) {
            Vec3 camPos = mc.gameRenderer.getMainCamera().position();
            Vec3 viewDir = RaycastHelper.computeCursorRayDirection(mc);
            Vec3 rayEnd = camPos.add(viewDir.scale(128.0D));
            EntityHitResult entityHit = RaycastHelper.raycastEntityFromCursor(mc, camPos, rayEnd, viewDir, 128.0D);
            if (entityHit != null) {
                renderEntityBrackets(poseStack, entityHit.getEntity(), camPos);
                return;
            }
        }

        List<BlockPos> blocks = preview.blocks();
        if (blocks == null || blocks.isEmpty()) return;

        // Step 1 — outer perimeter filtering
        List<BlockPos> outerBlocks = filterOuterBlocks(blocks);
        if (outerBlocks.isEmpty()) return;

        // Step 2 — merge adjacent outer blocks and extract edge segments
        List<UltimineBlockMerger.EdgeLine> edges = UltimineBlockMerger.getEdgeLines(outerBlocks);
        if (edges.isEmpty()) return;

        Matrix4f matrix = poseStack.last().pose();

        // Step 3 — breathing gold colour
        float breathFactor = RenderingUtil.getBreathFactor(BREATH_SPEED, BREATH_MIN_FACTOR);
        float r = 1.00F * breathFactor;
        float g = 0.72F * breathFactor;
        float b = 0.24F * breathFactor;
        float edgeR = RenderingUtil.lerp(r, 0.38F, progress);
        float edgeG = RenderingUtil.lerp(g, 1.00F, progress);
        float edgeB = RenderingUtil.lerp(b, 0.42F, progress);

        // Step 4 — opaque depth-tested edges
        renderPass1(edges, matrix, lineBuffer, edgeR, edgeG, edgeB);

        // Step 5 — translucent no-depth edges
        renderPass2(edges, matrix, edgeR, edgeG, edgeB, 0.34F);

        // Step 6 — faint per-block fill
        renderFill(outerBlocks, poseStack, fillBuffer, edgeR, edgeG, edgeB, 0.08F);
    }

    // ===== Shared edge rendering primitives (used by MergedSkeletonRenderer) =====

    static void renderPass1(List<UltimineBlockMerger.EdgeLine> edges, Matrix4f matrix,
            VertexConsumer lineBuffer, float r, float g, float b) {
        renderPass1(edges, matrix, lineBuffer, r, g, b, 0.95F);
    }

    static void renderPass1(List<UltimineBlockMerger.EdgeLine> edges, Matrix4f matrix,
            VertexConsumer lineBuffer, float r, float g, float b, float alpha) {
        for (UltimineBlockMerger.EdgeLine edge : edges) {
            lineBuffer.addVertex(matrix, (float) edge.x1(), (float) edge.y1(), (float) edge.z1())
                    .setColor(r, g, b, alpha)
                    .setNormal(edge.xn(), edge.yn(), edge.zn());
            lineBuffer.addVertex(matrix, (float) edge.x2(), (float) edge.y2(), (float) edge.z2())
                    .setColor(r, g, b, alpha)
                    .setNormal(edge.xn(), edge.yn(), edge.zn());
        }
    }

    static void renderPass2(List<UltimineBlockMerger.EdgeLine> edges, Matrix4f matrix,
            float r, float g, float b, float alpha) {
        // 26.1 的无深度副通道需要由 SubmitCustomGeometry 阶段统一提交。
        // 主线框仍由 renderPass1 绘制，避免此处重新引入共享缓冲区的所有权问题。
    }

    static void renderFill(List<BlockPos> outerBlocks, PoseStack poseStack,
            VertexConsumer fillBuffer, float r, float g, float b, float fillA) {
        for (BlockPos pos : outerBlocks) {
            com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                    r, g, b, fillA);
        }
    }

    // ===== Entity bracket rendering (ultimine override when hovering entity) =====

    /**
     * Renders entity selection corner brackets when the player's mouse is over an
     * entity during ultimine (chain-destroy) mode. This overrides the block-level
     * chain-mining ghost preview so the player can clearly see that the cursor
     * is targeting an entity rather than blocks.
     */
    private static void renderEntityBrackets(PoseStack poseStack,
            Entity entity, Vec3 camPos) {
        // 旧实现自己创建并提交 RenderType，26.1 已移除这条即时路径。
        // 实体角标将在统一几何提交适配器接入后恢复，先保证不会破坏世界渲染缓冲区。
    }

    // ===== Private helpers =====

    /**
     * Filters to outer-perimeter blocks (at least one face neighbour outside
     * the selection set).
     */
    private static List<BlockPos> filterOuterBlocks(List<BlockPos> blocks) {
        Set<BlockPos> allBlocks = new HashSet<>(blocks);
        BlockPos[] faceOffsets = {
                new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
                new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
                new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
        };
        List<BlockPos> outerBlocks = new ArrayList<>();
        for (BlockPos pos : blocks) {
            boolean isOuter = false;
            for (BlockPos offset : faceOffsets) {
                if (!allBlocks.contains(pos.offset(offset))) {
                    isOuter = true;
                    break;
                }
            }
            if (isOuter) outerBlocks.add(pos);
        }
        return outerBlocks;
    }
}
