package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * Tick-by-tick oracle for the confirmed destructive skeleton cache.
 *
 * <p>This intentionally models the current player-facing behaviour of
 * confirmed chain/range-destroy previews: once the work area is confirmed,
 * the remaining target set is reduced as blocks break and the skeleton is
 * rebuilt from those remaining blocks. Newly exposed internal faces are
 * visible in the next rendered tick.</p>
 */
final class SkeletonTickSequenceOracle {

    private SkeletonTickSequenceOracle() {
    }

    static List<BlockPos> ultimineLikeOrder(List<BlockPos> blocks) {
        Bounds bounds = Bounds.of(blocks);
        BlockPos seed = new BlockPos(
                (bounds.minX() + bounds.maxX()) / 2,
                (bounds.minY() + bounds.maxY()) / 2,
                (bounds.minZ() + bounds.maxZ()) / 2);
        return blocks.stream()
                .sorted(Comparator
                        .comparingLong((BlockPos pos) -> distanceSquared(seed, pos))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .toList();
    }

    static List<List<BlockPos>> batches(List<BlockPos> orderedBlocks, int batchSize) {
        int size = Math.max(1, batchSize);
        List<List<BlockPos>> batches = new ArrayList<>((orderedBlocks.size() + size - 1) / size);
        for (int start = 0; start < orderedBlocks.size(); start += size) {
            batches.add(orderedBlocks.subList(start, Math.min(orderedBlocks.size(), start + size)));
        }
        return batches;
    }

    static Model start(List<BlockPos> blocks) {
        return new Model(blocks);
    }

    private static long distanceSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    static final class Model {
        private final Set<Long> remainingKeys;
        private int destroyed;

        private Model(List<BlockPos> blocks) {
            this.remainingKeys = new HashSet<>(blocks.size() * 2);
            for (BlockPos pos : blocks) {
                if (pos != null) {
                    this.remainingKeys.add(pos.asLong());
                }
            }
        }

        int destroyed() {
            return this.destroyed;
        }

        int remainingCount() {
            return this.remainingKeys.size();
        }

        boolean contains(BlockPos pos) {
            return pos != null && this.remainingKeys.contains(pos.asLong());
        }

        void applyTick(List<BlockPos> destroyedThisTick) {
            for (BlockPos pos : destroyedThisTick) {
                if (pos == null || !this.remainingKeys.contains(pos.asLong())) {
                    continue;
                }
                if (this.remainingKeys.remove(pos.asLong())) {
                    this.destroyed++;
                }
            }
        }

        Set<SkeletonEdgeKey> visibleEdges() {
            return SkeletonEdgeOracle.expectedVisibleEdges(remainingBlocks());
        }

        List<BlockPos> remainingBlocksInOriginalOrder(List<BlockPos> originalBlocks) {
            List<BlockPos> remaining = new ArrayList<>(this.remainingKeys.size());
            for (BlockPos pos : originalBlocks) {
                if (contains(pos)) {
                    remaining.add(pos);
                }
            }
            return remaining;
        }

        private List<BlockPos> remainingBlocks() {
            List<BlockPos> blocks = new ArrayList<>(this.remainingKeys.size());
            for (Long key : this.remainingKeys) {
                if (key != null) {
                    blocks.add(BlockPos.of(key));
                }
            }
            return blocks;
        }
    }

    private static void addBlockSurfaceContributions(Map<EdgeKey, EdgeAccumulator> edges, int x, int y, int z,
            Set<Long> blockKeys) {
        if (!blockKeys.contains(BlockPos.asLong(x + 1, y, z))) addFaceEdges(edges, x, y, z, FaceSide.EAST);
        if (!blockKeys.contains(BlockPos.asLong(x - 1, y, z))) addFaceEdges(edges, x, y, z, FaceSide.WEST);
        if (!blockKeys.contains(BlockPos.asLong(x, y + 1, z))) addFaceEdges(edges, x, y, z, FaceSide.UP);
        if (!blockKeys.contains(BlockPos.asLong(x, y - 1, z))) addFaceEdges(edges, x, y, z, FaceSide.DOWN);
        if (!blockKeys.contains(BlockPos.asLong(x, y, z + 1))) addFaceEdges(edges, x, y, z, FaceSide.SOUTH);
        if (!blockKeys.contains(BlockPos.asLong(x, y, z - 1))) addFaceEdges(edges, x, y, z, FaceSide.NORTH);
    }

    private static void removeBlockSurfaceContributions(Map<EdgeKey, EdgeAccumulator> edges, BlockPos pos,
            Set<Long> blockKeys) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (!blockKeys.contains(BlockPos.asLong(x + 1, y, z))) removeFaceEdges(edges, x, y, z, FaceSide.EAST);
        if (!blockKeys.contains(BlockPos.asLong(x - 1, y, z))) removeFaceEdges(edges, x, y, z, FaceSide.WEST);
        if (!blockKeys.contains(BlockPos.asLong(x, y + 1, z))) removeFaceEdges(edges, x, y, z, FaceSide.UP);
        if (!blockKeys.contains(BlockPos.asLong(x, y - 1, z))) removeFaceEdges(edges, x, y, z, FaceSide.DOWN);
        if (!blockKeys.contains(BlockPos.asLong(x, y, z + 1))) removeFaceEdges(edges, x, y, z, FaceSide.SOUTH);
        if (!blockKeys.contains(BlockPos.asLong(x, y, z - 1))) removeFaceEdges(edges, x, y, z, FaceSide.NORTH);
    }

