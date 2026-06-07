package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostAlphaBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;

/**
 * Renders build-mode ghost previews — translucent block models or fallback
 * coloured cell outlines for shape placement (walls, floors, blocks, etc.).
 */
public final class BuildGhostRenderer {

    /** Alpha value applied to build-mode ghost block models. */
    static final float BUILD_GHOST_ALPHA = 0.8F;

    private static final double BOUNDARY_PADDING = 0.02D;

    // ── Custom no-depth translucent line render type (for envelope outer pass) ──

    private static final RenderType LINES_NO_DEPTH = RenderType.create(
            "rtsbuilding_build_env_no_depth",
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

    private BuildGhostRenderer() {
    }

    // ===== Public API (called from ShapeGhostRenderer) =====

    /**
     * Renders build-mode ghosts using transparent block models if available,
     * or coloured cell outlines as fallback.
     */
    static void render(Minecraft minecraft, ShapeDataRecords.GhostPreview preview,
            PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        BlockState blockState = resolveBuildBlockState(minecraft);
        if (blockState != null && !blockState.isAir() && blockState.getRenderShape() == RenderShape.MODEL) {
            renderBuildGhostModels(minecraft, preview, poseStack, blockState, lineBuffer);
        } else {
            renderBuildGhostFallback(preview, poseStack, lineBuffer, fillBuffer);
        }
    }

    // ===== Build mode rendering =====

    /**
     * Resolves the {@link BlockState} for build-mode ghost rendering.
     * Priority: selected RTS item → main hand item.
     */
    private static BlockState resolveBuildBlockState(Minecraft minecraft) {
        ClientRtsController controller = ClientRtsController.get();
        ItemStack itemPreview = controller.getSelectedItemPreview();
        if (!itemPreview.isEmpty() && itemPreview.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState();
        }
        if (minecraft.player != null) {
            ItemStack mainHand = minecraft.player.getMainHandItem();
            if (mainHand.getItem() instanceof BlockItem blockItem) {
                return blockItem.getBlock().defaultBlockState();
            }
        }
        return null;
    }

    /**
     * Renders translucent block models with an overall bounding-box outline.
     */
    private static void renderBuildGhostModels(Minecraft minecraft, ShapeDataRecords.GhostPreview preview,
            PoseStack poseStack, BlockState blockState, VertexConsumer lineBuffer) {
        List<BlockPos> blocks = preview.blocks();
        if (blocks.isEmpty()) {
            return;
        }

        // Compute overall bounding box
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1);
            maxY = Math.max(maxY, pos.getY() + 1);
            maxZ = Math.max(maxZ, pos.getZ() + 1);
        }

        // Render translucent models
        MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();
        MultiBufferSource translucentBuffer = new GhostAlphaBufferSource(blockBuffer, BUILD_GHOST_ALPHA);

        for (BlockPos pos : blocks) {
            int light = LevelRenderer.getLightColor(minecraft.level, pos);
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            minecraft.getBlockRenderer().renderSingleBlock(
                    blockState, poseStack, translucentBuffer,
                    light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        blockBuffer.endBatch();

        // Overall bounding-box outline
        float lineR = preview.readyConfirm() ? 0.45F : 0.30F;
        float lineG = preview.readyConfirm() ? 0.95F : 0.75F;
        float lineB = preview.readyConfirm() ? 0.45F : 1.00F;
        LevelRenderer.renderLineBox(poseStack, lineBuffer,
                minX - BOUNDARY_PADDING, minY - BOUNDARY_PADDING, minZ - BOUNDARY_PADDING,
                maxX + BOUNDARY_PADDING, maxY + BOUNDARY_PADDING, maxZ + BOUNDARY_PADDING,
                lineR, lineG, lineB, 0.95F);

        // ── Transparent no-depth envelope line box (visible through terrain) ──
        float ndAlpha = 0.20F;
        renderEnvelopeNoDepthLineBox(poseStack,
                minX - BOUNDARY_PADDING, minY - BOUNDARY_PADDING, minZ - BOUNDARY_PADDING,
                maxX + BOUNDARY_PADDING, maxY + BOUNDARY_PADDING, maxZ + BOUNDARY_PADDING,
                lineR, lineG, lineB, ndAlpha);
    }

    /**
     * Fallback rendering: coloured cell outlines + fill when block state
     * cannot be resolved (non-block item or air).
     */
    private static void renderBuildGhostFallback(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        List<BlockPos> blocks = preview.blocks();
        if (blocks.isEmpty()) {
            return;
        }

        float fillR = preview.readyConfirm() ? 0.24F : 0.16F;
        float fillG = preview.readyConfirm() ? 0.72F : 0.55F;
        float fillB = preview.readyConfirm() ? 0.24F : 0.90F;
        float fillA = preview.readyConfirm() ? 0.22F : 0.16F;
        float lineR = preview.readyConfirm() ? 0.45F : 0.30F;
        float lineG = preview.readyConfirm() ? 0.95F : 0.75F;
        float lineB = preview.readyConfirm() ? 0.45F : 1.00F;

        for (BlockPos pos : blocks) {
            double cellMinX = pos.getX() + 0.03D;
            double cellMinY = pos.getY() + 0.03D;
            double cellMinZ = pos.getZ() + 0.03D;
            double cellMaxX = pos.getX() + 0.97D;
            double cellMaxY = pos.getY() + 0.97D;
            double cellMaxZ = pos.getZ() + 0.97D;

            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    cellMinX, cellMinY, cellMinZ,
                    cellMaxX, cellMaxY, cellMaxZ,
                    fillR, fillG, fillB, fillA);

            LevelRenderer.renderLineBox(
                    poseStack, lineBuffer,
                    cellMinX, cellMinY, cellMinZ,
                    cellMaxX, cellMaxY, cellMaxZ,
                    lineR, lineG, lineB, 0.95F);
        }
    }

    // ===== No-depth envelope rendering =====

    /** Renders a transparent no-depth line box (visible through world geometry). */
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
}
