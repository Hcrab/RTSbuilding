package com.rtsbuilding.rtsbuilding.uikit.animation;

/**
 * 为滑块、旋钮等连续数值保存一个可插值的显示值。
 *
 * <p>调用方的逻辑值立即生效；本类只让屏幕上的位置平滑追上目标。第一次观察会直接
 * 对齐目标，避免窗口刚打开时控件从零位无意义地扫过；关闭 UI 动画后也会立即吸附。</p>
 */
public final class UiValueAnimation {
    private final UiFloatAnimation animation;
    private boolean initialized;
    private double target;

    public UiValueAnimation(UiClock clock) {
        this.animation = new UiFloatAnimation(clock, 0.0D);
    }

    public double update(double nextTarget, boolean animationsEnabled, long durationMillis) {
        if (durationMillis < 0L || !Double.isFinite(nextTarget)) {
            throw new IllegalArgumentException("target and duration must be valid");
        }
        if (!initialized || !animationsEnabled) {
            animation.snapTo(nextTarget);
            initialized = true;
        } else if (Double.compare(target, nextTarget) != 0) {
            animation.animateTo(nextTarget, durationMillis, UiEasing.EASE_OUT_CUBIC);
        }
        target = nextTarget;
        return animation.value();
    }

    public double value() {
        return animation.value();
    }
}
