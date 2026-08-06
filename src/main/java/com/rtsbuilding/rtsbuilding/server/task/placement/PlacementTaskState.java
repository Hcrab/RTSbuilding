package com.rtsbuilding.rtsbuilding.server.task.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Objects;

/**
 * detached placement executor 的完整纯值状态。
 *
 * <p>{@code definition} 只描述不可变放置参数和目标列表；cursor、成功/失败计数与历史位置
 * 在这里单独成为权威值。每个 slice 可以临时重建 PlaceBatchJob，但不能把该临时对象放回
 * Session，也不能把其 mutable 字段当作跨 tick 状态源。</p>
 */
public final class PlacementTaskState {
    private final CompoundTag definition;
    private final int workflowEntryId;
    private final int totalUnits;
    private final int cursorUnits;
    private final int succeededUnits;
    private final int failedUnits;
    private final List<BlockPos> placedPositions;
    private final PlacementResumePolicy resumePolicy;
    private final boolean creativeOperation;
    private final List<CompoundTag> historyRecords;

    public PlacementTaskState(
            CompoundTag definition, int workflowEntryId, int totalUnits, int cursorUnits,
            int succeededUnits, int failedUnits, List<BlockPos> placedPositions,
            PlacementResumePolicy resumePolicy, boolean creativeOperation,
            List<CompoundTag> historyRecords) {
        this(definition, workflowEntryId, totalUnits, cursorUnits, succeededUnits, failedUnits,
                placedPositions, resumePolicy, creativeOperation, historyRecords, false);
    }

