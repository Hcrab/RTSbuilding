package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.core.BlockPos;

import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;

/**
 * Renders destructive (range-destroy) ghost previews — per-block coloured
 * outlines with an envelope around non-breakable blocks.
 * <p>
 * Per-block cell boxes are shown with colours indicating the current
 * confirm state ({@code readyConfirm}).
 */
public final class DestructiveGhostRenderer {

    private static final double BOUNDARY_PADDING = 0.02D;

    // ── Custom no-depth translucent line render type (for envelope outer pass) ──

    private static final RenderType LINES_NO_DEPTH = RenderType.create(
            "rtsbuilding_destructive_env_no_depth",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            512,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(RenderStateShard.DEFAULT_LINE)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private static final ByteBufferBuilder LINES_NO_DEPTH_BACKING = new ByteBufferBuilder(LINES_NO_DEPTH.bufferSize());

    private DestructiveGhostRenderer() {
    }

    // ===== Public API (called from ShapeGhostRenderer) =====

    /**
     * Renders destructive ghost with per-block cells and outer envelope.
     */
    static void render(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float alphaMultiplier) {
        render(preview, poseStack, lineBuffer, fillBuffer, progress, alphaMultiplier, true, true);
    }

    static void render(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float alphaMultiplier,
            boolean renderFill, boolean renderLines) {
        renderDestructiveGhost(preview, poseStack, lineBuffer, fillBuffer, progress, alphaMultiplier,
                renderFill, renderLines);
    }

    /**
     * Renders the wireframe preview for both build and destructive modes.
     */
    static void renderWireframe(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, float progress) {
        boolean destructive = preview.destructive();
        boolean readyConfirm = preview.readyConfirm();

        // Envelope for destructive region
        if (destructive && (!RenderingUtil.isEmpty(preview.blocks()) || !RenderingUtil.isEmpty(preview.emptyBlocks()))) {
            float envLineR = RenderingUtil.lerp(1.00F, 0.38F, progress);
            float envLineG = RenderingUtil.lerp(0.86F, 1.00F, progress);
            float envLineB = RenderingUtil.lerp(0.22F, 0.42F, progress);
            renderWireframeEnvelope(poseStack, lineBuffer, preview.blocks(), preview.emptyBlocks(),
                    envLineR, envLineG, envLineB, 0.78F);
        }

        if (preview.blocks() == null || preview.blocks().isEmpty()) {
            return;
        }

        if (destructive) {
            // ── Per-block cell line boxes ──
            DestructiveCellColors dcc = DestructiveCellColors.forConfirmState(readyConfirm);
            for (BlockPos pos : preview.blocks()) {
                double cellMinX = pos.getX() + 0.03D;
                double cellMinY = pos.getY() + 0.03D;
                double cellMinZ = pos.getZ() + 0.03D;
                double cellMaxX = pos.getX() + 0.97D;
                double cellMaxY = pos.getY() + 0.97D;
                double cellMaxZ = pos.getZ() + 0.97D;

                LevelRenderer.renderLineBox(poseStack, lineBuffer,
                        cellMinX, cellMinY, cellMinZ,
                        cellMaxX, cellMaxY, cellMaxZ,
                        dcc.lineR(), dcc.lineG(), dcc.lineB(), dcc.lineA());
            }
        } else {
            // ── Build mode boxes ──
            for (BlockPos pos : preview.blocks()) {
                double cellMinX = pos.getX() + 0.03D;
                double cellMinY = pos.getY() + 0.03D;
                double cellMinZ = pos.getZ() + 0.03D;
                double cellMaxX = pos.getX() + 0.97D;
                double cellMaxY = pos.getY() + 0.97D;
                double cellMaxZ = pos.getZ() + 0.97D;

                float lineR = readyConfirm ? 0.45F : 0.30F;
                float lineG = readyConfirm ? 0.95F : 0.75F;
                float lineB = readyConfirm ? 0.45F : 1.00F;
                LevelRenderer.renderLineBox(poseStack, lineBuffer,
                        cellMinX, cellMinY, cellMinZ,
                        cellMaxX, cellMaxY, cellMaxZ,
                        lineR, lineG, lineB, 0.95F);
            }
        }
    }

    // ===== Destructive ghost rendering =====

    private static void renderDestructiveGhost(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float alphaMultiplier,
            boolean renderFill, boolean renderLines) {
        float alpha = RenderingUtil.clamp01(alphaMultiplier);
        if (alpha <= 0.0F || (!renderFill && !renderLines)) return;

        // Outer envelope (yellow → green transition) — always rendered
        if (!RenderingUtil.isEmpty(preview.blocks()) || !RenderingUtil.isEmpty(preview.emptyBlocks())) {
            float envLineR = RenderingUtil.lerp(1.00F, 0.38F, progress);
            float envLineG = RenderingUtil.lerp(0.86F, 1.00F, progress);
            float envLineB = RenderingUtil.lerp(0.22F, 0.42F, progress);
            float envFillR = RenderingUtil.lerp(1.00F, 0.30F, progress);
            float envFillG = RenderingUtil.lerp(0.86F, 0.95F, progress);
            float envFillB = RenderingUtil.lerp(0.18F, 0.36F, progress);
            renderGhostEnvelope(poseStack, lineBuffer, fillBuffer,
                    preview.blocks(), preview.emptyBlocks(),
                    envLineR, envLineG, envLineB, 0.78F * alpha,
                    envFillR, envFillG, envFillB, 0.10F * alpha,
                    renderFill, renderLines);
        }

        List<BlockPos> blocks = preview.blocks();
        if (blocks == null || blocks.isEmpty()) return;

        // ── Per-block cell highlights ──
        DestructiveCellColors dcc = DestructiveCellColors.forConfirmState(preview.readyConfirm());
        renderPerBlockCells(blocks, poseStack, lineBuffer, fillBuffer, progress, alpha, dcc,
                renderFill, renderLines);
    }



    // ===== Per-block cell rendering (confirmed state) =====

    private static void renderPerBlockCells(List<BlockPos> blocks, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float alpha,
            DestructiveCellColors dcc, boolean renderFill, boolean renderLines) {
        float lineR = RenderingUtil.lerp(dcc.lineR(), 0.38F, progress) * alpha;
        float lineG = RenderingUtil.lerp(dcc.lineG(), 1.00F, progress);
        float lineB = RenderingUtil.lerp(dcc.lineB(), 0.42F, progress);
        float lineA = dcc.lineA() * alpha;
        float fillR = RenderingUtil.lerp(dcc.fillR(), 0.30F, progress);
        float fillG = RenderingUtil.lerp(dcc.fillG(), 0.95F, progress);
        float fillB = RenderingUtil.lerp(dcc.fillB(), 0.36F, progress);
        float fillA = dcc.fillA() * alpha;

        for (BlockPos pos : blocks) {
            double cellMinX = pos.getX() + 0.03D;
            double cellMinY = pos.getY() + 0.03D;
            double cellMinZ = pos.getZ() + 0.03D;
            double cellMaxX = pos.getX() + 0.97D;
            double cellMaxY = pos.getY() + 0.97D;
            double cellMaxZ = pos.getZ() + 0.97D;

            if (renderFill) {
                LevelRenderer.addChainedFilledBoxVertices(
                        poseStack, fillBuffer,
                        cellMinX, cellMinY, cellMinZ,
                        cellMaxX, cellMaxY, cellMaxZ,
                        fillR, fillG, fillB, fillA);
            }
            if (renderLines) {
                LevelRenderer.renderLineBox(
                        poseStack, lineBuffer,
                        cellMinX, cellMinY, cellMinZ,
                        cellMaxX, cellMaxY, cellMaxZ,
                        lineR, lineG, lineB, lineA);
            }
        }
    }

    // ===== Envelope rendering =====

    /** Renders a combined bounding-box envelope (line + fill) plus a transparent no-depth pass. */
    private static void renderGhostEnvelope(PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            List<BlockPos> primaryBlocks, List<BlockPos> envelopeBlocks,
            float lineR, float lineG, float lineB, float lineA,
            float fillR, float fillG, float fillB, float fillA,
            boolean renderFill, boolean renderLines) {
        RenderingUtil.Bounds bounds = RenderingUtil.Bounds.from(primaryBlocks, envelopeBlocks);
        if (bounds == null) return;

        double padding = BOUNDARY_PADDING;
        double minX = bounds.minX() - padding;
        double minY = bounds.minY() - padding;
        double minZ = bounds.minZ() - padding;
        double maxX = bounds.maxX() + 1.0D + padding;
        double maxY = bounds.maxY() + 1.0D + padding;
        double maxZ = bounds.maxZ() + 1.0D + padding;

        if (renderFill) {
            LevelRenderer.addChainedFilledBoxVertices(poseStack, fillBuffer,
                    minX, minY, minZ, maxX, maxY, maxZ,
                    fillR, fillG, fillB, fillA);
        }

        if (renderLines) {
            LevelRenderer.renderLineBox(poseStack, lineBuffer,
                    minX, minY, minZ, maxX, maxY, maxZ,
                    lineR, lineG, lineB, lineA);

        // ── Transparent no-depth envelope line box (visible through terrain) ──
            float ndAlpha = 0.20F;
            renderEnvelopeNoDepthLineBox(poseStack, minX, minY, minZ, maxX, maxY, maxZ,
                    lineR, lineG, lineB, ndAlpha);
        }
    }

    private static void renderWireframeEnvelope(PoseStack poseStack, VertexConsumer lineBuffer,
            List<BlockPos> primaryBlocks, List<BlockPos> envelopeBlocks,
            float lineR, float lineG, float lineB, float lineA) {
        RenderingUtil.Bounds bounds = RenderingUtil.Bounds.from(primaryBlocks, envelopeBlocks);
        if (bounds == null) return;
        double padding = BOUNDARY_PADDING;
        double minX = bounds.minX() - padding;
        double minY = bounds.minY() - padding;
        double minZ = bounds.minZ() - padding;
        double maxX = bounds.maxX() + 1.0D + padding;
        double maxY = bounds.maxY() + 1.0D + padding;
        double maxZ = bounds.maxZ() + 1.0D + padding;

        LevelRenderer.renderLineBox(poseStack, lineBuffer,
                minX, minY, minZ, maxX, maxY, maxZ,
                lineR, lineG, lineB, lineA);

        // ── Transparent no-depth envelope line box (visible through terrain) ──
        float ndAlpha = 0.20F;
        renderEnvelopeNoDepthLineBox(poseStack, minX, minY, minZ, maxX, maxY, maxZ,
                lineR, lineG, lineB, ndAlpha);
    }

    // ===== No-depth envelope rendering =====

    /** Renders a transparent no-depth line box for the envelope (visible through world geometry). */
    private static void renderEnvelopeNoDepthLineBox(PoseStack poseStack,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            float r, float g, float b, float alpha) {
        BufferBuilder ndBuffer = new BufferBuilder(LINES_NO_DEPTH_BACKING, VertexFormat.Mode.LINES,
                DefaultVertexFormat.POSITION_COLOR_NORMAL);
        LevelRenderer.renderLineBox(poseStack, ndBuffer,
                minX, minY, minZ, maxX, maxY, maxZ,
                r, g, b, alpha);
        var meshData = ndBuffer.build();
        if (meshData != null) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            LINES_NO_DEPTH.draw(meshData);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    // ===== Internal records =====

    /** Cell rendering colours grouped by confirm state (only used for unconfirmed). */
    private record DestructiveCellColors(
            float lineR, float lineG, float lineB, float lineA,
            float fillR, float fillG, float fillB, float fillA) {
        private static DestructiveCellColors forConfirmState(boolean readyConfirm) {
            return new DestructiveCellColors(
                    1.00F,
                    readyConfirm ? 0.95F : 0.46F,
                    readyConfirm ? 0.45F : 0.64F,
                    readyConfirm ? 0.95F : 0.62F,
                    1.00F,
                    readyConfirm ? 0.72F : 0.25F,
                    readyConfirm ? 0.24F : 0.44F,
                    readyConfirm ? 0.22F : 0.07F
            );
        }
    }
}
