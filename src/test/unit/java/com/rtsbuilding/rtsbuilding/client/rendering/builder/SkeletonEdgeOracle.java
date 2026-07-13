package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * Independent surface-edge oracle for merged skeleton tests.
 *
 * <p>The oracle starts from the player-facing rule: only exposed block faces
 * should contribute to the destructive work-area skeleton, flat internal grid
 * lines should disappear, and true outside corners should stay visible. It is
 * deliberately separate from {@link MergedSkeletonRenderer}'s private edge
 * accumulators so tests can catch accidental changes in that renderer.</p>
 */
final class SkeletonEdgeOracle {

    private SkeletonEdgeOracle() {
    }

    static Set<SkeletonEdgeKey> expectedVisibleEdges(Collection<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return Set.of();
        }
        Set<Long> blockKeys = new HashSet<>(blocks.size() * 2);
        for (BlockPos pos : blocks) {
            if (pos != null) {
                blockKeys.add(pos.asLong());
            }
        }

        Map<SkeletonEdgeKey, EnumMap<FaceSide, Integer>> edges = new HashMap<>(Math.max(64, blockKeys.size() * 8));
        for (BlockPos pos : blocks) {
            if (pos == null) {
                continue;
            }
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if (!blockKeys.contains(BlockPos.asLong(x + 1, y, z))) addFace(edges, x, y, z, FaceSide.EAST);
            if (!blockKeys.contains(BlockPos.asLong(x - 1, y, z))) addFace(edges, x, y, z, FaceSide.WEST);
            if (!blockKeys.contains(BlockPos.asLong(x, y + 1, z))) addFace(edges, x, y, z, FaceSide.UP);
            if (!blockKeys.contains(BlockPos.asLong(x, y - 1, z))) addFace(edges, x, y, z, FaceSide.DOWN);
            if (!blockKeys.contains(BlockPos.asLong(x, y, z + 1))) addFace(edges, x, y, z, FaceSide.SOUTH);
            if (!blockKeys.contains(BlockPos.asLong(x, y, z - 1))) addFace(edges, x, y, z, FaceSide.NORTH);
        }

        Set<SkeletonEdgeKey> visible = new HashSet<>();
        for (Map.Entry<SkeletonEdgeKey, EnumMap<FaceSide, Integer>> entry : edges.entrySet()) {
            if (isVisible(entry.getValue())) {
                visible.add(entry.getKey());
            }
        }
        return visible;
    }

    private static void addFace(Map<SkeletonEdgeKey, EnumMap<FaceSide, Integer>> edges,
            int x, int y, int z, FaceSide side) {
        int x0 = x;
        int x1 = x + 1;
        int y0 = y;
        int y1 = y + 1;
        int z0 = z;
        int z1 = z + 1;
        switch (side) {
            case EAST -> {
                addEdge(edges, x1, y0, z0, x1, y1, z0, side);
                addEdge(edges, x1, y1, z0, x1, y1, z1, side);
                addEdge(edges, x1, y1, z1, x1, y0, z1, side);
                addEdge(edges, x1, y0, z1, x1, y0, z0, side);
            }
            case WEST -> {
                addEdge(edges, x0, y0, z0, x0, y0, z1, side);
                addEdge(edges, x0, y0, z1, x0, y1, z1, side);
                addEdge(edges, x0, y1, z1, x0, y1, z0, side);
                addEdge(edges, x0, y1, z0, x0, y0, z0, side);
            }
            case UP -> {
                addEdge(edges, x0, y1, z0, x0, y1, z1, side);
                addEdge(edges, x0, y1, z1, x1, y1, z1, side);
                addEdge(edges, x1, y1, z1, x1, y1, z0, side);
                addEdge(edges, x1, y1, z0, x0, y1, z0, side);
            }
            case DOWN -> {
                addEdge(edges, x0, y0, z0, x1, y0, z0, side);
                addEdge(edges, x1, y0, z0, x1, y0, z1, side);
                addEdge(edges, x1, y0, z1, x0, y0, z1, side);
                addEdge(edges, x0, y0, z1, x0, y0, z0, side);
            }
            case SOUTH -> {
                addEdge(edges, x0, y0, z1, x1, y0, z1, side);
                addEdge(edges, x1, y0, z1, x1, y1, z1, side);
                addEdge(edges, x1, y1, z1, x0, y1, z1, side);
                addEdge(edges, x0, y1, z1, x0, y0, z1, side);
            }
            case NORTH -> {
                addEdge(edges, x0, y0, z0, x0, y1, z0, side);
                addEdge(edges, x0, y1, z0, x1, y1, z0, side);
                addEdge(edges, x1, y1, z0, x1, y0, z0, side);
                addEdge(edges, x1, y0, z0, x0, y0, z0, side);
            }
        }
    }

    private static void addEdge(Map<SkeletonEdgeKey, EnumMap<FaceSide, Integer>> edges,
            int x1, int y1, int z1, int x2, int y2, int z2, FaceSide side) {
        SkeletonEdgeKey key = SkeletonEdgeKey.of(x1, y1, z1, x2, y2, z2);
        edges.computeIfAbsent(key, ignored -> new EnumMap<>(FaceSide.class))
                .merge(side, 1, Integer::sum);
    }

    private static boolean isVisible(EnumMap<FaceSide, Integer> counts) {
        int total = 0;
        int sideTypes = 0;
        for (int count : counts.values()) {
            if (count > 0) {
                total += count;
                sideTypes++;
            }
        }
        return total == 1 || sideTypes > 1;
    }

    private enum FaceSide {
        EAST,
        WEST,
        UP,
        DOWN,
        SOUTH,
        NORTH
    }
}
