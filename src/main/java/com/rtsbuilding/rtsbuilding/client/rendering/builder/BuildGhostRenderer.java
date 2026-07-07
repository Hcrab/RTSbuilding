package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Build-mode ghost renderer (facade class).
 * <p>
 * Orchestrates the complete rendering pipeline for single-block ghost previews,
 * delegating sub-tasks to dedicated sub-renderers:
 * <ul>
 *   <li>{@link BuildGhostBlockStateResolver} — block state resolution</li>
 *   <li>{@link BuildGhostModelRenderer} — translucent block model rendering</li>
 *   <li>{@link BuildGhostFillRenderer} — fallback fill box rendering</li>
 *   <li>{@link BuildGhostWireframeRenderer} — wireframe outline rendering</li>
 * </ul>
 * <p>
 * Public API is kept for backward compatibility.
 */
public final class BuildGhostRenderer {
    static final float BUILD_GHOST_ALPHA = 0.8F;

    private BuildGhostRenderer() {
    }

    /**
     * Renders the build-mode ghost preview. The model layer is toggleable, but
     * the blue preview wireframe is always rendered so range selection is never
     * invisible just because an animation setting was disabled.
     */
    static void render(Minecraft minecraft, ShapeDataRecords.GhostPreview preview,
            PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            boolean renderBlockGhost, boolean renderWireframe) {
        if (preview == null) {
            return;
        }
        List<BlockPos> blocks = preview.blocks();
        if (blocks.isEmpty()) {
            return;
        }
        BlockPos targetPos = blocks.isEmpty() ? null : blocks.get(0);
        boolean readyConfirm = preview.readyConfirm();

        // 1. Resolve placement-direction BlockState
        BlockState blockState = BuildGhostBlockStateResolver.resolve(minecraft, targetPos);

        // 2. Render translucent block model, entity ghost, or fallback fill
        if (renderBlockGhost) {
            if (blockState != null && !blockState.isAir() && blockState.getRenderShape() == RenderShape.MODEL) {
                BuildGhostModelRenderer.renderModels(minecraft, blocks, poseStack, blockState);
            } else {
                ItemStack spawnEggStack = BuildGhostBlockStateResolver.resolveSpawnEggStack(minecraft);
                if (!spawnEggStack.isEmpty()) {
                    EntityGhostRenderer.renderEntities(minecraft, blocks, poseStack, spawnEggStack);
                } else if (!BuildGhostBlockStateResolver.resolveEndCrystalStack(minecraft).isEmpty()) {
                    EntityGhostRenderer.renderEndCrystals(minecraft, blocks, poseStack);
                } else {
                    BuildGhostFillRenderer.renderFill(blocks, poseStack, fillBuffer, readyConfirm);
                }
            }
        }

        // 3. Render preview wireframe outlines. This is independent from animation settings.
        BuildGhostWireframeRenderer.renderWireframes(blocks, poseStack, lineBuffer, readyConfirm);
    }
}
