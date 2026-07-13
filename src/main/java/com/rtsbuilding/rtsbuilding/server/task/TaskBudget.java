package com.rtsbuilding.rtsbuilding.server.task;

import java.util.function.LongSupplier;

/** 同时限制处理数量和墙钟时间的调度片预算。 */
public final class TaskBudget {
    private final int maxUnits;
    private final long deadlineNanos;
    private final LongSupplier nanoClock;

    TaskBudget(int maxUnits, long deadlineNanos, LongSupplier nanoClock) {
        this.maxUnits = Math.max(1, maxUnits);
        this.deadlineNanos = deadlineNanos;
        this.nanoClock = nanoClock;
    }

    public int maxUnits() {
        return maxUnits;
    }

    public boolean hasTime() {
        return nanoClock.getAsLong() < deadlineNanos;
    }

    public long remainingNanos() {
        return Math.max(0L, deadlineNanos - nanoClock.getAsLong());
    }
}
