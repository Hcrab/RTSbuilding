package com.rtsbuilding.rtsbuilding.server.task.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * detached mining 的纯值权威状态。
 *
 * <p>工具租约和 Capability 不进入这里；它们只在服务端主线程 slice 内从当前运行环境绑定。
 * 目标、游标、单方块破坏进度与批量历史则完全脱离 Session 生命周期字段。</p>
 */
public final class MiningTaskState {
    public enum Mode { PROGRESSIVE_SINGLE, BATCH }

    private final Mode mode;
    private final int workflowEntryId;
    private final List<BlockPos> remainingTargets;
    private final int totalUnits;
    private final int cursorUnits;
    private final int succeededUnits;
    private final int failedUnits;
    private final Direction face;
    private final int toolSlot;
    private final boolean selectedToolRequested;
    private final boolean toolProtectionEnabled;
    private final float blockProgress;
    private final int visibleStage;
    private final List<CompoundTag> historyRecords;

    public MiningTaskState(
            Mode mode, int workflowEntryId, List<BlockPos> remainingTargets,
            int totalUnits, int cursorUnits, int succeededUnits, int failedUnits,
            Direction face, int toolSlot, boolean selectedToolRequested,
            boolean toolProtectionEnabled, float blockProgress, int visibleStage,
            List<CompoundTag> historyRecords) {
        this(mode, workflowEntryId, remainingTargets, totalUnits, cursorUnits,
                succeededUnits, failedUnits, face, toolSlot, selectedToolRequested,
                toolProtectionEnabled, blockProgress, visibleStage, historyRecords, false);
    }

    private MiningTaskState(
            Mode mode, int workflowEntryId, List<BlockPos> remainingTargets,
            int totalUnits, int cursorUnits, int succeededUnits, int failedUnits,
            Direction face, int toolSlot, boolean selectedToolRequested,
            boolean toolProtectionEnabled, float blockProgress, int visibleStage,
            List<CompoundTag> historyRecords, boolean trustedTransition) {
        this.mode = Objects.requireNonNull(mode, "mode");
        if (workflowEntryId < -1) throw new IllegalArgumentException("workflowEntryId 不能小于 -1");
        Objects.requireNonNull(remainingTargets, "remainingTargets");
        Objects.requireNonNull(historyRecords, "historyRecords");
        if (totalUnits < 0 || cursorUnits < 0 || succeededUnits < 0 || failedUnits < 0) {
            throw new IllegalArgumentException("mining 计数不能为负数");
        }
        if (cursorUnits > totalUnits || (long) succeededUnits + failedUnits > cursorUnits) {
            throw new IllegalArgumentException("mining cursor/result 计数不一致");
        }
        if (remainingTargets.size() > totalUnits - cursorUnits) {
            throw new IllegalArgumentException("remainingTargets 超过剩余任务容量");
        }
        if (!Float.isFinite(blockProgress) || blockProgress < 0.0F || blockProgress >= 1.0F) {
            throw new IllegalArgumentException("blockProgress 必须位于 [0,1)");
        }
        if (visibleStage < -1 || visibleStage > 9) throw new IllegalArgumentException("visibleStage 越界");
        if (mode == Mode.PROGRESSIVE_SINGLE && remainingTargets.isEmpty()) {
            throw new IllegalArgumentException("渐进单方块状态必须至少保留当前目标");
        }
        this.workflowEntryId = workflowEntryId;
        this.remainingTargets = trustedTransition
                ? List.copyOf(remainingTargets)
                : remainingTargets.stream().map(BlockPos::immutable).toList();
        this.totalUnits = totalUnits;
        this.cursorUnits = cursorUnits;
        this.succeededUnits = succeededUnits;
        this.failedUnits = failedUnits;
        this.face = face == null ? Direction.DOWN : face;
        this.toolSlot = Math.max(0, Math.min(8, toolSlot));
        this.selectedToolRequested = selectedToolRequested;
        this.toolProtectionEnabled = toolProtectionEnabled;
        this.blockProgress = blockProgress;
        this.visibleStage = visibleStage;
        if (trustedTransition) {
            this.historyRecords = List.copyOf(historyRecords);
        } else {
            List<CompoundTag> copiedHistory = new ArrayList<>(historyRecords.size());
            for (CompoundTag history : historyRecords) {
                if (history == null || history.isEmpty()) throw new IllegalArgumentException("history record 不能为空");
                copiedHistory.add(history.copy());
            }
            this.historyRecords = List.copyOf(copiedHistory);
        }
    }

    public Mode mode() { return mode; }
    public int workflowEntryId() { return workflowEntryId; }
    public List<BlockPos> remainingTargets() { return remainingTargets; }
    public int totalUnits() { return totalUnits; }
    public int cursorUnits() { return cursorUnits; }
    public int succeededUnits() { return succeededUnits; }
    public int failedUnits() { return failedUnits; }
    public Direction face() { return face; }
    public int toolSlot() { return toolSlot; }
    public boolean selectedToolRequested() { return selectedToolRequested; }
    public boolean toolProtectionEnabled() { return toolProtectionEnabled; }
    public float blockProgress() { return blockProgress; }
    public int visibleStage() { return visibleStage; }

    public List<CompoundTag> historyRecords() {
        return historyRecords.stream().map(CompoundTag::copy).toList();
    }

    /**
     * 仅供主线程执行镜像复用已冻结的历史引用；调用方只能追加新 Tag，不能修改已有 Tag。
     * 对外读取仍必须使用 {@link #historyRecords()} 的防御性副本。
     */
    public void appendFrozenHistoryTo(List<CompoundTag> destination) {
        Objects.requireNonNull(destination, "destination").addAll(historyRecords);
    }

    public boolean complete() { return remainingTargets.isEmpty() || cursorUnits >= totalUnits; }

    /**
     * 首个渐进挖掘目标已经结束、剩余目标已交给自动批处理时返回 {@code true}。
     *
     * <p>鼠标或按键松开只能取消仍处于 {@link Mode#PROGRESSIVE_SINGLE} 的首块挖掘；
     * 一旦切换到批处理，后续连锁目标不再依赖玩家持续按住输入。</p>
     */
    public boolean committedBatch() { return mode == Mode.BATCH; }

    public MiningTaskState next(
            Mode nextMode, List<BlockPos> nextTargets,
            int nextCursor, int nextSucceeded, int nextFailed,
            float nextProgress, int nextStage, List<CompoundTag> nextHistory) {
        return new MiningTaskState(nextMode, workflowEntryId, nextTargets,
                totalUnits, nextCursor, nextSucceeded, nextFailed,
                face, toolSlot, selectedToolRequested, toolProtectionEnabled,
                nextProgress, nextStage, nextHistory, true);
    }
}
