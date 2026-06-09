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
 * 蓝图虚影回退框线渲染器。
 * <p>
 * 负责渲染那些无法以方块模型方式呈现的虚影方块占位框线，
 * 包括：
 * <ul>
 *   <li>缺失方块（missing block）</li>
 *   <li>空气方块</li>
 *   <li>无 {@link RenderShape#MODEL} 渲染形状的方块</li>
 * </ul>
 * <p>
 * 缺失方块以红色框线标记，其他情况使用传入的颜色。
 */
public final class BlueprintGhostFallbackRenderer {

    /** 框线与方块边缘的间距 */
    private static final double CELL_PADDING = 0.04D;

    private BlueprintGhostFallbackRenderer() {
    }

    /**
     * 渲染所有需要回退框线的虚影方块。
     * <p>
     * 此方法会跳过已经由 {@link BlueprintGhostBlockModelRenderer} 渲染的模型方块，
     * 只处理缺失或无模型的方块。
     *
     * @param blocks    过滤后的蓝图方块列表
     * @param poseStack 姿势栈
     * @param lineBuffer 线条缓冲区
     * @param lineR     正常方块的框线红色分量
     * @param lineG     正常方块的框线绿色分量
     * @param lineB     正常方块的框线蓝色分量
     */
    public static void renderFallbacks(
            List<BlueprintPanel.BlueprintGhostBlock> blocks,
            PoseStack poseStack,
            VertexConsumer lineBuffer,
            float lineR, float lineG, float lineB) {

        for (BlueprintPanel.BlueprintGhostBlock block : blocks) {
            if (shouldRenderFallback(block)) {
                BlockPos pos = block.pos();
                double cellMinX = pos.getX() + CELL_PADDING;
                double cellMinY = pos.getY() + CELL_PADDING;
                double cellMinZ = pos.getZ() + CELL_PADDING;
                double cellMaxX = pos.getX() + 1.0D - CELL_PADDING;
                double cellMaxY = pos.getY() + 1.0D - CELL_PADDING;
                double cellMaxZ = pos.getZ() + 1.0D - CELL_PADDING;

                // 缺失方块使用红色，其他使用状态色
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
    }

    /**
     * 判断给定方块是否需要回退框线渲染。
     */
    private static boolean shouldRenderFallback(BlueprintPanel.BlueprintGhostBlock block) {
        if (block == null) return false;
        if (block.missing()) return true;
        BlockState state = block.state();
        return state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL;
    }
}
