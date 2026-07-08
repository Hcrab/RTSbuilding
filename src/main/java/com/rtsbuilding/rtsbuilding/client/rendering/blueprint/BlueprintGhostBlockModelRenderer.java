package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostBlockModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 渲染蓝图预览中的真实方块模型。
 *
 * <p>只处理可用 MODEL 渲染形态的方块；缺失方块、空气和特殊渲染形态交给兜底线框。</p>
 */
public final class BlueprintGhostBlockModelRenderer {
    public static final float GHOST_ALPHA = 0.30F;

    private BlueprintGhostBlockModelRenderer() {
    }

    public static boolean renderModels(
            Minecraft minecraft,
            List<BlueprintPanel.BlueprintGhostBlock> blocks,
            PoseStack poseStack,
            int[] outMinX, int[] outMinY, int[] outMinZ,
            int[] outMaxX, int[] outMaxY, int[] outMaxZ) {

        boolean renderedBlockModels = false;
        MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();

        for (BlueprintPanel.BlueprintGhostBlock block : blocks) {
            BlockPos pos = block.pos();
            outMinX[0] = Math.min(outMinX[0], pos.getX());
            outMinY[0] = Math.min(outMinY[0], pos.getY());
            outMinZ[0] = Math.min(outMinZ[0], pos.getZ());
            outMaxX[0] = Math.max(outMaxX[0], pos.getX() + 1);
            outMaxY[0] = Math.max(outMaxY[0], pos.getY() + 1);
            outMaxZ[0] = Math.max(outMaxZ[0], pos.getZ() + 1);

            BlockState state = block.state();
            if (!block.missing()
                    && state != null
                    && !state.isAir()
                    && state.getRenderShape() == RenderShape.MODEL) {
                renderedBlockModels |= GhostBlockModelRenderer.renderAt(minecraft, poseStack, blockBuffer,
                        state, pos, GHOST_ALPHA);
            }
        }

        if (renderedBlockModels) {
            blockBuffer.endBatch();
        }

        return renderedBlockModels;
    }

    public static boolean renderModels(Minecraft minecraft, List<BlueprintPanel.BlueprintGhostBlock> blocks,
            PoseStack poseStack) {
        int[] outMinX = {Integer.MAX_VALUE};
        int[] outMinY = {Integer.MAX_VALUE};
        int[] outMinZ = {Integer.MAX_VALUE};
        int[] outMaxX = {Integer.MIN_VALUE};
        int[] outMaxY = {Integer.MIN_VALUE};
        int[] outMaxZ = {Integer.MIN_VALUE};
        return renderModels(minecraft, blocks, poseStack, outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ);
    }
}
