package com.rtsbuilding.rtsbuilding.server.history;

import net.minecraft.core.BlockPos;
import java.util.Set;

/** 历史执行结果按精确位置推进，避免跳过中间格时误裁前缀。 */
public record HistoryExecutionResult(int executedCount, Set<BlockPos> completedPositions) {
    public HistoryExecutionResult {
        completedPositions = Set.copyOf(completedPositions);
    }
}
