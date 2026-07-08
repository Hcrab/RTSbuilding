package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 蓝图虚影兜底线框。
 *
 * <p>缺失方块、空气和非 MODEL 方块没有可靠模型可渲染，用单格线框标出位置。</p>
 */
public final class BlueprintGhostFallbackRenderer {
    private static final double CELL_PADDING = 0.04D;

    private BlueprintGhostFallbackRenderer() {
    }

    public static void renderFallbacks(List<BlueprintPanel.BlueprintGhostBlock> blocks, PoseStack poseStack,
            VertexConsumer lineBuffer, float lineR, float lineG, float lineB) {
        for (BlueprintPanel.BlueprintGhostBlock block : blocks) {
            if (!shouldRenderFallback(block)) {
                continue;
            }
            BlockPos pos = block.pos();
            double cellMinX = pos.getX() + CELL_PADDING;
            double cellMinY = pos.getY() + CELL_PADDING;
            double cellMinZ = pos.getZ() + CELL_PADDING;
            double cellMaxX = pos.getX() + 1.0D - CELL_PADDING;
            double cellMaxY = pos.getY() + 1.0D - CELL_PADDING;
            double cellMaxZ = pos.getZ() + 1.0D - CELL_PADDING;

            float fallbackR = block.missing() ? 1.00F : lineR;
            float fallbackG = block.missing() ? 0.25F : lineG;
            float fallbackB = block.missing() ? 0.25F : lineB;

            LevelRenderer.renderLineBox(
                    poseStack, lineBuffer,
                    cellMinX, cellMinY, cellMinZ,
                    cellMaxX, cellMaxY, cellMaxZ,
                    fallbackR, fallbackG, fallbackB,
                    0.90F);
        }
    }

    private static boolean shouldRenderFallback(BlueprintPanel.BlueprintGhostBlock block) {
        if (block == null) {
            return false;
        }
        if (block.missing()) {
            return true;
        }
        BlockState state = block.state();
        return state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL;
    }
}
