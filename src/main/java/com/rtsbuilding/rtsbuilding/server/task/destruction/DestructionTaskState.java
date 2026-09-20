package com.rtsbuilding.rtsbuilding.server.task.destruction;

import com.rtsbuilding.rtsbuilding.common.RtsHistoryConstants;
import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * detached destruction executor 的完整纯值状态。
 *
 * <p>目标和工具参数在任务创建后不变；cursor、成功/失败计数、已破坏位置及破坏前历史快照
 * 只由此状态拥有。外部构造会校验并深复制可变 NBT；切片迁移使用私有 trusted transition，
 * 复用冻结的 targets、历史前缀和增量预算，不在每个 slice 重新哈希 26 万个坐标。</p>
 */
public final class DestructionTaskState {
    /** 与范围挖掘和新操作入口共用的单次目标实现上限。 */
    public static final int MAX_TARGETS = MiningLimits.MAX_VOLUME;
    /** 附属结构可产生额外历史；历史数量不是目标数量的别名。 */
    public static final int MAX_HISTORY_RECORDS_PER_TARGET =
            RtsHistoryConstants.MAX_HISTORY_RECORDS_PER_TARGET;

    private final List<BlockPos> targets;
    private final byte toolSlot;
    private final boolean toolProtectionEnabled;
    private final boolean selectedToolRequested;
    private final int workflowEntryId;
    private final int cursorUnits;
    private final int succeededUnits;
    private final int failedUnits;
    private final List<BlockPos> destroyedPositions;
    private final List<CompoundTag> historyRecords;
    private final boolean creativeOperation;
    /** 不持久化的 compact history 派生预算。 */
    private final HistoryBudget historyBudget;
    /** 同一 task 已发送过容量反馈时保持静默，避免后续 slice 重复提示。 */
    private final boolean historyCapacityReported;

    public DestructionTaskState(
            List<BlockPos> targets, byte toolSlot, boolean toolProtectionEnabled,
            boolean selectedToolRequested, int workflowEntryId, int cursorUnits,
            int succeededUnits, int failedUnits, List<BlockPos> destroyedPositions,
            List<CompoundTag> historyRecords, boolean creativeOperation) {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(destroyedPositions, "destroyedPositions");
        Objects.requireNonNull(historyRecords, "historyRecords");
        validateScalars(targets, toolSlot, workflowEntryId, cursorUnits, succeededUnits,
                failedUnits, destroyedPositions, historyRecords);

        List<BlockPos> frozenTargets = immutableUniquePositions(targets, "targets");
        List<BlockPos> frozenDestroyed = immutableUniquePositions(
                destroyedPositions, "destroyedPositions");
        Set<BlockPos> targetSet = new HashSet<>(frozenTargets);
        if (!targetSet.containsAll(frozenDestroyed)) {
            throw new IllegalArgumentException("destroyedPositions 必须来自任务目标");
        }
        List<CompoundTag> frozenHistory = copyTags(historyRecords);
        HistoryBudget budget = HistoryBudget.forTargets(frozenTargets.size(), frozenHistory);
        if (!budget.accepts()) {
            throw new IllegalArgumentException("destruction history 超过 payload/节点/NBT 预算");
        }

        this.targets = frozenTargets;
        this.toolSlot = toolSlot;
        this.toolProtectionEnabled = toolProtectionEnabled;
        this.selectedToolRequested = selectedToolRequested;
        this.workflowEntryId = workflowEntryId;
        this.cursorUnits = cursorUnits;
        this.succeededUnits = succeededUnits;
        this.failedUnits = failedUnits;
        this.destroyedPositions = frozenDestroyed;
        this.historyRecords = List.copyOf(frozenHistory);
        this.creativeOperation = creativeOperation;
        this.historyBudget = budget;
        this.historyCapacityReported = false;
    }

    /** 兼容旧调用：默认按生存模式历史语义。 */
    public DestructionTaskState(
            List<BlockPos> targets, byte toolSlot, boolean toolProtectionEnabled,
            boolean selectedToolRequested, int workflowEntryId, int cursorUnits,
            int succeededUnits, int failedUnits, List<BlockPos> destroyedPositions,
            List<CompoundTag> historyRecords) {
        this(targets, toolSlot, toolProtectionEnabled, selectedToolRequested, workflowEntryId,
                cursorUnits, succeededUnits, failedUnits, destroyedPositions, historyRecords, false);
    }

