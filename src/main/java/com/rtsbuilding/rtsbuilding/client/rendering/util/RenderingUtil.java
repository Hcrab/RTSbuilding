package com.rtsbuilding.rtsbuilding.client.rendering.util;


import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * Shared rendering math kept deliberately small on Forge 1.20.1. It only owns
 * generic visual helpers, not gameplay selection or server authority.
 */
public final class RenderingUtil {
    private RenderingUtil() {
    }

    public static boolean isWithinBounds(BlockPos pos, double anchorX, double anchorZ, double maxRadius) {
        if (pos == null) {
            return false;
        }
        int minBlockX = Mth.floor(anchorX - maxRadius);
        int maxBlockX = Mth.ceil(anchorX + maxRadius) - 1;
        int minBlockZ = Mth.floor(anchorZ - maxRadius);
        int maxBlockZ = Mth.ceil(anchorZ + maxRadius) - 1;
        return pos.getX() >= minBlockX && pos.getX() <= maxBlockX
                && pos.getZ() >= minBlockZ && pos.getZ() <= maxBlockZ;
    }
}
