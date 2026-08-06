package com.rtsbuilding.rtsbuilding.common.storage;

import net.minecraft.util.math.BlockPos;

/** 批量储存链接框选的共享闭区间边界；服务端必须独立再次验证。 */
public final class RtsBatchStorageSelectionBounds {
    public static final int MAX_WIDTH = 64;
    public static final int MAX_HEIGHT = 64;
    public static final int MAX_DEPTH = 64;
    public static final long MAX_VOLUME = 262144L;

    private RtsBatchStorageSelectionBounds() {
    }

    public static Bounds normalize(BlockPos first, BlockPos second) {
        if (first == null || second == null) return null;
        BlockPos min = new BlockPos(Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        long width = (long) max.getX() - min.getX() + 1L;
        long height = (long) max.getY() - min.getY() + 1L;
        long depth = (long) max.getZ() - min.getZ() + 1L;
        if (width > MAX_WIDTH || height > MAX_HEIGHT || depth > MAX_DEPTH) return null;
        long volume = width * height * depth;
        if (volume <= 0L || volume > MAX_VOLUME) return null;
        return new Bounds(min, max, (int) width, (int) height, (int) depth, volume);
    }

    public static final class Bounds {
        private final BlockPos min;
        private final BlockPos max;
        private final int width;
        private final int height;
        private final int depth;
        private final long volume;

        private Bounds(BlockPos min, BlockPos max, int width, int height, int depth, long volume) {
            this.min = min.toImmutable();
            this.max = max.toImmutable();
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.volume = volume;
        }

        public BlockPos min() { return min; }
        public BlockPos max() { return max; }
        public int width() { return width; }
        public int height() { return height; }
        public int depth() { return depth; }
        public long volume() { return volume; }
        public boolean contains(BlockPos pos) {
            return pos != null && pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
    }
}