    private static void addFaceEdges(Map<EdgeKey, EdgeAccumulator> edges, int x, int y, int z, FaceSide side) {
        int x0 = x, x1 = x + 1, y0 = y, y1 = y + 1, z0 = z, z1 = z + 1;
        switch (side) {
            case EAST -> { addEdge(edges, x1, y0, z0, x1, y1, z0, side); addEdge(edges, x1, y1, z0, x1, y1, z1, side); addEdge(edges, x1, y1, z1, x1, y0, z1, side); addEdge(edges, x1, y0, z1, x1, y0, z0, side); }
            case WEST -> { addEdge(edges, x0, y0, z0, x0, y0, z1, side); addEdge(edges, x0, y0, z1, x0, y1, z1, side); addEdge(edges, x0, y1, z1, x0, y1, z0, side); addEdge(edges, x0, y1, z0, x0, y0, z0, side); }
            case UP -> { addEdge(edges, x0, y1, z0, x0, y1, z1, side); addEdge(edges, x0, y1, z1, x1, y1, z1, side); addEdge(edges, x1, y1, z1, x1, y1, z0, side); addEdge(edges, x1, y1, z0, x0, y1, z0, side); }
            case DOWN -> { addEdge(edges, x0, y0, z0, x1, y0, z0, side); addEdge(edges, x1, y0, z0, x1, y0, z1, side); addEdge(edges, x1, y0, z1, x0, y0, z1, side); addEdge(edges, x0, y0, z1, x0, y0, z0, side); }
            case SOUTH -> { addEdge(edges, x0, y0, z1, x1, y0, z1, side); addEdge(edges, x1, y0, z1, x1, y1, z1, side); addEdge(edges, x1, y1, z1, x0, y1, z1, side); addEdge(edges, x0, y1, z1, x0, y0, z1, side); }
            case NORTH -> { addEdge(edges, x0, y0, z0, x0, y1, z0, side); addEdge(edges, x0, y1, z0, x1, y1, z0, side); addEdge(edges, x1, y1, z0, x1, y0, z0, side); addEdge(edges, x1, y0, z0, x0, y0, z0, side); }
        }
    }

