package com.rtsbuilding.rtsbuilding.server.task.placement;

import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;

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
    private final NBTTagCompound definition;
    private final int workflowEntryId;
    private final int totalUnits;
    private final int cursorUnits;
    private final int succeededUnits;
    private final int failedUnits;
    private final List<BlockPos> placedPositions;
    private final PlacementResumePolicy resumePolicy;

    public PlacementTaskState(NBTTagCompound definition, int workflowEntryId,
            int totalUnits, int cursorUnits, int succeededUnits, int failedUnits,
            List<BlockPos> placedPositions, PlacementResumePolicy resumePolicy) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(placedPositions, "placedPositions");
        Objects.requireNonNull(resumePolicy, "resumePolicy");
        if (com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.isEmpty(definition)) throw new IllegalArgumentException("definition 不能为空");
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
        this.definition = (NBTTagCompound) definition.copy();
        this.workflowEntryId = workflowEntryId;
        this.totalUnits = totalUnits;
        this.cursorUnits = cursorUnits;
        this.succeededUnits = succeededUnits;
        this.failedUnits = failedUnits;
        List<BlockPos> frozenPositions = new java.util.ArrayList<BlockPos>(placedPositions.size());
        for (BlockPos pos : placedPositions) {
            Objects.requireNonNull(pos, "placedPositions element");
            frozenPositions.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        }
        this.placedPositions = com.rtsbuilding.rtsbuilding.server.task.Java8Collections.copyList(frozenPositions);
        this.resumePolicy = resumePolicy;
    }

    public int workflowEntryId() { return workflowEntryId; }
    public int totalUnits() { return totalUnits; }
    public int cursorUnits() { return cursorUnits; }
    public int succeededUnits() { return succeededUnits; }
    public int failedUnits() { return failedUnits; }
    public List<BlockPos> placedPositions() { return placedPositions; }
    public PlacementResumePolicy resumePolicy() { return resumePolicy; }

    /** 兼容创建默认策略快照的调用点；持久 codec 会显式保存策略。 */
    public PlacementTaskState(
            NBTTagCompound definition,
            int workflowEntryId,
            int totalUnits,
            int cursorUnits,
            int succeededUnits,
            int failedUnits,
            List<BlockPos> placedPositions) {
        this(definition, workflowEntryId, totalUnits, cursorUnits, succeededUnits, failedUnits,
                placedPositions, PlacementResumePolicy.DEFAULT);
    }

    /** 防止调用方绕过 snapshot revision 修改定义 NBT。 */
    public NBTTagCompound definition() {
        return (NBTTagCompound) definition.copy();
    }

    /** 当前状态是否已经消费全部目标。 */
    public boolean complete() {
        return cursorUnits >= totalUnits;
    }

    /** 返回同一任务的新纯值状态；用于 executor 将 slice 输出交回 TaskStore。 */
    public PlacementTaskState advance(
            int nextCursor, int nextSucceeded, int nextFailed, List<BlockPos> nextPlacedPositions) {
        return new PlacementTaskState(definition, workflowEntryId, totalUnits,
                nextCursor, nextSucceeded, nextFailed, nextPlacedPositions, resumePolicy);
    }

    /** 只改变后续逐目标策略，游标与历史计数保持不变。 */
    public PlacementTaskState withResumePolicy(PlacementResumePolicy nextPolicy) {
        return new PlacementTaskState(definition, workflowEntryId, totalUnits,
                cursorUnits, succeededUnits, failedUnits, placedPositions, nextPolicy);
    }
}
