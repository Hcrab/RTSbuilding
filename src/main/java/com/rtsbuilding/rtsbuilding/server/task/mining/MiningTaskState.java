package com.rtsbuilding.rtsbuilding.server.task.mining;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.common.RtsHistoryConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBudget;

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

    /** 单次任务目标数上限；与 codec、领域入口共用同一实现边界。 */
    public static final int MAX_TARGETS = MiningLimits.MAX_VOLUME;
    /** 门、床等附属破坏会产生额外历史，不能把历史条目数简单等同目标数。 */
    public static final int MAX_HISTORY_RECORDS_PER_TARGET =
            RtsHistoryConstants.MAX_HISTORY_RECORDS_PER_TARGET;

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
    private final boolean creativeOperation;
    /** 不持久化的紧凑历史派生摘要，切片之间沿用并只追加新增记录。 */
    private final HistoryBudget historyBudget;
    /** 同一任务已经提示过历史容量时，后续切片不再重复刷屏。 */
    private final boolean historyCapacityReported;

    public MiningTaskState(
            Mode mode, int workflowEntryId, List<BlockPos> remainingTargets,
            int totalUnits, int cursorUnits, int succeededUnits, int failedUnits,
            Direction face, int toolSlot, boolean selectedToolRequested,
            boolean toolProtectionEnabled, float blockProgress, int visibleStage,
            List<CompoundTag> historyRecords) {
        this(mode, workflowEntryId, remainingTargets, totalUnits, cursorUnits,
                succeededUnits, failedUnits, face, toolSlot, selectedToolRequested,
                toolProtectionEnabled, blockProgress, visibleStage, historyRecords, false, false,
                false, null);
    }

    public MiningTaskState(
            Mode mode, int workflowEntryId, List<BlockPos> remainingTargets,
            int totalUnits, int cursorUnits, int succeededUnits, int failedUnits,
            Direction face, int toolSlot, boolean selectedToolRequested,
            boolean toolProtectionEnabled, float blockProgress, int visibleStage,
            List<CompoundTag> historyRecords, boolean creativeOperation) {
        this(mode, workflowEntryId, remainingTargets, totalUnits, cursorUnits,
                succeededUnits, failedUnits, face, toolSlot, selectedToolRequested,
                toolProtectionEnabled, blockProgress, visibleStage, historyRecords,
                creativeOperation, false, false, null);
    }

    private MiningTaskState(
            Mode mode, int workflowEntryId, List<BlockPos> remainingTargets,
            int totalUnits, int cursorUnits, int succeededUnits, int failedUnits,
            Direction face, int toolSlot, boolean selectedToolRequested,
            boolean toolProtectionEnabled, float blockProgress, int visibleStage,
            List<CompoundTag> historyRecords, boolean creativeOperation,
            boolean trustedTransition, boolean historyCapacityReported,
            HistoryBudget trustedHistoryBudget) {
        this.mode = Objects.requireNonNull(mode, "mode");
        if (workflowEntryId < -1) throw new IllegalArgumentException("workflowEntryId 不能小于 -1");
        Objects.requireNonNull(remainingTargets, "remainingTargets");
        Objects.requireNonNull(historyRecords, "historyRecords");
        if (totalUnits < 0 || totalUnits > MAX_TARGETS
                || cursorUnits < 0 || succeededUnits < 0 || failedUnits < 0) {
            throw new IllegalArgumentException("mining 计数为负数或超过目标上限");
        }
        if (cursorUnits > totalUnits || (long) succeededUnits + failedUnits > cursorUnits) {
            throw new IllegalArgumentException("mining cursor/result 计数不一致");
        }
        if (remainingTargets.size() > totalUnits - cursorUnits) {
            throw new IllegalArgumentException("remainingTargets 超过剩余任务容量");
        }
        if (historyRecords.size() > maxHistoryRecordsForTargets(totalUnits)) {
            throw new IllegalArgumentException("historyRecords 超过有界上限");
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
        this.creativeOperation = creativeOperation;
        if (trustedTransition) {
            this.historyRecords = List.copyOf(historyRecords);
            this.historyBudget = Objects.requireNonNull(trustedHistoryBudget, "trustedHistoryBudget");
            if (this.historyBudget.targetCount() != totalUnits
                    || !this.historyBudget.accepts()
                    || this.historyBudget.recordCount() != this.historyRecords.size()) {
                throw new IllegalArgumentException("trusted mining history budget 无效");
            }
        } else {
            List<CompoundTag> copiedHistory = new ArrayList<>(historyRecords.size());
            for (CompoundTag history : historyRecords) {
                if (history == null || history.isEmpty()) throw new IllegalArgumentException("history record 不能为空");
                copiedHistory.add(history.copy());
            }
            this.historyRecords = List.copyOf(copiedHistory);
            this.historyBudget = HistoryBudget.forTargets(totalUnits, this.historyRecords);
            if (!this.historyBudget.accepts()) {
                throw new IllegalArgumentException("mining history 超过 payload/节点/NBT 预算");
            }
        }
        this.historyCapacityReported = historyCapacityReported;
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
    public boolean creativeOperation() { return creativeOperation; }
    public HistoryBudget historyBudget() { return historyBudget; }
    public boolean historyCapacityReported() { return historyCapacityReported; }

    /**
     * 返回允许的历史条目数；附属方块快照共享任务预算，但不占用目标游标名额。
     */
    public static int maxHistoryRecordsForTargets(int targetCount) {
        if (targetCount < 0 || targetCount > MAX_TARGETS) {
            throw new IllegalArgumentException("mining target 数量越界");
        }
        return targetCount * MAX_HISTORY_RECORDS_PER_TARGET;
    }

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
        Objects.requireNonNull(nextTargets, "nextTargets");
        Objects.requireNonNull(nextHistory, "nextHistory");
        List<BlockPos> defensiveTargets = nextTargets.stream()
                .map(target -> {
                    if (target == null) throw new IllegalArgumentException("target 不能为 null");
                    return target.immutable();
                })
                .toList();
        // 兼容旧调用属于外部边界；只有显式传入可信摘要的热路径重载才复用 NBT 引用。
        List<CompoundTag> defensiveHistory = nextHistory.stream()
                .map(history -> {
                    if (history == null) throw new IllegalArgumentException("history record 不能为 null");
                    return history.copy();
                })
                .toList();
        return next(nextMode, defensiveTargets, nextCursor, nextSucceeded, nextFailed,
                nextProgress, nextStage, defensiveHistory, null, historyCapacityReported);
    }

    /**
     * 可信切片迁移。服务端已经以旧历史为冻结前缀时传入增量摘要，避免每 32 块重新深拷贝
     * 或重算完整历史；普通外部调用仍可使用上面的兼容重载。
     */
    public MiningTaskState next(
            Mode nextMode, List<BlockPos> nextTargets,
            int nextCursor, int nextSucceeded, int nextFailed,
            float nextProgress, int nextStage, List<CompoundTag> nextHistory,
            HistoryBudget nextBudget, boolean nextHistoryCapacityReported) {
        Objects.requireNonNull(nextHistory, "nextHistory");
        HistoryBudget budget = nextBudget;
        if (budget == null) {
            boolean sharedPrefix = sharesHistoryPrefix(nextHistory);
            budget = sharedPrefix
                    ? historyBudget.append(nextHistory, historyRecords.size())
                    : HistoryBudget.forTargets(totalUnits, nextHistory);
        }
        return new MiningTaskState(nextMode, workflowEntryId, nextTargets,
                totalUnits, nextCursor, nextSucceeded, nextFailed,
                face, toolSlot, selectedToolRequested, toolProtectionEnabled,
                nextProgress, nextStage, nextHistory, creativeOperation, true,
                nextHistoryCapacityReported, budget);
    }

    private boolean sharesHistoryPrefix(List<CompoundTag> candidate) {
        if (candidate.size() < historyRecords.size()) return false;
        for (int i = 0; i < historyRecords.size(); i++) {
            if (candidate.get(i) != historyRecords.get(i)) return false;
        }
        return true;
    }
}
