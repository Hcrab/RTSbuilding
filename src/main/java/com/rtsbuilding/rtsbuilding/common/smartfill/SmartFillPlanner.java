package com.rtsbuilding.rtsbuilding.common.smartfill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智能填洞的共享、只读规划器。
 *
 * <p>本类只负责从真实点击面相邻格开始，以稳定的六向 BFS 寻找可填目标。每个目标都要
 * 独立通过“六个轴向中至少四个方向在检测半径内遇到真实洞壁”的判定；已经访问或已经
 * 接受的空气从不作为伪墙。规划器不加载区块、不读取玩家物品、不检查领地，也不提交
 * 任务，这些职责保留在服务端适配层。</p>
 */
public final class SmartFillPlanner {
    private static final Direction[] BFS_DIRECTIONS = {
            Direction.DOWN, Direction.UP,
            Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST
    };
    private static final int REQUIRED_BOUNDARY_DIRECTIONS = 4;

    private SmartFillPlanner() {
    }

    public static SmartFillPlan plan(
            BlockPos clicked,
            Direction face,
            Limits limits,
            SmartFillQuery query) {
        if (clicked == null || face == null || limits == null || query == null) {
            return rejected(SmartFillPlan.Status.INVALID_START, BlockPos.ZERO, 0, 0);
        }
        if (limits.requestedMaxBlocks() <= 0
                || limits.diameter() <= 0
                || limits.hardMaxBlocks() <= 0
                || limits.queryBudget() <= 0) {
            return rejected(SmartFillPlan.Status.INVALID_START, clicked.relative(face), 0, 0);
        }
        BlockPos start = clicked.relative(face).immutable();
        if (limits.requestedMaxBlocks() > limits.hardMaxBlocks()) {
            return rejected(SmartFillPlan.Status.HARD_LIMIT_REJECTED, start, 0, 0);
        }

        Scan scan = new Scan(query, limits.queryBudget());
        try {
            if (scan.cell(start) != SmartFillCell.CANDIDATE) {
                return rejected(SmartFillPlan.Status.INVALID_START, start, 0, scan.probes);
            }
            int radius = Math.max(1, limits.diameter() / 2);
            if (!isEnclosed(start, radius, scan)) {
                return rejected(SmartFillPlan.Status.NO_TARGET, start, 1, scan.probes);
            }

            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> visited = new HashSet<>();
            List<BlockPos> targets = new ArrayList<>(
                    Math.min(limits.requestedMaxBlocks(), 1024));
            queue.add(start);
            visited.add(start);
            boolean diameterClipped = false;

            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                targets.add(current.immutable());

                for (Direction direction : BFS_DIRECTIONS) {
                    BlockPos next = current.relative(direction);
                    if (!insideDiameter(start, next, radius)) {
                        if (scan.cell(next) == SmartFillCell.CANDIDATE) {
                            diameterClipped = true;
                        }
                        continue;
                    }
                    if (!visited.add(next)) {
                        continue;
                    }
                    if (scan.cell(next) == SmartFillCell.CANDIDATE
                            && isEnclosed(next, radius, scan)) {
                        queue.addLast(next.immutable());
                    }
                }

                if (targets.size() >= limits.requestedMaxBlocks()) {
                    SmartFillPlan.Status status = queue.isEmpty()
                            ? diameterClipped
                            ? SmartFillPlan.Status.DIAMETER_CLIPPED
                            : SmartFillPlan.Status.COMPLETE
                            : SmartFillPlan.Status.USER_LIMIT_REACHED;
                    return completed(status, start, targets, visited.size(), scan.probes);
                }
            }

            SmartFillPlan.Status status = diameterClipped
                    ? SmartFillPlan.Status.DIAMETER_CLIPPED
                    : SmartFillPlan.Status.COMPLETE;
            return completed(status, start, targets, visited.size(), scan.probes);
        } catch (UnloadedBoundary ignored) {
            return rejected(
                    SmartFillPlan.Status.UNLOADED_BOUNDARY, start, scan.cachedCells(), scan.probes);
        } catch (ProbeBudgetExceeded ignored) {
            return rejected(
                    SmartFillPlan.Status.QUERY_BUDGET_EXCEEDED, start, scan.cachedCells(), scan.probes);
        }
    }

    private static boolean isEnclosed(BlockPos pos, int radius, Scan scan) {
        int boundaries = 0;
        for (Direction direction : BFS_DIRECTIONS) {
            for (int distance = 1; distance <= radius; distance++) {
                SmartFillCell cell = scan.cell(pos.relative(direction, distance));
                if (cell == SmartFillCell.BOUNDARY) {
                    boundaries++;
                    break;
                }
                if (cell == SmartFillCell.FORBIDDEN) {
                    break;
                }
            }
            if (boundaries >= REQUIRED_BOUNDARY_DIRECTIONS) {
                return true;
            }
        }
        return false;
    }

    private static boolean insideDiameter(BlockPos start, BlockPos candidate, int radius) {
        return Math.abs(candidate.getX() - start.getX()) <= radius
                && Math.abs(candidate.getY() - start.getY()) <= radius
                && Math.abs(candidate.getZ() - start.getZ()) <= radius;
    }

    private static SmartFillPlan completed(
            SmartFillPlan.Status status,
            BlockPos start,
            List<BlockPos> targets,
            int visited,
            int probes) {
        if (targets == null || targets.isEmpty()) {
            return rejected(SmartFillPlan.Status.NO_TARGET, start, visited, probes);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos target : targets) {
            minX = Math.min(minX, target.getX());
            minY = Math.min(minY, target.getY());
            minZ = Math.min(minZ, target.getZ());
            maxX = Math.max(maxX, target.getX());
            maxY = Math.max(maxY, target.getY());
            maxZ = Math.max(maxZ, target.getZ());
        }
        return new SmartFillPlan(
                status,
                start,
                targets,
                new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ),
                visited,
                probes);
    }

    private static SmartFillPlan rejected(
            SmartFillPlan.Status status,
            BlockPos start,
            int visited,
            int probes) {
        return new SmartFillPlan(status, start, List.of(), null, visited, probes);
    }

    /**
     * 单次规划的全部硬边界。调用方可以给客户端与服务端相同的玩家参数，同时让服务端
     * 使用更严格的硬上限和探测预算。
     */
    public record Limits(
            int requestedMaxBlocks,
            int diameter,
            int hardMaxBlocks,
            int queryBudget) {
    }

    private static final class Scan {
        private final SmartFillQuery query;
        private final int budget;
        private final Map<BlockPos, SmartFillCell> cache = new HashMap<>();
        private int probes;

        private Scan(SmartFillQuery query, int budget) {
            this.query = query;
            this.budget = budget;
        }

        private SmartFillCell cell(BlockPos pos) {
            if (++this.probes > this.budget) {
                throw ProbeBudgetExceeded.INSTANCE;
            }
            BlockPos key = pos.immutable();
            SmartFillCell cached = this.cache.get(key);
            if (cached != null) {
                return cached;
            }
            SmartFillCell classified = this.query.classify(key);
            SmartFillCell normalized = classified == null ? SmartFillCell.FORBIDDEN : classified;
            this.cache.put(key, normalized);
            if (normalized == SmartFillCell.UNLOADED) {
                throw UnloadedBoundary.INSTANCE;
            }
            return normalized;
        }

        private int cachedCells() {
            return this.cache.size();
        }
    }

    private static final class UnloadedBoundary extends RuntimeException {
        private static final UnloadedBoundary INSTANCE = new UnloadedBoundary();
        private UnloadedBoundary() {
            super(null, null, false, false);
        }
    }

    private static final class ProbeBudgetExceeded extends RuntimeException {
        private static final ProbeBudgetExceeded INSTANCE = new ProbeBudgetExceeded();
        private ProbeBudgetExceeded() {
            super(null, null, false, false);
        }
    }
}

