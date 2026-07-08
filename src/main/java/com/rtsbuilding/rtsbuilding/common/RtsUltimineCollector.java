package com.rtsbuilding.rtsbuilding.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class RtsUltimineCollector {
    public static final int DEFAULT_MAX_RADIUS = 32;

    private static final int[][] NEIGHBOR_OFFSETS = buildNeighborOffsets();

    private RtsUltimineCollector() {
    }

    public static List<BlockPos> collect(Level level, BlockPos seed, int limit, CandidateFilter filter) {
        return collect(level, seed, limit, DEFAULT_MAX_RADIUS, filter);
    }

    public static List<BlockPos> collect(Level level, BlockPos seed, int limit, int maxRadius, CandidateFilter filter) {
        if (level == null || seed == null || limit <= 0 || filter == null) {
            return List.of();
        }
        return collect(seed, limit, maxRadius, level::getBlockState,
                (candidatePos, state, seedState) -> filter.test(candidatePos, state, seedState));
    }

    /**
     * 使用调用方提供的状态查询函数收集连锁破坏目标。
     *
     * <p>这个入口让服务端真实世界和纯内存测试场景共用同一套 BFS 顺序、
     * 半径限制和数量上限，避免渲染/队列测试再复制一份近似算法。</p>
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

        while (!frontier.isEmpty() && result.size() < clampedLimit) {
            BlockPos current = frontier.removeFirst();
            if (chebyshevDistance(seedPos, current) > clampedRadius) {
                continue;
            }

            S state = stateLookup.get(current);
            if (!filter.test(current, state, seedState)) {
                continue;
            }

            result.add(current.immutable());
            for (int[] offset : NEIGHBOR_OFFSETS) {
                BlockPos next = current.offset(offset[0], offset[1], offset[2]).immutable();
                if (chebyshevDistance(seedPos, next) <= clampedRadius && visited.add(next)) {
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

    private static int chebyshevDistance(BlockPos a, BlockPos b) {
        return Math.max(
                Math.abs(a.getX() - b.getX()),
                Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
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

    @FunctionalInterface
    public interface StateLookup<S> {
        S get(BlockPos pos);
    }

    @FunctionalInterface
    public interface GenericCandidateFilter<S> {
        boolean test(BlockPos pos, S state, S seedState);
    }
}
