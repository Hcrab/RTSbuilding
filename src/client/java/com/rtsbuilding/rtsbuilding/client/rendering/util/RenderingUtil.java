package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared rendering utility methods that eliminate duplicate code across multiple renderers.
 */
public final class RenderingUtil {
    private RenderingUtil() {}

    // ===== Math =====

    public static float lerp(float from, float to, float amount) {
        return from + (to - from) * clamp01(amount);
    }

    public static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    // ===== BlockPos list utilities =====

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

    /**
     * Filters a block position list, keeping only blocks within the RTS boundary.
     * <p>
     * The boundary is a square region centred on the anchor point with a half-length
     * of {@code maxRadius}. A block is considered within bounds if it overlaps the region
     * (i.e. is not entirely outside the boundary).
     *
     * @param blocks    the list of block positions to filter
     * @param anchorX   boundary centre X coordinate
     * @param anchorZ   boundary centre Z coordinate
     * @param maxRadius boundary half-length
     * @return a new list containing only blocks within the boundary; empty list if all blocks are outside
     */
    public static List<BlockPos> filterBlocksWithinBounds(List<BlockPos> blocks, double anchorX, double anchorZ, double maxRadius) {
        if (blocks == null || blocks.isEmpty()) return blocks;
        int minBlockX = Mth.floor(anchorX - maxRadius);
        int maxBlockX = Mth.ceil(anchorX + maxRadius) - 1;
        int minBlockZ = Mth.floor(anchorZ - maxRadius);
        int maxBlockZ = Mth.ceil(anchorZ + maxRadius) - 1;
        List<BlockPos> result = new ArrayList<>(blocks.size());
        for (BlockPos pos : blocks) {
            if (pos != null && pos.getX() >= minBlockX && pos.getX() <= maxBlockX
                    && pos.getZ() >= minBlockZ && pos.getZ() <= maxBlockZ) {
                result.add(pos);
            }
        }
        return result.isEmpty() ? List.of() : result;
    }

    /**
     * Checks whether a single block position is within the RTS boundary.
     *
     * @param pos       block position to test
     * @param anchorX   boundary centre X coordinate
     * @param anchorZ   boundary centre Z coordinate
     * @param maxRadius boundary half-length
     * @return true if the position is within the boundary
     */
    public static boolean isWithinBounds(BlockPos pos, double anchorX, double anchorZ, double maxRadius) {
        if (pos == null) return false;
        int minBlockX = Mth.floor(anchorX - maxRadius);
        int maxBlockX = Mth.ceil(anchorX + maxRadius) - 1;
        int minBlockZ = Mth.floor(anchorZ - maxRadius);
        int maxBlockZ = Mth.ceil(anchorZ + maxRadius) - 1;
        return pos.getX() >= minBlockX && pos.getX() <= maxBlockX
                && pos.getZ() >= minBlockZ && pos.getZ() <= maxBlockZ;
    }

    // ===== Breath animation =====

    public static float getBreathFactor(float speed, float minFactor) {
        double timeSeconds = System.currentTimeMillis() / 1000.0D;
        double phase = timeSeconds * speed * 2.0D * Math.PI;
        double sin = Math.sin(phase);
        return (float) ((sin + 1.0D) * 0.5D * (1.0F - minFactor) + minFactor);
    }

    // ===== Quad rendering =====

    public static void quad(VertexConsumer consumer, PoseStack poseStack,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double x3, double y3, double z3,
            double x4, double y4, double z4,
            float r, float g, float b, float a) {
        consumer.addVertex(poseStack.last(), (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        consumer.addVertex(poseStack.last(), (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
        consumer.addVertex(poseStack.last(), (float) x3, (float) y3, (float) z3).setColor(r, g, b, a);
        consumer.addVertex(poseStack.last(), (float) x4, (float) y4, (float) z4).setColor(r, g, b, a);
    }

    // ===== Bounds (used by ShapeGhostRenderer & DestructiveGhostRenderer) =====

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
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
            return this.hasAny ? new Bounds(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ) : null;
        }
    }
}
