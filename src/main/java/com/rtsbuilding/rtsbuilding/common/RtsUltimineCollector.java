package com.rtsbuilding.rtsbuilding.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class RtsUltimineCollector {
    public static final int DEFAULT_MAX_RADIUS = 32;

    private static final Direction[] NEIGHBORS = {
            Direction.UP,
            Direction.DOWN,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    private RtsUltimineCollector() {
    }

    public static List<BlockPos> collect(Level level, BlockPos seed, int limit, CandidateFilter filter) {
        return collect(level, seed, limit, DEFAULT_MAX_RADIUS, filter);
    }

    public static List<BlockPos> collect(Level level, BlockPos seed, int limit, int maxRadius, CandidateFilter filter) {
        if (level == null || seed == null || limit <= 0 || filter == null) {
            return List.of();
        }

        BlockPos seedPos = seed.immutable();
        BlockState seedState = level.getBlockState(seedPos);
        if (!filter.test(seedPos, seedState, seedState)) {
            return List.of();
        }

        int clampedLimit = Math.max(1, limit);
        int clampedRadius = Math.max(1, maxRadius);
        List<BlockPos> result = new ArrayList<>(Math.min(clampedLimit, 256));
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        visited.add(seedPos);
        frontier.addLast(seedPos);

        while (!frontier.isEmpty() && result.size() < clampedLimit) {
            BlockPos current = frontier.removeFirst();
            if (manhattanDistance(seedPos, current) > clampedRadius) {
                continue;
            }

            BlockState state = level.getBlockState(current);
            if (!filter.test(current, state, seedState)) {
                continue;
            }

            result.add(current.immutable());
            for (Direction direction : NEIGHBORS) {
                BlockPos next = current.relative(direction).immutable();
                if (manhattanDistance(seedPos, next) <= clampedRadius && visited.add(next)) {
                    frontier.addLast(next);
                }
            }
        }

        result.sort(Comparator
                .comparingLong((BlockPos pos) -> distanceSquared(seedPos, pos))
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return result;
    }

    private static int manhattanDistance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX())
                + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ());
    }

    private static long distanceSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    @FunctionalInterface
    public interface CandidateFilter {
        boolean test(BlockPos pos, BlockState state, BlockState seedState);
    }
}
