package com.rtsbuilding.rtsbuilding.client.rendering.animation;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.rtsbuilding.rtsbuilding.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Coordinates pending placement ghosts and short server-confirmed placement /
 * break animations. These effects are visual-only; gameplay state remains
 * server-authoritative.
 */
public final class PlacementAnimationRenderer {
    private PlacementAnimationRenderer() {
    }

    public static void addPendingBatch(List<BlockPos> positions, BlockState blockState) {
        PendingGhostRenderer.addPendingBatch(positions, blockState);
    }

    public static void confirmPlacement(BlockPos pos, BlockState state) {
        PendingGhostRenderer.remove(pos);
        if (shouldRenderPlacementLayers()) {
            ConfirmedPlacementRenderer.add(pos, state);
        }
    }

    public static void addDestroy(BlockPos pos, BlockState state) {
        PendingGhostRenderer.remove(pos);
        if (shouldRenderPlacementLayers()) {
            DestroyGhostRenderer.add(pos, state);
        }
    }

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
