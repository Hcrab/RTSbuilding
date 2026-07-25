package com.rtsbuilding.rtsbuilding.server.task.effect;

/**
 * Tick 末副作用提交的双重预算。
 *
 * <p>目标数上限约束最坏循环次数，纳秒预算约束已经变慢的页面、存档或网络投影不会继续
 * 吞掉整个 Tick。时间预算只能在两个目标之间停止，无法中断已经进入的单次提交。</p>
 */
public record RtsEffectCommitBudget(int maxTargets, long maxNanos) {
    public static final RtsEffectCommitBudget DEFAULT = new RtsEffectCommitBudget(32, 1_000_000L);

    public RtsEffectCommitBudget {
        if (maxTargets <= 0) {
            throw new IllegalArgumentException("副作用提交目标上限必须大于零");
        }
        if (maxNanos <= 0L) {
            throw new IllegalArgumentException("副作用提交纳秒预算必须大于零");
        }
    }
}
