package com.rtsbuilding.rtsbuilding.server.history;

import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 撤销或重做结果；按精确位置推进，避免跳过中间方块时误裁历史前缀。 */
public final class HistoryExecutionResult {
    private final int executedCount;
    private final Set<BlockPos> completedPositions;

    public HistoryExecutionResult(int executedCount, Set<BlockPos> completedPositions) {
        this.executedCount = Math.max(0, executedCount);
        this.completedPositions = Collections.unmodifiableSet(
                new LinkedHashSet<BlockPos>(completedPositions));
    }

    public int executedCount() { return executedCount; }
    public Set<BlockPos> completedPositions() { return completedPositions; }
}
