package com.rtsbuilding.rtsbuilding.server.task.mining;

import java.util.Objects;

/** detached mining 单个调度片的纯值输出。 */
public final class MiningSliceResult {
    private final MiningTaskState state;
    private final int processedUnits;
    private final int cursorUnits;
    private final int succeededUnits;
    private final int failedUnits;
    private final Outcome outcome;
    private final MiningWaitHint waitHint;

    public MiningSliceResult(MiningTaskState state, int processedUnits, int cursorUnits,
            int succeededUnits, int failedUnits, Outcome outcome, MiningWaitHint waitHint) {
        this.state = Objects.requireNonNull(state, "state");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        if (processedUnits < 0 || cursorUnits < 0 || succeededUnits < 0 || failedUnits < 0) {
            throw new IllegalArgumentException("mining slice delta 不能为负数");
        }
        if ((long) succeededUnits + failedUnits > cursorUnits) {
            throw new IllegalArgumentException("mining slice 结果不能超过 cursor 增量");
        }
        if ((outcome == Outcome.WAITING) != (waitHint != null)) {
            throw new IllegalArgumentException("WAITING 与 waitHint 必须同时出现");
        }
        this.processedUnits = processedUnits;
        this.cursorUnits = cursorUnits;
        this.succeededUnits = succeededUnits;
        this.failedUnits = failedUnits;
        this.waitHint = waitHint;
    }

    public MiningTaskState state() { return state; }
    public int processedUnits() { return processedUnits; }
    public int cursorUnits() { return cursorUnits; }
    public int succeededUnits() { return succeededUnits; }
    public int failedUnits() { return failedUnits; }
    public Outcome outcome() { return outcome; }
    public MiningWaitHint waitHint() { return waitHint; }

    public enum Outcome { CONTINUE, NEXT_TICK, WAITING, COMPLETE }
}
