package com.rtsbuilding.rtsbuilding.common.smartfill;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * 一次智能填洞扫描的不可变结果。
 *
 * <p>结果只描述目标与扫描结论，不修改世界、不提取物品，也不生成玩家语言文本。
 * 服务端只有在 {@link #canSubmit()} 为真时才能把目标交给普通持久化放置任务。</p>
 */
public record SmartFillPlan(
        Status status,
        BlockPos start,
        List<BlockPos> targets,
        BoundingBox bounds,
        int visitedCandidates,
        int probes) {

    public SmartFillPlan {
        status = status == null ? Status.NO_TARGET : status;
        start = start == null ? BlockPos.ZERO : start.immutable();
        targets = targets == null ? List.of() : List.copyOf(targets);
        bounds = bounds == null ? null : new BoundingBox(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        visitedCandidates = Math.max(0, visitedCandidates);
        probes = Math.max(0, probes);
    }

    public boolean canSubmit() {
        return !targets.isEmpty()
                && (status == Status.COMPLETE
                || status == Status.USER_LIMIT_REACHED
                || status == Status.DIAMETER_CLIPPED);
    }

    public boolean partial() {
        return status == Status.USER_LIMIT_REACHED || status == Status.DIAMETER_CLIPPED;
    }

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
}

