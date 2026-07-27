package com.rtsbuilding.rtsbuilding.server.task.destruction;

import java.util.Objects;

/** 单个 detached destruction 调度片的纯值输出。 */
public final class DestructionSliceResult {
    private final DestructionTaskState state;
    private final int processedUnits;
    private final int cursorUnits;
    private final int succeededUnits;
    private final int failedUnits;
    private final Outcome outcome;

    public DestructionSliceResult(DestructionTaskState state, int processedUnits,
            int cursorUnits, int succeededUnits, int failedUnits, Outcome outcome) {
        this.state = Objects.requireNonNull(state, "state");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        if (processedUnits < 0 || cursorUnits < 0 || succeededUnits < 0 || failedUnits < 0) {
            throw new IllegalArgumentException("slice delta 不能为负数");
        }
        if ((long) succeededUnits + failedUnits > cursorUnits) {
            throw new IllegalArgumentException("slice 成功与失败数不能超过 cursor 增量");
        }
        this.processedUnits = processedUnits;
        this.cursorUnits = cursorUnits;
        this.succeededUnits = succeededUnits;
        this.failedUnits = failedUnits;
    }

    public DestructionTaskState state() { return state; }
    public int processedUnits() { return processedUnits; }
    public int cursorUnits() { return cursorUnits; }
    public int succeededUnits() { return succeededUnits; }
    public int failedUnits() { return failedUnits; }
    public Outcome outcome() { return outcome; }

    public enum Outcome {
        CONTINUE,
        WAITING_RESOURCE,
        COMPLETE
    }
}
