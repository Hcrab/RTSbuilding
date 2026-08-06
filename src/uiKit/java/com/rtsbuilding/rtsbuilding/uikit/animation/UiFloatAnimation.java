package com.rtsbuilding.rtsbuilding.uikit.animation;

/** 由注入时钟驱动的确定性单值动画。 */
public final class UiFloatAnimation {
    private final UiClock clock;
    private double startValue;
    private double endValue;
    private long startMillis;
    private long durationMillis;
    private UiEasing easing;

    public UiFloatAnimation(UiClock clock, double initialValue) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
        this.startValue = initialValue;
        this.endValue = initialValue;
        this.startMillis = clock.nowMillis();
        this.easing = UiEasing.LINEAR;
    }

    public void animateTo(double newValue, long newDurationMillis, UiEasing newEasing) {
        if (newDurationMillis < 0L || newEasing == null) {
            throw new IllegalArgumentException("duration and easing must be valid");
        }
        startValue = value();
        endValue = newValue;
        startMillis = clock.nowMillis();
        durationMillis = newDurationMillis;
        easing = newEasing;
    }

    /**
     * 立即跳到新值并结束当前过渡，供受击闪烁这类每次触发都必须从满强度重播的效果使用。
     */
    public void snapTo(double newValue) {
        startValue = newValue;
        endValue = newValue;
        startMillis = clock.nowMillis();
        durationMillis = 0L;
        easing = UiEasing.LINEAR;
    }

    public double value() {
        if (durationMillis <= 0L) {
            return endValue;
        }
        double progress = (double) (clock.nowMillis() - startMillis) / (double) durationMillis;
        double eased = easing.apply(progress);
        return startValue + (endValue - startValue) * eased;
    }

    public boolean isFinished() {
        return durationMillis <= 0L || clock.nowMillis() - startMillis >= durationMillis;
    }
}
