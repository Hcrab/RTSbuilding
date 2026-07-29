package com.rtsbuilding.rtsbuilding.server.history;

import net.minecraft.core.BlockPos;

import java.util.Set;

/** 撤回执行结果；按具体位置推进，避免跳过中间格时误裁历史前缀。 */
public record HistoryExecutionResult(int executedCount, Set<BlockPos> completedPositions) {
    public HistoryExecutionResult {
        completedPositions = Set.copyOf(completedPositions);
    }
}
