package com.rtsbuilding.rtsbuilding.client.kernel;

/**
 * 统一时间源——每 tick 只调一次 {@link System#currentTimeMillis()}，
 * 所有 FeatureModule 共享同一个时间戳，消除 20+ 次/ tick 的重复系统调用。
 *
 * <p>{@link #tickIndex} 提供单调递增的 tick 序号，适合动画插值等无需
 * 绝对时间戳的场景，避免浮点累积误差。</p>
 */
public final class EpochClock {
    private long epochMs;
    private int tickIndex;

    /** 推进到下一 tick，返回当前 epoch 毫秒时间戳。 */
    public long tick() {
        this.epochMs = System.currentTimeMillis();
        this.tickIndex++;
        return this.epochMs;
    }

    /** 当前 tick 的系统毫秒时间戳（仅在同一 tick 内有效）。 */
    public long epochMs() {
        return this.epochMs;
    }

    /** 当前 tick 序号（从 0 开始单调递增）。 */
    public int tickIndex() {
        return this.tickIndex;
    }
}
