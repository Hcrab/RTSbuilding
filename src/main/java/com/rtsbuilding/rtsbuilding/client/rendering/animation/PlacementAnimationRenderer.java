package com.rtsbuilding.rtsbuilding.client.rendering.animation;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

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
     * Removes the pending ghost at the given position (server confirmed placement).
     *
     * @see PendingGhostRenderer#remove(BlockPos)
     */
    public static void confirmPlacement(BlockPos pos) {
        PendingGhostRenderer.remove(pos);
    }

    // ===== Destroy ghost delegation =====

    /**
     * Registers a position for destroy shrink-out animation (server confirmed).
     *
     * @see DestroyGhostRenderer#add(BlockPos)
     */
    public static void addDestroy(BlockPos pos) {
        // Clean up any lingering pending ghost that was never confirmed
        // (e.g. placement confirmation packet hadn't arrived before destruction)
        PendingGhostRenderer.remove(pos);
        DestroyGhostRenderer.add(pos);
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
        if (com.rtsbuilding.rtsbuilding.Config.isWireframePreviewEnabled()) {
            PendingGhostRenderer.renderWireframes(poseStack, lineBuffer);
            DestroyGhostRenderer.render(poseStack, lineBuffer);
        } else {
            PendingGhostRenderer.render(minecraft, poseStack, lineBuffer, fillBuffer);
            DestroyGhostRenderer.render(poseStack, lineBuffer);
        }
    }
}
