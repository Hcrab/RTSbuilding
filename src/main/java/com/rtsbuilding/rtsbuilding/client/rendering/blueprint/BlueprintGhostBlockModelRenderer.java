package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.rtsbuilding.rtsbuilding.client.rendering.util.LegacyGhostBlockRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostBlock;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.client.Minecraft;

import java.util.List;

/** 1.7.10 蓝图方块模型层；动态 TileEntity 渲染明确留给后续 GTNH 专项兼容。 */
public final class BlueprintGhostBlockModelRenderer {
    public static final float GHOST_ALPHA = 0.30F;

    private BlueprintGhostBlockModelRenderer() {
    }

    public static boolean renderModels(Minecraft minecraft, List<BlueprintGhostBlock> blocks,
            double cameraX, double cameraY, double cameraZ,
            int[] outMinX, int[] outMinY, int[] outMinZ,
            int[] outMaxX, int[] outMaxY, int[] outMaxZ) {
        if (minecraft == null || blocks == null || blocks.isEmpty()) return false;
        boolean rendered = false;
        for (BlueprintGhostBlock block : blocks) {
            if (block == null || block.pos() == null) continue;
            BlockPos pos = block.pos();
            updateBounds(pos, outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ);
            if (!block.missing() && block.state() != null) {
                rendered |= LegacyGhostBlockRenderer.renderAt(
                        minecraft, block.state(), pos, GHOST_ALPHA, 1.0F);
            }
        }
        return rendered;
    }

    public static boolean renderModels(Minecraft minecraft, List<BlueprintGhostBlock> blocks,
            double cameraX, double cameraY, double cameraZ) {
        return renderModels(minecraft, blocks, cameraX, cameraY, cameraZ,
                new int[]{Integer.MAX_VALUE}, new int[]{Integer.MAX_VALUE},
                new int[]{Integer.MAX_VALUE}, new int[]{Integer.MIN_VALUE},
                new int[]{Integer.MIN_VALUE}, new int[]{Integer.MIN_VALUE});
    }

    private static void updateBounds(BlockPos pos,
            int[] minX, int[] minY, int[] minZ, int[] maxX, int[] maxY, int[] maxZ) {
        minX[0] = Math.min(minX[0], pos.getX());
        minY[0] = Math.min(minY[0], pos.getY());
        minZ[0] = Math.min(minZ[0], pos.getZ());
        maxX[0] = Math.max(maxX[0], pos.getX() + 1);
        maxY[0] = Math.max(maxY[0], pos.getY() + 1);
        maxZ[0] = Math.max(maxZ[0], pos.getZ() + 1);
    }
}
