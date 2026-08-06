package com.rtsbuilding.rtsbuilding.common.smartfill;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一次智能填洞扫描的只读结果，不能直接作为客户端执行指令。 */
public final class SmartFillPlan {
    public enum Status {
        COMPLETE,
        USER_LIMIT_REACHED,
        DIAMETER_CLIPPED,
        INVALID_START,
        NO_TARGET,
        UNLOADED_BOUNDARY,
        HARD_LIMIT_REJECTED,
        QUERY_BUDGET_EXCEEDED
    }

    private final Status status;
    private final BlockPos start;
    private final List<BlockPos> targets;
    private final Bounds bounds;
    private final int visitedCandidates;
    private final int probes;

    public SmartFillPlan(Status status, BlockPos start, List<BlockPos> targets, Bounds bounds,
                         int visitedCandidates, int probes) {
        this.status = status == null ? Status.NO_TARGET : status;
        this.start = start == null ? BlockPos.ORIGIN : start.toImmutable();
        List<BlockPos> copied = new ArrayList<BlockPos>();
        if (targets != null) {
            for (BlockPos target : targets) {
                if (target != null) copied.add(target.toImmutable());
            }
        }
        this.targets = Collections.unmodifiableList(copied);
        this.bounds = bounds == null ? null : new Bounds(bounds.min(), bounds.max());
        this.visitedCandidates = Math.max(0, visitedCandidates);
        this.probes = Math.max(0, probes);
    }

    public Status status() { return status; }
    public BlockPos start() { return start; }
    public List<BlockPos> targets() { return targets; }
    public Bounds bounds() { return bounds; }
    public int visitedCandidates() { return visitedCandidates; }
    public int probes() { return probes; }

    public boolean canSubmit() {
        return !targets.isEmpty() && (status == Status.COMPLETE
                || status == Status.USER_LIMIT_REACHED || status == Status.DIAMETER_CLIPPED);
    }

    public boolean partial() {
        return status == Status.USER_LIMIT_REACHED || status == Status.DIAMETER_CLIPPED;
    }

    /** 方块闭区间包围盒，避免引入只用于预览的现代 BoundingBox API。 */
    public static final class Bounds {
        private final BlockPos min;
        private final BlockPos max;

        public Bounds(BlockPos min, BlockPos max) {
            this.min = min == null ? BlockPos.ORIGIN : min.toImmutable();
            this.max = max == null ? BlockPos.ORIGIN : max.toImmutable();
        }

        public BlockPos min() { return min; }
        public BlockPos max() { return max; }
    }
}
