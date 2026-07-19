package com.rtsbuilding.rtsbuilding.client.rendering.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

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
        if (shouldRenderPlaceAnimationLayers()) {
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
        if (shouldRenderDestroyLayers()) {
            DestroyGhostRenderer.add(pos, state);
        }
    }

    // ===== Render =====

    /**
     * Renders all ghost effects. Placement feedback and destroy feedback have
     * separate visual layer settings so players can tune preview noise.
     */
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

    /**
     * 在 26.1 的提取阶段冻结需要走方块模型渲染层的动画。
     * 线框和纯色盒仍由统一几何提取器记录，两条链路不会直接刷新共享 buffer。
     */
    public static List<ModelAnimation> captureModelAnimations() {
        List<ModelAnimation> animations = new ArrayList<>();
        if (Config.isPlacementBlockGhostPreviewEnabled()) {
            PendingGhostRenderer.collectModelAnimations(animations);
        }
        if (Config.isPlaceBlockGhostAnimationEnabled()) {
            ConfirmedPlacementRenderer.collectModelAnimations(animations);
        }
        if (Config.isDestroyBlockGhostAnimationEnabled()) {
            DestroyGhostRenderer.collectModelAnimations(animations);
        }
        return List.copyOf(animations);
    }

    public record ModelAnimation(
            BlockState state,
            BlockPos pos,
            float alpha,
            float scale) {
    }

    private static boolean shouldRenderPlaceAnimationLayers() {
        return Config.isPlaceBlockGhostAnimationEnabled() || Config.isPlaceWireframeAnimationEnabled();
    }

    private static boolean shouldRenderDestroyLayers() {
        return Config.isDestroyBlockGhostAnimationEnabled() || Config.isDestroyWireframeAnimationEnabled();
    }
}
