package com.rtsbuilding.rtsbuilding.client.util.animate;

import com.rtsbuilding.rtsbuilding.client.util.state.HoverSuppression;
import net.minecraft.Util;

public final class AnimFloat {

    private static boolean enabled = true;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean v) {
        enabled = v;
    }

    private float value;
    private float from;
    private float target;
    private long startTime;
    private long duration;
    private Easing easing;
    private boolean animating;
    private Runnable onComplete;

    private AnimFloat(float value, long duration, Easing easing) {
        this.value = value;
        this.target = value;
        this.from = value;
        this.duration = duration;
        this.easing = easing;
    }

    public static AnimFloat of(float initial, long durationMs, Easing easing) {
        return new AnimFloat(initial, durationMs, easing);
    }

    public static AnimFloat hover() {
        return new AnimFloat(0f, 120L, Easing.SMOOTHSTEP);
    }

    public static AnimFloat slide() {
        return new AnimFloat(0f, 200L, Easing.EASE_OUT_BACK);
    }

    public static AnimFloat expand() {
        return new AnimFloat(0f, 300L, Easing.EASE_OUT_QUART);
    }

    public static AnimFloat popup() {
        return new AnimFloat(0f, 250L, Easing.EASE_OUT_BACK);
    }

    public static AnimFloat fade() {
        return new AnimFloat(0f, 150L, Easing.LINEAR);
    }

    public AnimFloat onComplete(Runnable callback) {
        this.onComplete = callback;
        return this;
    }

    public void target(float target) {
        if (!enabled) {
            this.value = target;
            this.target = target;
            this.animating = false;
            return;
        }
        if (this.animating && Math.abs(this.target - target) < 0.001f) return;
        this.from = this.value;
        this.target = target;
        this.startTime = Util.getMillis();
        this.animating = true;
    }

    public float get() {
        if (!animating) return value;
        long elapsed = Util.getMillis() - startTime;
        if (elapsed >= duration) {
            value = target;
            animating = false;
            if (onComplete != null) {
                Runnable cb = onComplete;
                onComplete = null;
                cb.run();
            }
            return value;
        }
        float t = easing.apply((float) elapsed / duration);
        value = from + (target - from) * t;
        return value;
    }

    public float track(boolean condition) {
        target(condition && !HoverSuppression.floatingWindow().isSuppressed() ? 1f : 0f);
        return get();
    }

    public void snapTo(float value) {
        this.value = value;
        this.target = value;
        this.from = value;
        this.animating = false;
    }

    public boolean isAnimating() {
        return animating;
    }

    public float targetValue() {
        return target;
    }
}
