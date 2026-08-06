package com.rtsbuilding.rtsbuilding.common.storage;

import net.minecraft.core.BlockPos;

/**
 * 批量储存链接选区的共同硬边界。
 *
 * <p>客户端用它在提交前给出即时反馈，服务端必须再次独立校验，不能信任客户端已经做过检查。
 * 本类只定义几何约束，不扫描世界、区块或储存能力。</p>
 */
public final class RtsBatchStorageSelectionBounds {
    public static final int MAX_WIDTH = 64;
    public static final int MAX_HEIGHT = 64;
    public static final int MAX_DEPTH = 64;
    public static final long MAX_VOLUME = 262_144L;

    private RtsBatchStorageSelectionBounds() {
    }

    /** 返回规范化的闭区间选区；非法或超限时返回 {@code null}。 */
    public static Bounds normalize(BlockPos first, BlockPos second) {
        if (first == null || second == null) {
            return null;
        }
        BlockPos min = new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
        long width = (long) max.getX() - min.getX() + 1L;
        long height = (long) max.getY() - min.getY() + 1L;
        long depth = (long) max.getZ() - min.getZ() + 1L;
        if (width > MAX_WIDTH || height > MAX_HEIGHT || depth > MAX_DEPTH) {
            return null;
        }
        long volume = width * height * depth;
        if (volume <= 0L || volume > MAX_VOLUME) {
            return null;
        }
        return new Bounds(min.immutable(), max.immutable(),
                (int) width, (int) height, (int) depth, volume);
    }

    /** 坐标为闭区间，便于直接表达玩家点选的两个方块。 */
    public record Bounds(BlockPos min, BlockPos max, int width, int height, int depth, long volume) {
        public boolean contains(BlockPos pos) {
            return pos != null
                    && pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
    }
}

