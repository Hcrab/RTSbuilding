package com.rtsbuilding.rtsbuilding.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Ultimine block collector — flood-fills from a seed block to collect matching blocks.
 * <p>
 * Uses BFS (breadth-first search) to spread in 3D space,
 * limits the maximum spread radius by Chebyshev distance,
 * and supports arbitrary block state lookup and filtering via generic interfaces.
 */
public final class RtsUltimineCollector {

    /** Default maximum spread radius (Chebyshev distance) */
    public static final int DEFAULT_MAX_RADIUS = 32;

    /** Neighbor offset array for 26 directions (3x3x3 minus center) */
    private static final int[][] NEIGHBOR_OFFSETS = buildNeighborOffsets();

    private RtsUltimineCollector() {
    }

    /**
     * Collect connected matching blocks starting from a seed block (using default max radius).
     *
     * @param level  the world
     * @param seed   seed block position
     * @param limit  maximum collection count
     * @param filter candidate block filter
     * @return list of collected block positions (sorted by distance)
     */
    public static List<BlockPos> collect(Level level, BlockPos seed, int limit, CandidateFilter filter) {
        return collect(level, seed, limit, DEFAULT_MAX_RADIUS, filter);
    }

    /**
     * Collect connected matching blocks starting from a seed block (with specified max radius).
     *
     * @param level     the world
     * @param seed      seed block position
     * @param limit     maximum collection count
     * @param maxRadius maximum spread radius (Chebyshev distance)
     * @param filter    candidate block filter
     * @return list of collected block positions
     */
    public static List<BlockPos> collect(Level level, BlockPos seed, int limit, int maxRadius, CandidateFilter filter) {
        if (level == null || seed == null || limit <= 0 || filter == null) {
            return List.of();
        }
        return collect(seed, limit, maxRadius, level::getBlockState,
                (candidatePos, state, seedState) -> filter.test(candidatePos, state, seedState));
    }

    /**
     * Generic block collection method with custom state lookup and filtering logic.
     * <p>
     * Uses BFS to spread outward from the seed block, with {@code StateLookup} and
     * {@code GenericCandidateFilter} generic interfaces supporting any block state type.
     *
     * @param <S>         block state type
     * @param seed        seed block position
     * @param limit       maximum collection count
     * @param maxRadius   maximum spread radius
     * @param stateLookup block state lookup function
     * @param filter      candidate block filter function
     * @return list of collected block positions (sorted by distance)
     */
    public static <S> List<BlockPos> collect(BlockPos seed, int limit, int maxRadius, StateLookup<S> stateLookup,
            GenericCandidateFilter<S> filter) {
        if (seed == null || limit <= 0 || stateLookup == null || filter == null) {
            return List.of();
        }
        BlockPos seedPos = seed.immutable();
        S seedState = stateLookup.get(seedPos);

        int clampedLimit = Math.max(1, limit);
        int clampedRadius = Math.max(1, maxRadius);
        List<BlockPos> result = new ArrayList<>(Math.min(clampedLimit, 256));
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        visited.add(seedPos);
        frontier.addLast(seedPos);

        // BFS spread search
        while (!frontier.isEmpty() && result.size() < clampedLimit) {
            BlockPos current = frontier.removeFirst();
            // skip positions beyond radius
            if (chebyshevDistance(seedPos, current) > clampedRadius) {
                continue;
            }

            S state = stateLookup.get(current);
            if (!filter.test(current, state, seedState)) {
                continue;
            }

            result.add(current.immutable());
            // traverse 26 neighbor directions
            for (int[] offset : NEIGHBOR_OFFSETS) {
                BlockPos next = current.offset(offset[0], offset[1], offset[2]).immutable();
                if (chebyshevDistance(seedPos, next) <= clampedRadius && visited.add(next)) {
                    frontier.addLast(next);
                }
            }
        }

        // sort by distance: first by squared Euclidean distance, then by Y/X/Z coordinates
        result.sort(Comparator
                .comparingLong((BlockPos pos) -> distanceSquared(seedPos, pos))
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return result;
    }

    /** Build the neighbor offset array for 26 directions */
    private static int[][] buildNeighborOffsets() {
        int[][] offsets = new int[26][3];
        int index = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    offsets[index++] = new int[] { dx, dy, dz };
                }
            }
        }
        return offsets;
    }

    /** Calculate the Chebyshev distance between two positions */
    private static int chebyshevDistance(BlockPos a, BlockPos b) {
        return Math.max(
                Math.abs(a.getX() - b.getX()),
                Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
    }

    /** Calculate the squared Euclidean distance between two positions */
    private static long distanceSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /** Candidate block filter — determines whether a block matches the criteria */
    @FunctionalInterface
    public interface CandidateFilter {
        boolean test(BlockPos pos, BlockState state, BlockState seedState);
    }

    /** Block state lookup interface — gets the block state at a given position (generic-friendly) */
    @FunctionalInterface
    public interface StateLookup<S> {
        S get(BlockPos pos);
    }

    /** Generic candidate block filter — supports any block state type */
    @FunctionalInterface
    public interface GenericCandidateFilter<S> {
        boolean test(BlockPos pos, S state, S seedState);
    }
}
