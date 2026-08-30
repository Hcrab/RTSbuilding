package com.rtsbuilding.rtsbuilding.server.task;

/** 跨调度片记录“完整扫描一轮仍无进展”，避免大队列永远轮转。 */
public final class NoProgressCycleTracker {
    private int remainingInCycle;

    public void beginIfIdle(int candidates) {
        if (remainingInCycle <= 0) remainingInCycle = Math.max(0, candidates);
    }

    /** 记录一个仍需延期的候选；返回 true 表示完整无进展周期已经结束。 */
    public boolean deferredOne() {
        if (remainingInCycle <= 0) return true;
        remainingInCycle--;
        return remainingInCycle == 0;
    }

    /** 任意候选被真正解决后，从当前未解决集合开始新的观察周期。 */
    public void progressed(int unresolvedCandidates) {
        remainingInCycle = Math.max(0, unresolvedCandidates);
    }

    public void reset() {
        remainingInCycle = 0;
    }

    int remainingInCycle() {
        return remainingInCycle;
    }
}
