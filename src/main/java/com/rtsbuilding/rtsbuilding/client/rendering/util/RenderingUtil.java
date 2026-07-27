package com.rtsbuilding.rtsbuilding.client.rendering.util;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 与具体 Forge 渲染事件无关的数学、范围和顶点工具。 */
public final class RenderingUtil {
    private RenderingUtil() {
    }

    public static float lerp(float from, float to, float amount) {
        return from + (to - from) * clamp01(amount);
    }

    public static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public static boolean isEmpty(List<BlockPos> blocks) {
        return blocks == null || blocks.isEmpty();
    }

    public static boolean contains(List<BlockPos> blocks, BlockPos pos) {
        if (blocks == null || pos == null) return false;
        for (BlockPos block : blocks) {
            if (pos.equals(block)) return true;
        }
        return false;
    }

    public static List<BlockPos> filterBlocksWithinBounds(List<BlockPos> blocks,
            double anchorX, double anchorZ, double maxRadius) {
        if (blocks == null || blocks.isEmpty()) return blocks;
        int minBlockX = MathHelper.floor(anchorX - maxRadius);
        int maxBlockX = MathHelper.ceil(anchorX + maxRadius) - 1;
        int minBlockZ = MathHelper.floor(anchorZ - maxRadius);
        int maxBlockZ = MathHelper.ceil(anchorZ + maxRadius) - 1;
        List<BlockPos> result = new ArrayList<BlockPos>(blocks.size());
        for (BlockPos pos : blocks) {
            if (pos != null && pos.getX() >= minBlockX && pos.getX() <= maxBlockX
                    && pos.getZ() >= minBlockZ && pos.getZ() <= maxBlockZ) {
                result.add(pos);
            }
        }
        return result.isEmpty() ? Collections.<BlockPos>emptyList() : result;
    }

    public static boolean isWithinBounds(BlockPos pos,
            double anchorX, double anchorZ, double maxRadius) {
        if (pos == null) return false;
        int minBlockX = MathHelper.floor(anchorX - maxRadius);
        int maxBlockX = MathHelper.ceil(anchorX + maxRadius) - 1;
        int minBlockZ = MathHelper.floor(anchorZ - maxRadius);
        int maxBlockZ = MathHelper.ceil(anchorZ + maxRadius) - 1;
        return pos.getX() >= minBlockX && pos.getX() <= maxBlockX
                && pos.getZ() >= minBlockZ && pos.getZ() <= maxBlockZ;
    }

    public static float getBreathFactor(float speed, float minFactor) {
        double phase = System.currentTimeMillis() / 1000.0D * speed * 2.0D * Math.PI;
        return (float) ((Math.sin(phase) + 1.0D) * 0.5D * (1.0F - minFactor) + minFactor);
    }

    /** 向调用方已经 begin 的 POSITION_COLOR 私有缓冲追加一个四边形。 */
    public static void quad(BufferBuilder buffer,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double x3, double y3, double z3,
            double x4, double y4, double z4,
            float red, float green, float blue, float alpha) {
        vertex(buffer, x1, y1, z1, red, green, blue, alpha);
        vertex(buffer, x2, y2, z2, red, green, blue, alpha);
        vertex(buffer, x3, y3, z3, red, green, blue, alpha);
        vertex(buffer, x4, y4, z4, red, green, blue, alpha);
    }

    private static void vertex(BufferBuilder buffer, double x, double y, double z,
            float red, float green, float blue, float alpha) {
        buffer.pos(x, y, z).color(red, green, blue, alpha).endVertex();
    }

    /** Java 8 值对象，替代主线 record，同时保留同名访问器。 */
    public static final class Bounds {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        public Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public int minX() { return this.minX; }
        public int minY() { return this.minY; }
        public int minZ() { return this.minZ; }
        public int maxX() { return this.maxX; }
        public int maxY() { return this.maxY; }
        public int maxZ() { return this.maxZ; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Bounds)) return false;
            Bounds bounds = (Bounds) other;
            return this.minX == bounds.minX && this.minY == bounds.minY && this.minZ == bounds.minZ
                    && this.maxX == bounds.maxX && this.maxY == bounds.maxY && this.maxZ == bounds.maxZ;
        }

        @Override
        public int hashCode() {
            int result = this.minX;
            result = 31 * result + this.minY;
            result = 31 * result + this.minZ;
            result = 31 * result + this.maxX;
            result = 31 * result + this.maxY;
            result = 31 * result + this.maxZ;
            return result;
        }

        @Override
        public String toString() {
            return "Bounds[minX=" + this.minX + ", minY=" + this.minY + ", minZ=" + this.minZ
                    + ", maxX=" + this.maxX + ", maxY=" + this.maxY + ", maxZ=" + this.maxZ + ']';
        }

        public static Bounds from(List<BlockPos> first, List<BlockPos> second) {
            MutableBounds bounds = new MutableBounds();
            bounds.include(first);
            bounds.include(second);
            return bounds.toBounds();
        }
    }

    public static final class MutableBounds {
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;
        private boolean hasAny;

        public void include(List<BlockPos> blocks) {
            if (blocks == null || blocks.isEmpty()) return;
            for (BlockPos pos : blocks) {
                if (pos == null) continue;
                this.minX = Math.min(this.minX, pos.getX());
                this.minY = Math.min(this.minY, pos.getY());
                this.minZ = Math.min(this.minZ, pos.getZ());
                this.maxX = Math.max(this.maxX, pos.getX());
                this.maxY = Math.max(this.maxY, pos.getY());
                this.maxZ = Math.max(this.maxZ, pos.getZ());
                this.hasAny = true;
            }
        }

        public Bounds toBounds() {
            return this.hasAny
                    ? new Bounds(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ)
                    : null;
        }
    }
}
