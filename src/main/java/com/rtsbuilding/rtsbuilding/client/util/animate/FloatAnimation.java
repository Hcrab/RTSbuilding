package com.rtsbuilding.rtsbuilding.client.util.animate;

import net.minecraft.Util;


public class FloatAnimation {

    
    private static boolean enabled = true;

    
    public static boolean isEnabled() {
        return enabled;
    }

    
    public static void setEnabled(boolean v) {
        enabled = v;
    }

    

    
    public static final class Builder {
        private float fromValue;
        private float toValue;
        private long durationMs = 200L;
        private EasingFunctions easing = EasingFunctions.SMOOTHSTEP;
        private Runnable onComplete;
        private boolean startFromCurrent;

        private Builder() {}

        
        public Builder from(float from) { this.fromValue = from; return this; }

        
        public Builder to(float to) { this.toValue = to; return this; }

        
        public Builder duration(long ms) { this.durationMs = ms; return this; }

        
        public Builder easing(EasingFunctions easing) { this.easing = easing; return this; }

        
        public Builder onComplete(Runnable onComplete) { this.onComplete = onComplete; return this; }

        
        public Builder startFromCurrent(boolean startFromCurrent) { this.startFromCurrent = startFromCurrent; return this; }

        
        public FloatAnimation build() { return new FloatAnimation(this); }
    }

    
    public static Builder builder() { return new Builder(); }

    

    private final long durationMs;
    private final EasingFunctions easing;
    private final Runnable onComplete;
    private final boolean startFromCurrent;

    private float fromValue;
    private float toValue;
    private float currentValue;
    private long startTime = -1L;

    private FloatAnimation(Builder builder) {
        this.fromValue = builder.fromValue;
        this.toValue = builder.toValue;
        this.durationMs = builder.durationMs;
        this.easing = builder.easing;
        this.onComplete = builder.onComplete;
        this.startFromCurrent = builder.startFromCurrent;
        this.currentValue = builder.fromValue;
    }

    

    
    public void start() {
        if (!enabled) {
            snapTo(toValue);
            return;
        }
        if (startFromCurrent) {
            this.fromValue = this.currentValue;
        }
        this.startTime = Util.getMillis();
    }

    
    public void start(float to) {
        this.toValue = to;
        start();
    }

    
    public void start(float from, float to) {
        this.fromValue = from;
        this.toValue = to;
        this.startTime = enabled ? Util.getMillis() : -1L;
        if (!enabled) {
            this.currentValue = to;
        }
    }

    
    public void tick() {
        if (this.startTime < 0) return;
        long elapsed = Util.getMillis() - this.startTime;
        if (elapsed >= this.durationMs) {
            this.currentValue = this.toValue;
            this.startTime = -1L;
            if (onComplete != null) onComplete.run();
            return;
        }
        float t = (float) elapsed / this.durationMs;
        float easedT = easing.apply(t);
        this.currentValue = this.fromValue + (this.toValue - this.fromValue) * easedT;
    }

    
    public float getValue() {
        return this.currentValue;
    }

    
    public boolean isRunning() {
        return this.startTime >= 0;
    }

    
    public void snapTo(float value) {
        this.currentValue = value;
        this.startTime = -1L;
    }

    
    public float getToValue() {
        return toValue;
    }

    
    public float getFromValue() {
        return fromValue;
    }
}