    private PlacementTaskState(
            CompoundTag definition, int workflowEntryId, int totalUnits, int cursorUnits,
            int succeededUnits, int failedUnits, List<BlockPos> placedPositions,
            PlacementResumePolicy resumePolicy, boolean creativeOperation,
            List<CompoundTag> historyRecords, boolean trustedTransition) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(placedPositions, "placedPositions");
        Objects.requireNonNull(resumePolicy, "resumePolicy");
        Objects.requireNonNull(historyRecords, "historyRecords");
        if (definition.isEmpty()) throw new IllegalArgumentException("definition 不能为空");
        if (workflowEntryId < -1) throw new IllegalArgumentException("workflowEntryId 不能小于 -1");
        if (totalUnits < 0 || cursorUnits < 0 || succeededUnits < 0 || failedUnits < 0) {
            throw new IllegalArgumentException("placement 计数不能为负数");
        }
        if (cursorUnits > totalUnits) throw new IllegalArgumentException("cursorUnits 不能超过 totalUnits");
        if ((long) succeededUnits + failedUnits > cursorUnits) {
            throw new IllegalArgumentException("成功与失败数不能超过 cursorUnits");
        }
        if (placedPositions.size() != succeededUnits) {
            throw new IllegalArgumentException("placedPositions 数量必须等于 succeededUnits");
        }
        if (!historyRecords.isEmpty() && historyRecords.size() != succeededUnits) {
            throw new IllegalArgumentException("historyRecords 数量必须等于 succeededUnits");
        }
        this.definition = trustedTransition ? definition : definition.copy();
        this.workflowEntryId = workflowEntryId;
        this.totalUnits = totalUnits;
        this.cursorUnits = cursorUnits;
        this.succeededUnits = succeededUnits;
        this.failedUnits = failedUnits;
        this.placedPositions = trustedTransition
                ? List.copyOf(placedPositions)
                : placedPositions.stream().map(BlockPos::immutable).toList();
        this.resumePolicy = resumePolicy;
        this.creativeOperation = creativeOperation;
        this.historyRecords = trustedTransition ? List.copyOf(historyRecords) : copyTags(historyRecords);
    }

    /** 兼容创建默认策略快照的调用点；持久 codec 会显式保存策略。 */
    public PlacementTaskState(
            CompoundTag definition,
            int workflowEntryId,
            int totalUnits,
            int cursorUnits,
            int succeededUnits,
            int failedUnits,
            List<BlockPos> placedPositions) {
        this(definition, workflowEntryId, totalUnits, cursorUnits, succeededUnits, failedUnits,
                placedPositions, PlacementResumePolicy.DEFAULT, false, List.of());
    }

    public PlacementTaskState(
            CompoundTag definition, int workflowEntryId, int totalUnits, int cursorUnits,
            int succeededUnits, int failedUnits, List<BlockPos> placedPositions,
            PlacementResumePolicy resumePolicy) {
        this(definition, workflowEntryId, totalUnits, cursorUnits, succeededUnits, failedUnits,
                placedPositions, resumePolicy, false, List.of());
    }

    /** 防止调用方绕过 snapshot revision 修改定义 NBT。 */
    public CompoundTag definition() {
        return definition.copy();
    }

    public int workflowEntryId() { return workflowEntryId; }
    public int totalUnits() { return totalUnits; }
    public int cursorUnits() { return cursorUnits; }
    public int succeededUnits() { return succeededUnits; }
    public int failedUnits() { return failedUnits; }
    public List<BlockPos> placedPositions() { return placedPositions; }
    public PlacementResumePolicy resumePolicy() { return resumePolicy; }
    public boolean creativeOperation() { return creativeOperation; }

    public List<CompoundTag> historyRecords() {
        return copyTags(historyRecords);
    }

    /**
     * 仅供主线程执行镜像复用已经冻结的累计结果；调用方只可追加新元素，不能修改已有 Tag。
     * 对外读取 NBT 仍必须使用 {@link #historyRecords()} 的防御性副本。
     */
    public void appendFrozenProgressTo(
            List<BlockPos> positionDestination, List<CompoundTag> historyDestination) {
        Objects.requireNonNull(positionDestination, "positionDestination").addAll(placedPositions);
        Objects.requireNonNull(historyDestination, "historyDestination").addAll(historyRecords);
    }

    /** 当前状态是否已经消费全部目标。 */
    public boolean complete() {
        return cursorUnits >= totalUnits;
    }

    /** 返回同一任务的新纯值状态；用于 executor 将 slice 输出交回 TaskStore。 */
    public PlacementTaskState advance(
            int nextCursor, int nextSucceeded, int nextFailed, List<BlockPos> nextPlacedPositions,
            List<CompoundTag> nextHistoryRecords) {
        return new PlacementTaskState(definition, workflowEntryId, totalUnits,
                nextCursor, nextSucceeded, nextFailed, nextPlacedPositions, resumePolicy,
                creativeOperation, nextHistoryRecords, true);
    }

    /** 使用兼容恢复后升级过的不可变 definition 推进任务，其余累计状态保持原语义。 */
    public PlacementTaskState advance(
            CompoundTag nextDefinition, int nextCursor, int nextSucceeded, int nextFailed,
            List<BlockPos> nextPlacedPositions, List<CompoundTag> nextHistoryRecords) {
        return new PlacementTaskState(nextDefinition, workflowEntryId, totalUnits,
                nextCursor, nextSucceeded, nextFailed, nextPlacedPositions, resumePolicy,
                creativeOperation, nextHistoryRecords, true);
    }

    /** 兼容不产生新历史快照的纯状态测试与旧调用点。 */
    public PlacementTaskState advance(
            int nextCursor, int nextSucceeded, int nextFailed, List<BlockPos> nextPlacedPositions) {
        return advance(nextCursor, nextSucceeded, nextFailed, nextPlacedPositions, historyRecords);
    }

    /** 只改变后续逐目标策略，游标与历史计数保持不变。 */
    public PlacementTaskState withResumePolicy(PlacementResumePolicy nextPolicy) {
        return new PlacementTaskState(definition, workflowEntryId, totalUnits,
                cursorUnits, succeededUnits, failedUnits, placedPositions, nextPolicy,
                creativeOperation, historyRecords, true);
    }

    private static List<CompoundTag> copyTags(List<CompoundTag> tags) {
        return tags.stream().map(CompoundTag::copy).toList();
    }
}
