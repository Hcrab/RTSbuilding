package com.rtsbuilding.rtsbuilding.common.smartfill;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智能填洞的共享只读规划器。
 *
 * <p>从点击面的相邻格开始，以六向 BFS 只收集有至少四面真实洞壁的候选格。未加载边界、
 * 查询预算耗尽或用户请求越过硬上限都会整体拒绝，绝不把未知空气当作封闭洞穴。</p>
 */
public final class SmartFillPlanner {
    private static final EnumFacing[] DIRECTIONS = {
            EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH,
            EnumFacing.WEST, EnumFacing.EAST
    };
    private static final int REQUIRED_BOUNDARIES = 4;

    private SmartFillPlanner() {
    }

    public static SmartFillPlan plan(BlockPos clicked, EnumFacing face, Limits limits,
                                     SmartFillQuery query) {
        if (clicked == null || face == null || limits == null || query == null) {
            return rejected(SmartFillPlan.Status.INVALID_START, BlockPos.ORIGIN, 0, 0);
        }
        BlockPos start = clicked.offset(face).toImmutable();
        if (limits.requestedMaxBlocks() <= 0 || limits.diameter() <= 0
                || limits.hardMaxBlocks() <= 0 || limits.queryBudget() <= 0) {
            return rejected(SmartFillPlan.Status.INVALID_START, start, 0, 0);
        }
        if (limits.requestedMaxBlocks() > limits.hardMaxBlocks()) {
            return rejected(SmartFillPlan.Status.HARD_LIMIT_REJECTED, start, 0, 0);
        }

        Scan scan = new Scan(query, limits.queryBudget());
        try {
            if (scan.cell(start) != SmartFillCell.CANDIDATE) {
                return rejected(SmartFillPlan.Status.INVALID_START, start, 0, scan.probes());
            }
            int radius = Math.max(1, limits.diameter() / 2);
            if (!enclosed(start, radius, scan)) {
                return rejected(SmartFillPlan.Status.NO_TARGET, start, 1, scan.probes());
            }

            ArrayDeque<BlockPos> queue = new ArrayDeque<BlockPos>();
            Set<BlockPos> visited = new HashSet<BlockPos>();
            List<BlockPos> targets = new ArrayList<BlockPos>(Math.min(limits.requestedMaxBlocks(), 1024));
            queue.add(start);
            visited.add(start);
            boolean diameterClipped = false;
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                targets.add(current.toImmutable());
                for (EnumFacing direction : DIRECTIONS) {
                    BlockPos next = current.offset(direction);
                    if (!insideDiameter(start, next, radius)) {
                        if (scan.cell(next) == SmartFillCell.CANDIDATE) diameterClipped = true;
                        continue;
                    }
                    if (!visited.add(next)) continue;
                    if (scan.cell(next) == SmartFillCell.CANDIDATE && enclosed(next, radius, scan)) {
                        queue.addLast(next.toImmutable());
                    }
                }
                if (targets.size() >= limits.requestedMaxBlocks()) {
                    SmartFillPlan.Status status = queue.isEmpty()
                            ? (diameterClipped ? SmartFillPlan.Status.DIAMETER_CLIPPED
                            : SmartFillPlan.Status.COMPLETE)
                            : SmartFillPlan.Status.USER_LIMIT_REACHED;
                    return completed(status, start, targets, visited.size(), scan.probes());
                }
            }
            return completed(diameterClipped ? SmartFillPlan.Status.DIAMETER_CLIPPED
                    : SmartFillPlan.Status.COMPLETE, start, targets, visited.size(), scan.probes());
        } catch (UnloadedBoundary ignored) {
            return rejected(SmartFillPlan.Status.UNLOADED_BOUNDARY, start,
                    scan.cachedCells(), scan.probes());
        } catch (ProbeBudgetExceeded ignored) {
            return rejected(SmartFillPlan.Status.QUERY_BUDGET_EXCEEDED, start,
                    scan.cachedCells(), scan.probes());
        }
    }

    private static boolean enclosed(BlockPos position, int radius, Scan scan) {
        int boundaries = 0;
        for (EnumFacing direction : DIRECTIONS) {
            for (int distance = 1; distance <= radius; distance++) {
                SmartFillCell cell = scan.cell(position.offset(direction, distance));
                if (cell == SmartFillCell.BOUNDARY) {
                    boundaries++;
                    break;
                }
                if (cell == SmartFillCell.FORBIDDEN) break;
            }
            if (boundaries >= REQUIRED_BOUNDARIES) return true;
        }
        return false;
    }

    private static boolean insideDiameter(BlockPos start, BlockPos candidate, int radius) {
        return Math.abs(candidate.getX() - start.getX()) <= radius
                && Math.abs(candidate.getY() - start.getY()) <= radius
                && Math.abs(candidate.getZ() - start.getZ()) <= radius;
    }

    private static SmartFillPlan completed(SmartFillPlan.Status status, BlockPos start,
                                           List<BlockPos> targets, int visited, int probes) {
        if (targets == null || targets.isEmpty()) {
            return rejected(SmartFillPlan.Status.NO_TARGET, start, visited, probes);
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos target : targets) {
            minX = Math.min(minX, target.getX());
            minY = Math.min(minY, target.getY());
            minZ = Math.min(minZ, target.getZ());
            maxX = Math.max(maxX, target.getX());
            maxY = Math.max(maxY, target.getY());
            maxZ = Math.max(maxZ, target.getZ());
        }
        return new SmartFillPlan(status, start, targets,
                new SmartFillPlan.Bounds(new BlockPos(minX, minY, minZ),
                        new BlockPos(maxX, maxY, maxZ)), visited, probes);
    }

    private static SmartFillPlan rejected(SmartFillPlan.Status status, BlockPos start,
                                          int visited, int probes) {
        return new SmartFillPlan(status, start, java.util.Collections.<BlockPos>emptyList(),
                null, visited, probes);
    }

    /** 客户端预览与服务端重算共享的紧凑参数边界。 */
    public static final class Limits {
        private final int requestedMaxBlocks;
        private final int diameter;
        private final int hardMaxBlocks;
        private final int queryBudget;

        public Limits(int requestedMaxBlocks, int diameter, int hardMaxBlocks, int queryBudget) {
            this.requestedMaxBlocks = requestedMaxBlocks;
            this.diameter = diameter;
            this.hardMaxBlocks = hardMaxBlocks;
            this.queryBudget = queryBudget;
        }

        public int requestedMaxBlocks() { return requestedMaxBlocks; }
        public int diameter() { return diameter; }
        public int hardMaxBlocks() { return hardMaxBlocks; }
        public int queryBudget() { return queryBudget; }
    }

    private static final class Scan {
        private final SmartFillQuery query;
        private final int budget;
        private final Map<BlockPos, SmartFillCell> cache = new HashMap<BlockPos, SmartFillCell>();
        private int probes;

        private Scan(SmartFillQuery query, int budget) {
            this.query = query;
            this.budget = budget;
        }

        private SmartFillCell cell(BlockPos position) {
            if (++probes > budget) throw ProbeBudgetExceeded.INSTANCE;
            BlockPos key = position.toImmutable();
            SmartFillCell cached = cache.get(key);
            if (cached != null) return cached;
            SmartFillCell classified = query.classify(key);
            SmartFillCell normalized = classified == null ? SmartFillCell.FORBIDDEN : classified;
            cache.put(key, normalized);
            if (normalized == SmartFillCell.UNLOADED) throw UnloadedBoundary.INSTANCE;
            return normalized;
        }

        private int probes() { return probes; }
        private int cachedCells() { return cache.size(); }
    }

    private static final class UnloadedBoundary extends RuntimeException {
        private static final UnloadedBoundary INSTANCE = new UnloadedBoundary();
        private UnloadedBoundary() { super(null, null, false, false); }
    }

    private static final class ProbeBudgetExceeded extends RuntimeException {
        private static final ProbeBudgetExceeded INSTANCE = new ProbeBudgetExceeded();
        private ProbeBudgetExceeded() { super(null, null, false, false); }
    }
}
