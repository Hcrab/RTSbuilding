package com.rtsbuilding.rtsbuilding.client.util.animate;

import java.util.function.DoubleConsumer;


public final class Tween {

    

    public enum State {
        
        IDLE,
        
        PLAYING,
        
        PAUSED,
        
        FINISHED
    }

    

    private final double from;
    private final double to;
    private final long durationMs;
    private final EasingFunctions easing;

    private double currentValue;
    private double startFrom;         
    private double endTo;            
    private State state = State.IDLE;
    private long startTime;
    private long pauseElapsed;       
    private DoubleConsumer onUpdate;
    private Runnable onComplete;
    private boolean completed;       
    private Tween chainedTween;      

    Tween(double from, double to, long durationMs, EasingFunctions easing) {
        this.from = from;
        this.to = to;
        this.durationMs = durationMs;
        this.easing = easing;
        this.currentValue = from;
        this.startFrom = from;
        this.endTo = to;
    }

    

    
    public Tween onUpdate(DoubleConsumer callback) {
        this.onUpdate = callback;
        return this;
    }

    
    public Tween onComplete(Runnable callback) {
        this.onComplete = callback;
        return this;
    }

    

    
    public Tween start() {
        if (state == State.FINISHED) return this;
        this.state = State.PLAYING;
        this.startTime = System.currentTimeMillis();
        this.startFrom = this.currentValue;
        this.completed = false;
        this.durationRemaining = 0;
        AnimationEngine.getInstance().register(this);
        return this;
    }

    
    public void pause() {
        if (state != State.PLAYING) return;
        this.state = State.PAUSED;
        this.pauseElapsed = System.currentTimeMillis() - this.startTime;
    }

    
    public void resume() {
        if (state != State.PAUSED) return;
        this.state = State.PLAYING;
        this.startTime = System.currentTimeMillis() - this.pauseElapsed;
    }

    
    public void retarget(double newTo) {
        this.endTo = newTo;
        this.startFrom = this.currentValue;
        if (state == State.PLAYING) {
            long elapsed = System.currentTimeMillis() - this.startTime;
            double progress = Math.min(1.0, (double) elapsed / durationMs);
            long remaining = (long) ((1.0 - progress) * durationMs);
            this.startTime = System.currentTimeMillis();
            this.durationRemaining = remaining;
        }
    }
    
    private long durationRemaining;

    
    public Tween chain(Tween next) {
        this.chainedTween = next;
        return next;
    }

    
    public void finish() {
        if (state == State.FINISHED) return;
        this.currentValue = this.endTo;
        this.state = State.FINISHED;
        notifyUpdate();
        notifyComplete();
        if (chainedTween != null) {
            chainedTween.start();
        }
    }

    
    public void snapTo(double value) {
        this.currentValue = value;
        this.startFrom = value;
        this.state = State.IDLE;
    }

    

    
    boolean tick() {
        if (state != State.PLAYING) return state != State.FINISHED;

        long now = System.currentTimeMillis();
        long elapsed = now - this.startTime;
        long effectiveDuration = (this.durationRemaining > 0) ? this.durationRemaining : this.durationMs;

        if (elapsed >= effectiveDuration) {
            this.currentValue = this.endTo;
            this.state = State.FINISHED;
            notifyUpdate();
            notifyComplete();
            if (chainedTween != null) {
                chainedTween.start();
            }
            return false;
        }

        double t = (double) elapsed / effectiveDuration;
        float easedT = easing.apply((float) t);
        this.currentValue = this.startFrom + (this.endTo - this.startFrom) * easedT;
        notifyUpdate();
        return true;
    }

    
    public double getValue() {
        return currentValue;
    }

    
    public float getFloat() {
        return (float) currentValue;
    }

    
    public boolean isRunning() {
        return state == State.PLAYING;
    }

    
    public boolean isFinished() {
        return state == State.FINISHED;
    }

    

    private void notifyUpdate() {
        if (onUpdate != null) {
            onUpdate.accept(currentValue);
        }
    }

    private void notifyComplete() {
        if (!completed && onComplete != null) {
            completed = true;
            onComplete.run();
        }
    }
}