    private static void removeFaceEdges(Map<EdgeKey, EdgeAccumulator> edges, int x, int y, int z, FaceSide side) {
        int x0 = x, x1 = x + 1, y0 = y, y1 = y + 1, z0 = z, z1 = z + 1;
        switch (side) {
            case EAST -> { removeEdge(edges, x1, y0, z0, x1, y1, z0, side); removeEdge(edges, x1, y1, z0, x1, y1, z1, side); removeEdge(edges, x1, y1, z1, x1, y0, z1, side); removeEdge(edges, x1, y0, z1, x1, y0, z0, side); }
            case WEST -> { removeEdge(edges, x0, y0, z0, x0, y0, z1, side); removeEdge(edges, x0, y0, z1, x0, y1, z1, side); removeEdge(edges, x0, y1, z1, x0, y1, z0, side); removeEdge(edges, x0, y1, z0, x0, y0, z0, side); }
            case UP -> { removeEdge(edges, x0, y1, z0, x0, y1, z1, side); removeEdge(edges, x0, y1, z1, x1, y1, z1, side); removeEdge(edges, x1, y1, z1, x1, y1, z0, side); removeEdge(edges, x1, y1, z0, x0, y1, z0, side); }
            case DOWN -> { removeEdge(edges, x0, y0, z0, x1, y0, z0, side); removeEdge(edges, x1, y0, z0, x1, y0, z1, side); removeEdge(edges, x1, y0, z1, x0, y0, z1, side); removeEdge(edges, x0, y0, z1, x0, y0, z0, side); }
            case SOUTH -> { removeEdge(edges, x0, y0, z1, x1, y0, z1, side); removeEdge(edges, x1, y0, z1, x1, y1, z1, side); removeEdge(edges, x1, y1, z1, x0, y1, z1, side); removeEdge(edges, x0, y1, z1, x0, y0, z1, side); }
            case NORTH -> { removeEdge(edges, x0, y0, z0, x0, y1, z0, side); removeEdge(edges, x0, y1, z0, x1, y1, z0, side); removeEdge(edges, x1, y1, z0, x1, y0, z0, side); removeEdge(edges, x1, y0, z0, x0, y0, z0, side); }
        }
    }

    private static void addEdge(Map<EdgeKey, EdgeAccumulator> edges, int x1, int y1, int z1, int x2, int y2, int z2,
            FaceSide side) {
        edges.computeIfAbsent(EdgeKey.of(x1, y1, z1, x2, y2, z2), ignored -> new EdgeAccumulator()).add(side);
    }

    private static void removeEdge(Map<EdgeKey, EdgeAccumulator> edges, int x1, int y1, int z1, int x2, int y2, int z2,
            FaceSide side) {
        EdgeKey key = EdgeKey.of(x1, y1, z1, x2, y2, z2);
        EdgeAccumulator acc = edges.get(key);
        if (acc == null) {
            return;
        }
        acc.remove(side);
        if (acc.isEmpty()) {
            edges.remove(key);
        }
    }

    private enum FaceSide {
        EAST,
        WEST,
        UP,
        DOWN,
        SOUTH,
        NORTH
    }

    private record EdgeKey(int x1, int y1, int z1, int x2, int y2, int z2) {
        private static EdgeKey of(int x1, int y1, int z1, int x2, int y2, int z2) {
            if (compareVertex(x1, y1, z1, x2, y2, z2) <= 0) {
                return new EdgeKey(x1, y1, z1, x2, y2, z2);
            }
            return new EdgeKey(x2, y2, z2, x1, y1, z1);
        }

        private static int compareVertex(int x1, int y1, int z1, int x2, int y2, int z2) {
            if (x1 != x2) return Integer.compare(x1, x2);
            if (y1 != y2) return Integer.compare(y1, y2);
            return Integer.compare(z1, z2);
        }

        private SkeletonEdgeKey toSkeletonKey() {
            return new SkeletonEdgeKey(this.x1, this.y1, this.z1, this.x2, this.y2, this.z2);
        }
    }

    private static final class EdgeAccumulator {
        private final int[] sideCounts = new int[FaceSide.values().length];
        private int total;

        private void add(FaceSide side) {
            this.sideCounts[side.ordinal()]++;
            this.total++;
        }

        private void remove(FaceSide side) {
            int idx = side.ordinal();
            if (this.sideCounts[idx] <= 0) {
                return;
            }
            this.sideCounts[idx]--;
            this.total--;
        }

        private boolean isEmpty() {
            return this.total <= 0;
        }

        private boolean isVisible() {
            return this.total == 1 || sideTypeCount() > 1;
        }

        private int sideTypeCount() {
            int count = 0;
            for (int sideCount : this.sideCounts) {
                if (sideCount > 0) {
                    count++;
                }
            }
            return count;
        }
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        private static Bounds of(List<BlockPos> blocks) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : blocks) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new Bounds(minX, maxX, minY, maxY, minZ, maxZ);
        }
    }
}
