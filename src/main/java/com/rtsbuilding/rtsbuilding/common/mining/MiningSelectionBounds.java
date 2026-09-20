package com.rtsbuilding.rtsbuilding.common.mining;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 区域挖掘接纳前的纯包围盒计算，不读世界、不过滤空气，也不悄悄裁掉请求的一部分。
 * 客户端可调整预览，服务端则用完整请求的体积决定接纳；权限与工具检查仍属于执行器。
 */
public record MiningSelectionBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    public static MiningSelectionBounds between(int x1, int x2, int y1, int y2, int z1, int z2) {
        return new MiningSelectionBounds(Math.min(x1, x2), Math.max(x1, x2),
                Math.min(y1, y2), Math.max(y1, y2), Math.min(z1, z2), Math.max(z1, z2));
    }

    public boolean fits(int maxVolume) {
        return fits(new SelectionVolumeLimit(MiningLimits.clampVolume(maxVolume),
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    public boolean fits(SelectionVolumeLimit limit) {
        if (limit == null) {
            return false;
        }
        return limit.fits((long) maxX - minX + 1L,
                (long) maxY - minY + 1L, (long) maxZ - minZ + 1L);
    }

    /** 原始列表受实现容量约束，包围盒受本次有效配置约束；空或损坏请求不进入世界扫描。 */
    public static boolean accepts(List<BlockPos> positions, int maxVolume) {
        return accepts(positions, new SelectionVolumeLimit(MiningLimits.clampVolume(maxVolume),
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    public static boolean accepts(List<BlockPos> positions, SelectionVolumeLimit limit) {
        if (positions == null || positions.isEmpty() || positions.size() > MiningLimits.MAX_VOLUME || limit == null) return false;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            if (pos == null) return false;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new MiningSelectionBounds(minX, maxX, minY, maxY, minZ, maxZ).fits(limit);
    }
}
