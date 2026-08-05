package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.LegacyGhostBlockRenderer;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.render.BufferBuilder;
import net.minecraft.client.Minecraft;

import java.util.List;

/** GTNH 快速建造预览的真实方块模型层；列表和几何兜底仍由上层持有。 */
public final class BuildGhostModelRenderer {
    public static final float GHOST_ALPHA = 0.8F;

    private BuildGhostModelRenderer() {
    }

    public static void renderModels(Minecraft minecraft, List<BlockPos> blocks,
            BufferBuilder callerBuffer, BlockState state) {
        if (minecraft == null || blocks == null || state == null) return;
        for (BlockPos pos : blocks) {
            if (pos != null && !LegacyGhostBlockRenderer.renderAt(
                    minecraft, state, pos, GHOST_ALPHA, 1.0F)) {
                // 模型拒绝渲染时保留实体几何提示，不让预览静默消失。
                PlacementAnimationRenderer.renderFilledBox(callerBuffer,
                        pos.getX() + 0.04D, pos.getY() + 0.04D, pos.getZ() + 0.04D,
                        pos.getX() + 0.96D, pos.getY() + 0.96D, pos.getZ() + 0.96D,
                        0.40F, 0.85F, 0.90F, 0.16F);
            }
        }
    }
}
