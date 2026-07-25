package com.rtsbuilding.rtsbuilding.server.task.destruction;

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
 * 只由此状态拥有。每个 slice 可以临时重建 DestructionJob，但临时对象不得放回 Session。</p>
 */
public record DestructionTaskState(
        List<BlockPos> targets,
        byte toolSlot,
        boolean toolProtectionEnabled,
        boolean selectedToolRequested,
        int workflowEntryId,
        int cursorUnits,
        int succeededUnits,
        int failedUnits,
        List<BlockPos> destroyedPositions,
        List<CompoundTag> historyRecords) {

    public static final int MAX_TARGETS = 98_304;
    public static final int MAX_HISTORY_RECORDS_PER_TARGET = 7;

    public DestructionTaskState {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(destroyedPositions, "destroyedPositions");
        Objects.requireNonNull(historyRecords, "historyRecords");
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
        long maxHistory = (long) targets.size() * MAX_HISTORY_RECORDS_PER_TARGET;
        if (historyRecords.size() > maxHistory) {
            throw new IllegalArgumentException("historyRecords 超过有界上限");
        }

        targets = immutableUniquePositions(targets, "targets");
        destroyedPositions = immutableUniquePositions(destroyedPositions, "destroyedPositions");
        Set<BlockPos> targetSet = new HashSet<>(targets);
        if (!targetSet.containsAll(destroyedPositions)) {
            throw new IllegalArgumentException("destroyedPositions 必须来自任务目标");
        }
        historyRecords = copyTags(historyRecords);
    }

    @Override
    public List<CompoundTag> historyRecords() {
        return copyTags(historyRecords);
    }

    public int totalUnits() {
        return targets.size();
    }

    public boolean complete() {
        return cursorUnits >= targets.size();
    }

    public DestructionTaskState advance(int nextCursor, int nextSucceeded, int nextFailed,
            List<BlockPos> nextDestroyedPositions, List<CompoundTag> nextHistoryRecords) {
        return new DestructionTaskState(
                targets, toolSlot, toolProtectionEnabled, selectedToolRequested, workflowEntryId,
                nextCursor, nextSucceeded, nextFailed, nextDestroyedPositions, nextHistoryRecords);
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
}
