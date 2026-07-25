package com.rtsbuilding.rtsbuilding.server.task.mining;

import java.util.Objects;

/** detached mining 单个调度片的纯值输出。 */
public record MiningSliceResult(
        MiningTaskState state,
        int processedUnits,
        int cursorUnits,
        int succeededUnits,
        int failedUnits,
        Outcome outcome,
        MiningWaitHint waitHint) {

    public MiningSliceResult {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(outcome, "outcome");
        if (processedUnits < 0 || cursorUnits < 0 || succeededUnits < 0 || failedUnits < 0) {
            throw new IllegalArgumentException("mining slice delta 不能为负数");
        }
        if ((long) succeededUnits + failedUnits > cursorUnits) {
            throw new IllegalArgumentException("mining slice 结果不能超过 cursor 增量");
        }
        if ((outcome == Outcome.WAITING) != (waitHint != null)) {
            throw new IllegalArgumentException("WAITING 与 waitHint 必须同时出现");
        }
    }

    public enum Outcome { CONTINUE, NEXT_TICK, WAITING, COMPLETE }
}
