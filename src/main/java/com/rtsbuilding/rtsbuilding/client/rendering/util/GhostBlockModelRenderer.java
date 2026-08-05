package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.client.Minecraft;

/** 保留跨版本调用面；1.7.10 的真实渲染细节由 {@link LegacyGhostBlockRenderer} 拥有。 */
public final class GhostBlockModelRenderer {
    private GhostBlockModelRenderer() {
    }

    public static boolean renderAt(Minecraft minecraft, BlockState state, BlockPos pos, float alpha) {
        return renderAt(minecraft, state, pos, alpha, 1.0F);
    }

    public static boolean renderAt(Minecraft minecraft, BlockState state, BlockPos pos,
            float alpha, float scale) {
        return LegacyGhostBlockRenderer.renderAt(minecraft, state, pos, alpha, scale);
    }
}
