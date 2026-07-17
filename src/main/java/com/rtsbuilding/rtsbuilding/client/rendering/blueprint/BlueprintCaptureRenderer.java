package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.rendering.selection.RtsBoxHandleRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Blueprint capture box renderer.
 * Renders the selection box, included block highlights,
 * and excluded block markers during blueprint recording.
 */
public final class BlueprintCaptureRenderer {
    // Max number of included block highlights to prevent performance issues
    private static final int CAPTURE_BLOCK_HIGHLIGHT_LIMIT = 8192;
    // Max number of excluded block highlights
    private static final int CAPTURE_EXCLUDED_HIGHLIGHT_LIMIT = 1024;

    // Optimisation: extracted colour constants for easy adjustment
    private static final float INCLUDED_BLOCK_R = 0.12F;
    private static final float INCLUDED_BLOCK_G = 0.56F;
    private static final float INCLUDED_BLOCK_B = 1.0F;
    private static final float INCLUDED_BLOCK_A = 0.11F;

    private static final float EXCLUDED_BLOCK_R = 1.0F;
    private static final float EXCLUDED_BLOCK_G = 0.36F;
    private static final float EXCLUDED_BLOCK_B = 0.12F;
    private static final float EXCLUDED_BLOCK_LINE_A = 0.95F;
    private static final float EXCLUDED_BLOCK_FILL_A = 0.24F;
    private static final float EXCLUDED_BLOCK_MARK_A = 0.72F;

    private static final float BOUNDARY_BOX_R = 0.35F;
    private static final float BOUNDARY_BOX_G = 0.78F;
    private static final float BOUNDARY_BOX_B = 1.0F;
    private static final float BOUNDARY_BOX_A = 0.95F;

    /**
     * Private constructor to prevent instantiation.
     */
    private BlueprintCaptureRenderer() {
    }

    /**
     * Renders the blueprint capture selection box and highlights.
     *
     * @param poseStack  Pose stack for coordinate transforms
     * @param lineBuffer Line vertex buffer
     * @param fillBuffer Fill vertex buffer
     */
    public static void renderBlueprintCaptureBox(PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            VertexConsumer handleLineBuffer, VertexConsumer handleFillBuffer) {
        RtsCullingBox box = BlueprintPanel.getCapturePreviewBoxForRender();
        if (box == null) {
            return;
        }
        AABB renderBox = BlueprintPanel.getCapturePreviewAabbForRender();
        if (renderBox == null) {
            return;
        }

        // Compute bounding box edges (expand 0.01 units to prevent Z-fighting)
        double minX = renderBox.minX - 0.01D;
        double minY = renderBox.minY - 0.01D;
        double minZ = renderBox.minZ - 0.01D;
        double maxX = renderBox.maxX + 0.01D;
        double maxY = renderBox.maxY + 0.01D;
        double maxZ = renderBox.maxZ + 0.01D;

        // Get the list of included blocks (subject to limit)
        List<BlockPos> includedBlocks = BlueprintPanel.getCaptureIncludedBlocksForRender(CAPTURE_BLOCK_HIGHLIGHT_LIMIT);

        // Render a translucent blue fill when not showing individual highlights
        // Render blue highlights for each included block
        for (BlockPos pos : includedBlocks) {
            com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.addChainedFilledBoxVertices(
                    poseStack,
                    fillBuffer,
                    pos.getX() + 0.04D, pos.getY() + 0.04D, pos.getZ() + 0.04D,
                    pos.getX() + 0.96D, pos.getY() + 0.96D, pos.getZ() + 0.96D,
                    INCLUDED_BLOCK_R, INCLUDED_BLOCK_G, INCLUDED_BLOCK_B, INCLUDED_BLOCK_A);
        }

        // Render red wireframe for each excluded block
        for (BlockPos pos : BlueprintPanel.getCaptureExcludedBlocksForRender(CAPTURE_EXCLUDED_HIGHLIGHT_LIMIT)) {
            com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.addChainedFilledBoxVertices(
                    poseStack,
                    fillBuffer,
                    pos.getX() + 0.07D, pos.getY() + 0.07D, pos.getZ() + 0.07D,
                    pos.getX() + 0.93D, pos.getY() + 0.93D, pos.getZ() + 0.93D,
                    EXCLUDED_BLOCK_R, EXCLUDED_BLOCK_G, EXCLUDED_BLOCK_B, EXCLUDED_BLOCK_FILL_A);
            com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.addChainedFilledBoxVertices(
                    poseStack,
                    fillBuffer,
                    pos.getX() + 0.18D, pos.getY() + 0.91D, pos.getZ() + 0.18D,
                    pos.getX() + 0.82D, pos.getY() + 0.99D, pos.getZ() + 0.82D,
                    EXCLUDED_BLOCK_R, EXCLUDED_BLOCK_G, EXCLUDED_BLOCK_B, EXCLUDED_BLOCK_MARK_A);
            com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    pos.getX() + 0.06D, pos.getY() + 0.06D, pos.getZ() + 0.06D,
                    pos.getX() + 0.94D, pos.getY() + 0.94D, pos.getZ() + 0.94D,
                    EXCLUDED_BLOCK_R, EXCLUDED_BLOCK_G, EXCLUDED_BLOCK_B, EXCLUDED_BLOCK_LINE_A);
        }

        // Render the blue bounding box outline for the entire selection
        com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.renderLineBox(
                poseStack,
                lineBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                BOUNDARY_BOX_R, BOUNDARY_BOX_G, BOUNDARY_BOX_B, BOUNDARY_BOX_A);
        if (BlueprintPanel.isCaptureSelectionComplete()) {
            RtsBoxHandleRenderer.renderAxisHandles(
                    poseStack,
                    handleLineBuffer,
                    handleFillBuffer,
                    renderBox,
                    BlueprintPanel.getCaptureHoveredHandleDirection(),
                    BlueprintPanel.getCaptureActiveHandleDirection());
        }
    }
}
