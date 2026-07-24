package com.rtsbuilding.rtsbuilding.client.util.animate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public final class AnimationGroup {

    
    public enum Mode {
        
        PARALLEL,
        
        SEQUENCE
    }

    
    public static AnimationGroup parallel(FloatAnimation... animations) {
        return new AnimationGroup(Mode.PARALLEL, Arrays.asList(animations));
    }

    
    public static AnimationGroup sequence(FloatAnimation... animations) {
        return new AnimationGroup(Mode.SEQUENCE, Arrays.asList(animations));
    }

    

    private final Mode mode;
    private final List<FloatAnimation> animations;
    private int currentIndex;
    private boolean started;

    private AnimationGroup(Mode mode, List<FloatAnimation> animations) {
        this.mode = mode;
        this.animations = new ArrayList<>(animations);
    }

    
    public void start() {
        if (animations.isEmpty()) return;
        this.started = true;
        switch (mode) {
            case PARALLEL -> animations.forEach(FloatAnimation::start);
            case SEQUENCE -> {
                currentIndex = 0;
                animations.get(0).start();
            }
        }
    }

    
    public void tick() {
        if (!started) return;

        switch (mode) {
            case PARALLEL -> animations.forEach(FloatAnimation::tick);
            case SEQUENCE -> {
                if (currentIndex >= animations.size()) return;
                FloatAnimation current = animations.get(currentIndex);
                current.tick();
                if (!current.isRunning()) {
                    currentIndex++;
                    if (currentIndex < animations.size()) {
                        animations.get(currentIndex).start();
                    }
                }
            }
        }
    }

    
    public boolean isFinished() {
        if (!started) return false;
        return switch (mode) {
            case PARALLEL -> animations.stream().noneMatch(FloatAnimation::isRunning);
            case SEQUENCE -> currentIndex >= animations.size();
        };
    }

    
    public float getValue(int index) {
        if (index < 0 || index >= animations.size()) return 0.0f;
        return animations.get(index).getValue();
    }

    
    public void snapToEnd() {
        switch (mode) {
            case PARALLEL -> animations.forEach(a -> a.snapTo(a.getToValue()));
            case SEQUENCE -> {
                for (FloatAnimation a : animations) {
                    a.snapTo(a.getToValue());
                }
                currentIndex = animations.size();
            }
        }
    }
}
