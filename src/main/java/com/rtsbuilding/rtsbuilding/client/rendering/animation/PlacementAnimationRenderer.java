package com.rtsbuilding.rtsbuilding.client.rendering.animation;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.rtsbuilding.rtsbuilding.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Facade that coordinates pending placement ghosts and destroy animations.
 * <p>
 * Delegates to {@link PendingGhostRenderer} for client-submitted-but-unconfirmed
 * placement ghosts, and to {@link DestroyGhostRenderer} for brief shrink-out
 * skeletons on server-confirmed block destruction.
 */
public final class PlacementAnimationRenderer {

    private PlacementAnimationRenderer() {
    }

    // ===== Pending ghost delegation =====

    /**
     * Registers pending placement ghosts at the given positions.
     *
     * @see PendingGhostRenderer#addPendingBatch(List, BlockState)
     */
    public static void addPendingBatch(List<BlockPos> positions, BlockState blockState) {
        PendingGhostRenderer.addPendingBatch(positions, blockState);
    }

    /**
     * Removes the pending ghost at the given position and plays the confirmed
     * grow-in animation when placement visuals are enabled.
     *
     * @see PendingGhostRenderer#remove(BlockPos)
     */
    public static void confirmPlacement(BlockPos pos, BlockState state) {
        PendingGhostRenderer.remove(pos);
        if (shouldRenderPlacementLayers()) {
            ConfirmedPlacementRenderer.add(pos, state);
        }
    }

    // ===== Destroy ghost delegation =====

    /**
     * Registers a position for destroy shrink-out animation (server confirmed).
     *
     * @see DestroyGhostRenderer#add(BlockPos)
     */
    public static void addDestroy(BlockPos pos, BlockState state) {
        // Clean up any lingering pending ghost that was never confirmed
        // (e.g. placement confirmation packet hadn't arrived before destruction)
        PendingGhostRenderer.remove(pos);
        if (shouldRenderPlacementLayers()) {
            DestroyGhostRenderer.add(pos, state);
        }
    }

    // ===== Render =====

    /**
     * Renders all ghost effects. The wireframe/translucent mode switch is
     * controlled by the global config.
     */
    public static void render(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        boolean blockGhost = Config.isBlockGhostPreviewEnabled();
        boolean wireframe = Config.isWireframePreviewEnabled();
        if (blockGhost) {
            PendingGhostRenderer.render(minecraft, poseStack, lineBuffer, fillBuffer);
            ConfirmedPlacementRenderer.renderModels(minecraft, poseStack, fillBuffer);
            DestroyGhostRenderer.renderModels(minecraft, poseStack, fillBuffer);
        }
        if (wireframe) {
            PendingGhostRenderer.renderWireframes(poseStack, lineBuffer);
            ConfirmedPlacementRenderer.renderWireframes(poseStack, lineBuffer);
            DestroyGhostRenderer.renderWireframes(poseStack, lineBuffer);
        }
    }

    private static boolean shouldRenderPlacementLayers() {
        return Config.isBlockGhostPreviewEnabled() || Config.isWireframePreviewEnabled();
    }
}
