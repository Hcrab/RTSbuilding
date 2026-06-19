package com.rtsbuilding.rtsbuilding.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 连锁挖掘（Ultimine）方块收集器 —— 从种子方块开始 Flood-Fill 扩散收集同类方块。
 * <p>
 * 使用 BFS（广度优先搜索）算法在 3D 空间中扩散，
 * 通过切比雪夫距离限制最大扩散半径，
 * 并通过泛型接口支持任意类型的方块状态查找和过滤。
 */
public final class RtsUltimineCollector {

    /** 默认最大扩散半径（切比雪夫距离） */
    public static final int DEFAULT_MAX_RADIUS = 32;

    /** 26 个方向的邻居偏移数组（3x3x3 去掉中心） */
    private static final int[][] NEIGHBOR_OFFSETS = buildNeighborOffsets();

    private RtsUltimineCollector() {
    }

    /**
     * 从种子方块开始收集相连的同类方块（使用默认最大半径）。
     *
     * @param level  世界
     * @param seed   种子方块位置
     * @param limit  最大收集数量
     * @param filter 候选方块过滤器
     * @return 收集到的方块位置列表（按距离排序）
     */
    public static List<BlockPos> collect(Level level, BlockPos seed, int limit, CandidateFilter filter) {
        return collect(level, seed, limit, DEFAULT_MAX_RADIUS, filter);
    }

    /**
     * 从种子方块开始收集相连的同类方块（指定最大半径）。
     *
     * @param level     世界
     * @param seed      种子方块位置
     * @param limit     最大收集数量
     * @param maxRadius 最大扩散半径（切比雪夫距离）
     * @param filter    候选方块过滤器
     * @return 收集到的方块位置列表
     */
    public static List<BlockPos> collect(Level level, BlockPos seed, int limit, int maxRadius, CandidateFilter filter) {
        if (level == null || seed == null || limit <= 0 || filter == null) {
            return List.of();
        }
        return collect(seed, limit, maxRadius, level::getBlockState,
                (candidatePos, state, seedState) -> filter.test(candidatePos, state, seedState));
    }

    /**
     * 通用的方块收集方法，支持自定义状态查找和过滤逻辑。
     * <p>
     * 使用 BFS 从种子方块向外扩散，配合 {@code StateLookup} 和 {@code GenericCandidateFilter}
     * 两个泛型接口，支持任意类型的方块状态表示。
     *
     * @param <S>         方块状态类型
     * @param seed        种子方块位置
     * @param limit       最大收集数量
     * @param maxRadius   最大扩散半径
     * @param stateLookup 方块状态查找函数
     * @param filter      候选方块过滤函数
     * @return 收集到的方块位置列表（按距离排序）
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

        // BFS 扩散搜索
        while (!frontier.isEmpty() && result.size() < clampedLimit) {
            BlockPos current = frontier.removeFirst();
            // 跳过超出半径的位置
            if (chebyshevDistance(seedPos, current) > clampedRadius) {
                continue;
            }

            S state = stateLookup.get(current);
            if (!filter.test(current, state, seedState)) {
                continue;
            }

            result.add(current.immutable());
            // 遍历 26 个邻居方向
            for (int[] offset : NEIGHBOR_OFFSETS) {
                BlockPos next = current.offset(offset[0], offset[1], offset[2]).immutable();
                if (chebyshevDistance(seedPos, next) <= clampedRadius && visited.add(next)) {
                    frontier.addLast(next);
                }
            }
        }

        // 按距离排序：先按欧几里得距离平方，再按 Y/X/Z 坐标
        result.sort(Comparator
                .comparingLong((BlockPos pos) -> distanceSquared(seedPos, pos))
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return result;
    }

    /** 构建 26 个方向的邻居偏移数组 */
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

    /** 计算两个位置之间的切比雪夫距离 */
    private static int chebyshevDistance(BlockPos a, BlockPos b) {
        return Math.max(
                Math.abs(a.getX() - b.getX()),
                Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
    }

    /** 计算两个位置之间的欧几里得距离的平方 */
    private static long distanceSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /** 候选方块过滤器 —— 判断方块是否符合条件 */
    @FunctionalInterface
    public interface CandidateFilter {
        boolean test(BlockPos pos, BlockState state, BlockState seedState);
    }

    /** 方块状态查找接口 —— 获取指定位置的方块状态（泛型友好） */
    @FunctionalInterface
    public interface StateLookup<S> {
        S get(BlockPos pos);
    }

    /** 泛型候选方块过滤器 —— 支持任意类型的方块状态 */
    @FunctionalInterface
    public interface GenericCandidateFilter<S> {
        boolean test(BlockPos pos, S state, S seedState);
    }
}