    private DestructionTaskState(
            List<BlockPos> targets, byte toolSlot, boolean toolProtectionEnabled,
            boolean selectedToolRequested, int workflowEntryId, int cursorUnits,
            int succeededUnits, int failedUnits, List<BlockPos> destroyedPositions,
            List<CompoundTag> historyRecords, boolean creativeOperation,
            HistoryBudget historyBudget, boolean historyCapacityReported) {
        validateScalars(targets, toolSlot, workflowEntryId, cursorUnits, succeededUnits,
                failedUnits, destroyedPositions, historyRecords);
        // 可信迁移只接受当前状态已经冻结的目标列表；这里复用引用，避免每片复制全量坐标。
        this.targets = targets;
        this.toolSlot = toolSlot;
        this.toolProtectionEnabled = toolProtectionEnabled;
        this.selectedToolRequested = selectedToolRequested;
        this.workflowEntryId = workflowEntryId;
        this.cursorUnits = cursorUnits;
        this.succeededUnits = succeededUnits;
        this.failedUnits = failedUnits;
        this.destroyedPositions = List.copyOf(destroyedPositions);
        this.historyRecords = List.copyOf(historyRecords);
        this.creativeOperation = creativeOperation;
        this.historyBudget = Objects.requireNonNull(historyBudget, "historyBudget");
        if (historyBudget.targetCount() != targets.size()
                || !historyBudget.accepts()
                || historyBudget.recordCount() != this.historyRecords.size()) {
            throw new IllegalArgumentException("trusted destruction history budget 无效");
        }
        this.historyCapacityReported = historyCapacityReported;
    }

    public List<BlockPos> targets() { return targets; }
    public byte toolSlot() { return toolSlot; }
    public boolean toolProtectionEnabled() { return toolProtectionEnabled; }
    public boolean selectedToolRequested() { return selectedToolRequested; }
    public int workflowEntryId() { return workflowEntryId; }
    public int cursorUnits() { return cursorUnits; }
    public int succeededUnits() { return succeededUnits; }
    public int failedUnits() { return failedUnits; }
    public List<BlockPos> destroyedPositions() { return destroyedPositions; }
    public boolean creativeOperation() { return creativeOperation; }
    public HistoryBudget historyBudget() { return historyBudget; }
    public boolean historyCapacityReported() { return historyCapacityReported; }

    public List<CompoundTag> historyRecords() {
        return copyTags(historyRecords);
    }

    /** 热路径只追加已冻结引用；对外读取仍通过 {@link #historyRecords()} 深复制。 */
    public void appendFrozenHistoryTo(List<CompoundTag> destination) {
        Objects.requireNonNull(destination, "destination").addAll(historyRecords);
    }

    public int totalUnits() {
        return targets.size();
    }

    public static int maxHistoryRecordsForTargets(int targetCount) {
        if (targetCount < 0 || targetCount > MAX_TARGETS) {
            throw new IllegalArgumentException("destruction target 数量越界");
        }
        return targetCount * MAX_HISTORY_RECORDS_PER_TARGET;
    }

    public boolean complete() {
        return cursorUnits >= targets.size();
    }

    /** 兼容旧调用；trusted transition 会根据冻结前缀增量计算预算。 */
    public DestructionTaskState advance(int nextCursor, int nextSucceeded, int nextFailed,
            List<BlockPos> nextDestroyedPositions, List<CompoundTag> nextHistoryRecords) {
        Objects.requireNonNull(nextDestroyedPositions, "nextDestroyedPositions");
        Objects.requireNonNull(nextHistoryRecords, "nextHistoryRecords");
        List<BlockPos> defensiveDestroyed = nextDestroyedPositions.stream()
                .map(position -> {
                    if (position == null) throw new IllegalArgumentException("destroyed position 不能为 null");
                    return position.immutable();
                })
                .toList();
        // 兼容旧调用属于外部边界；热路径使用带摘要的可信重载复用冻结 NBT。
        List<CompoundTag> defensiveHistory = copyTags(nextHistoryRecords);
        return advance(nextCursor, nextSucceeded, nextFailed, defensiveDestroyed,
                defensiveHistory, null, historyCapacityReported);
    }

