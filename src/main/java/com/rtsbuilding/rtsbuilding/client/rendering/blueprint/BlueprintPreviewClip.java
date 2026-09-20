package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * 以蓝图局部坐标表示现有 RTS 边界；完全在界内移动时裁剪键不变，网格可直接平移。
 * 本类不增加蓝图距离或规模限制。
 */
record BlueprintPreviewClip(int minX, int maxX, int minZ, int maxZ) {
    static BlueprintPreviewClip of(AABB content, BlockPos anchor,
            int worldMinX, int worldMaxX, int worldMinZ, int worldMaxZ) {
        return new BlueprintPreviewClip(
                Math.max((int) content.minX, worldMinX - anchor.getX()),
                Math.min((int) content.maxX - 1, worldMaxX - anchor.getX()),
                Math.max((int) content.minZ, worldMinZ - anchor.getZ()),
                Math.min((int) content.maxZ - 1, worldMaxZ - anchor.getZ()));
    }

    boolean contains(BlockPos local) {
        return local.getX() >= minX && local.getX() <= maxX
                && local.getZ() >= minZ && local.getZ() <= maxZ;
    }

    boolean isEmpty() { return minX > maxX || minZ > maxZ; }

    AABB bounds(AABB content) {
        return isEmpty() ? null : new AABB(minX, content.minY, minZ,
                maxX + 1, content.maxY, maxZ + 1);
    }
}
