package com.rtsbuilding.rtsbuilding.server.task.destruction;

import java.util.Objects;

/** 单个 detached destruction 调度片的纯值输出。 */
public record DestructionSliceResult(
        DestructionTaskState state,
        int processedUnits,
        int cursorUnits,
        int succeededUnits,
        int failedUnits,
        Outcome outcome) {

    public DestructionSliceResult {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(outcome, "outcome");
        if (processedUnits < 0 || cursorUnits < 0 || succeededUnits < 0 || failedUnits < 0) {
            throw new IllegalArgumentException("slice delta 不能为负数");
        }
        if ((long) succeededUnits + failedUnits > cursorUnits) {
            throw new IllegalArgumentException("slice 成功与失败数不能超过 cursor 增量");
        }
    }

    public enum Outcome {
        CONTINUE,
        WAITING_RESOURCE,
        COMPLETE
    }
}