    /**
     * 可信切片迁移。传入的 targets/history 前缀来自当前状态时只追加新增历史预算，
     * 不再进行全量目标去重或 NBT 深复制。
     */
    public DestructionTaskState advance(int nextCursor, int nextSucceeded, int nextFailed,
            List<BlockPos> nextDestroyedPositions, List<CompoundTag> nextHistoryRecords,
            HistoryBudget nextBudget, boolean nextHistoryCapacityReported) {
        Objects.requireNonNull(nextDestroyedPositions, "nextDestroyedPositions");
        Objects.requireNonNull(nextHistoryRecords, "nextHistoryRecords");
        HistoryBudget budget = nextBudget;
        if (budget == null) {
            boolean sharedPrefix = sharesHistoryPrefix(nextHistoryRecords);
            budget = sharedPrefix
                    ? historyBudget.append(nextHistoryRecords, historyRecords.size())
                    : HistoryBudget.forTargets(targets.size(), nextHistoryRecords);
        }
        return new DestructionTaskState(
                targets, toolSlot, toolProtectionEnabled, selectedToolRequested, workflowEntryId,
                nextCursor, nextSucceeded, nextFailed, nextDestroyedPositions, nextHistoryRecords,
                creativeOperation, budget, nextHistoryCapacityReported);
    }

    private boolean sharesHistoryPrefix(List<CompoundTag> candidate) {
        if (candidate.size() < historyRecords.size()) return false;
        for (int i = 0; i < historyRecords.size(); i++) {
            if (candidate.get(i) != historyRecords.get(i)) return false;
        }
        return true;
    }

    private static void validateScalars(
            List<BlockPos> targets, byte toolSlot, int workflowEntryId,
            int cursorUnits, int succeededUnits, int failedUnits,
            List<BlockPos> destroyedPositions, List<CompoundTag> historyRecords) {
        if (targets.isEmpty() || targets.size() > MAX_TARGETS) {
            throw new IllegalArgumentException("destruction targets 数量无效");
        }
        if (toolSlot < 0 || toolSlot > 8) throw new IllegalArgumentException("toolSlot 必须位于快捷栏");
        if (workflowEntryId < -1) throw new IllegalArgumentException("workflowEntryId 不能小于 -1");
        if (cursorUnits < 0 || succeededUnits < 0 || failedUnits < 0) {
            throw new IllegalArgumentException("destruction 计数不能为负数");
        }
        if (cursorUnits > targets.size()) throw new IllegalArgumentException("cursorUnits 不能超过目标数");
        if ((long) succeededUnits + failedUnits > cursorUnits) {
            throw new IllegalArgumentException("成功与失败数不能超过 cursorUnits");
        }
        if (destroyedPositions.size() != succeededUnits) {
            throw new IllegalArgumentException("destroyedPositions 数量必须等于 succeededUnits");
        }
        if (historyRecords.size() > maxHistoryRecordsForTargets(targets.size())) {
            throw new IllegalArgumentException("historyRecords 超过有界上限");
        }
    }

    private static List<BlockPos> immutableUniquePositions(List<BlockPos> positions, String field) {
        List<BlockPos> copy = new ArrayList<>(positions.size());
        Set<BlockPos> unique = new HashSet<>();
        for (BlockPos pos : positions) {
            if (pos == null) throw new IllegalArgumentException(field + " 不能包含 null");
            BlockPos immutable = pos.immutable();
            if (!unique.add(immutable)) throw new IllegalArgumentException(field + " 不能包含重复坐标");
            copy.add(immutable);
        }
        return List.copyOf(copy);
    }

    private static List<CompoundTag> copyTags(List<CompoundTag> tags) {
        List<CompoundTag> copy = new ArrayList<>(tags.size());
        for (CompoundTag tag : tags) {
            if (tag == null) throw new IllegalArgumentException("historyRecords 不能包含 null");
            copy.add(tag.copy());
        }
        return List.copyOf(copy);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DestructionTaskState that)) return false;
        return toolSlot == that.toolSlot
                && toolProtectionEnabled == that.toolProtectionEnabled
                && selectedToolRequested == that.selectedToolRequested
                && workflowEntryId == that.workflowEntryId
                && cursorUnits == that.cursorUnits
                && succeededUnits == that.succeededUnits
                && failedUnits == that.failedUnits
                && creativeOperation == that.creativeOperation
                && targets.equals(that.targets)
                && destroyedPositions.equals(that.destroyedPositions)
                && historyRecords.equals(that.historyRecords);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targets, toolSlot, toolProtectionEnabled, selectedToolRequested,
                workflowEntryId, cursorUnits, succeededUnits, failedUnits,
                destroyedPositions, historyRecords, creativeOperation);
    }

    @Override
    public String toString() {
        return "DestructionTaskState[targets=" + targets.size()
                + ", cursorUnits=" + cursorUnits
                + ", succeededUnits=" + succeededUnits
                + ", failedUnits=" + failedUnits
                + ", historyRecords=" + historyRecords.size() + "]";
    }
}
