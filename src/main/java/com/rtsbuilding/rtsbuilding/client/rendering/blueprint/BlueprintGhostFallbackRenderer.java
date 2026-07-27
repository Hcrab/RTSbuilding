package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** 为缺失、空气或非模型方块写入蓝图占位线框。 */
public final class BlueprintGhostFallbackRenderer {
    private static final double CELL_PADDING = 0.04D;

    private BlueprintGhostFallbackRenderer() {
    }

    /**
     * 只向调用方已经 begin 的私有线缓冲写顶点；本类绝不 finish/draw 缓冲。
     */
    public static void renderFallbacks(List<BlueprintGhostBlock> blocks, BufferBuilder lineBuffer,
            float lineR, float lineG, float lineB) {
        if (blocks == null || lineBuffer == null) {
            return;
        }

        for (BlueprintGhostBlock block : blocks) {
            if (!shouldRenderFallback(block)) {
                continue;
            }

            BlockPos pos = block.pos();
            double minX = pos.getX() + CELL_PADDING;
            double minY = pos.getY() + CELL_PADDING;
            double minZ = pos.getZ() + CELL_PADDING;
            double maxX = pos.getX() + 1.0D - CELL_PADDING;
            double maxY = pos.getY() + 1.0D - CELL_PADDING;
            double maxZ = pos.getZ() + 1.0D - CELL_PADDING;
            float r = block.missing() ? 1.00F : lineR;
            float g = block.missing() ? 0.25F : lineG;
            float b = block.missing() ? 0.25F : lineB;
            RenderGlobal.drawBoundingBox(lineBuffer, minX, minY, minZ, maxX, maxY, maxZ,
                    r, g, b, 0.90F);
        }
    }

    private static boolean shouldRenderFallback(BlueprintGhostBlock block) {
        if (block == null || block.pos() == null) {
            return false;
        }
        if (block.missing()) {
            return true;
        }
        IBlockState state = block.state();
        return state == null || state.getRenderType() != EnumBlockRenderType.MODEL;
    }
}
