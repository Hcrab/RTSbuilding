package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 建造预览渲染入口。
 *
 * <p>单方块 Block 模式的蓝色线框遵守玩家设置；范围类建造的线框是选区反馈，
 * 始终保留。</p>
 */
public final class BuildGhostRenderer {
    private BuildGhostRenderer() {
    }

    static void render(Minecraft minecraft, ShapeDataRecords.GhostPreview preview,
            PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        if (preview == null) {
            return;
        }
        List<BlockPos> blocks = preview.blocks();
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        BlockPos targetPos = blocks.get(0);
        BlockState blockState = BuildGhostBlockStateResolver.resolve(minecraft, targetPos);
        boolean renderBlockGhost = Config.isPlacementBlockGhostPreviewEnabled() && blocks.size() <= 1;

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
                    BuildGhostFillRenderer.renderFill(blocks, poseStack, fillBuffer, preview.readyConfirm());
                }
            }
        }

        if (shouldRenderWireframe(ClientRtsController.get().getBuildShape(),
                Config.isPlacementWireframePreviewEnabled())) {
            BuildGhostWireframeRenderer.renderWireframes(blocks, poseStack, lineBuffer);
        }
    }

    static boolean shouldRenderWireframe(ClientRtsController.BuildShape shape, boolean placementWireframeEnabled) {
        return shape != ClientRtsController.BuildShape.BLOCK || placementWireframeEnabled;
    }
}
