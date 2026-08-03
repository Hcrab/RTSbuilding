package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;

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
        Matrix4f matrix = poseStack.last().pose();
        consumer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) x3, (float) y3, (float) z3).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) x4, (float) y4, (float) z4).color(r, g, b, a).endVertex();
    }

    /**
     * 向任意 POSITION_COLOR 顶点消费者写入完整实心盒。
     * 1.19.2 的原版辅助方法只接受 BufferBuilder 且忽略 PoseStack，因此由这里显式写六个面。
     */
    public static void filledBox(PoseStack poseStack, VertexConsumer consumer,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            float r, float g, float b, float a) {
        quad(consumer, poseStack, minX, minY, minZ, maxX, minY, minZ,
                maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        quad(consumer, poseStack, minX, maxY, minZ, minX, maxY, maxZ,
                maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        quad(consumer, poseStack, minX, minY, minZ, minX, minY, maxZ,
                minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        quad(consumer, poseStack, maxX, minY, minZ, maxX, maxY, minZ,
                maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        quad(consumer, poseStack, minX, minY, minZ, minX, maxY, minZ,
                maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        quad(consumer, poseStack, minX, minY, maxZ, maxX, minY, maxZ,
                maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    /**
     * 向 Forge 1.20.1 的 POSITION_COLOR_NORMAL 线缓冲写入一条完整线段。
     * 旧版 {@link VertexConsumer} 不会自动补齐法线；遗漏 normal 会在 endVertex 时直接崩溃。
     */
    public static void line(VertexConsumer consumer, PoseStack poseStack,
            Vec3 first, Vec3 second, float r, float g, float b, float a) {
        Vec3 direction = second.subtract(first).normalize();
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        consumer.vertex(pose, (float) first.x, (float) first.y, (float) first.z)
                .color(r, g, b, a)
                .normal(normal, (float) direction.x, (float) direction.y, (float) direction.z)
                .endVertex();
        consumer.vertex(pose, (float) second.x, (float) second.y, (float) second.z)
                .color(r, g, b, a)
                .normal(normal, (float) direction.x, (float) direction.y, (float) direction.z)
                .endVertex();
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
