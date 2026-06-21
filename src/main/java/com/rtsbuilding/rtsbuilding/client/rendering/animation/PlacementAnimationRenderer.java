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
        if (shouldRenderPlaceAnimationLayers()) {
            ConfirmedPlacementRenderer.add(pos, state);
        }
    }

    public static void addDestroy(BlockPos pos, BlockState state) {
        PendingGhostRenderer.remove(pos);
        if (shouldRenderDestroyLayers()) {
            DestroyGhostRenderer.add(pos, state);
        }
    }

    public static void render(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        boolean previewBlockGhost = Config.isPlacementBlockGhostPreviewEnabled();
        boolean placeBlockGhost = Config.isPlaceBlockGhostAnimationEnabled();
        boolean destroyBlockGhost = Config.isDestroyBlockGhostAnimationEnabled();
        boolean previewWireframe = Config.isPlacementWireframePreviewEnabled();
        boolean placeWireframe = Config.isPlaceWireframeAnimationEnabled();
        boolean destroyWireframe = Config.isDestroyWireframeAnimationEnabled();
        if (previewBlockGhost) {
            PendingGhostRenderer.render(minecraft, poseStack, lineBuffer, fillBuffer);
        }
        if (placeBlockGhost) {
            ConfirmedPlacementRenderer.renderModels(minecraft, poseStack, fillBuffer);
        }
        if (destroyBlockGhost) {
            DestroyGhostRenderer.renderModels(minecraft, poseStack, fillBuffer);
        }
        if (previewWireframe) {
            PendingGhostRenderer.renderWireframes(poseStack, lineBuffer);
        }
        if (placeWireframe) {
            ConfirmedPlacementRenderer.renderWireframes(poseStack, lineBuffer);
        }
        if (destroyWireframe) {
            DestroyGhostRenderer.renderWireframes(poseStack, lineBuffer);
        }
    }

    private static boolean shouldRenderPlaceAnimationLayers() {
        return Config.isPlaceBlockGhostAnimationEnabled() || Config.isPlaceWireframeAnimationEnabled();
    }

    private static boolean shouldRenderDestroyLayers() {
        return Config.isDestroyBlockGhostAnimationEnabled() || Config.isDestroyWireframeAnimationEnabled();
    }
}
