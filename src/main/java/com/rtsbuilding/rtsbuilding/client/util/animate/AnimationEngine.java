package com.rtsbuilding.rtsbuilding.client.util.animate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public final class AnimationEngine {

    private static final AnimationEngine INSTANCE = new AnimationEngine();

    
    private final List<Tween> activeTweens = new ArrayList<>();

    
    private final List<Tween> pendingAdd = new ArrayList<>();

    
    private boolean enabled = true;

    private AnimationEngine() {}

    
    public static AnimationEngine getInstance() {
        return INSTANCE;
    }

    

    
    public boolean isEnabled() {
        return enabled;
    }

    
    public void setEnabled(boolean v) {
        this.enabled = v;
        if (!v) {
            
            for (Tween t : activeTweens) {
                t.finish();
            }
            activeTweens.clear();
            pendingAdd.clear();
        }
    }

    

    
    public void tick() {
        if (!enabled) return;

        
        if (!pendingAdd.isEmpty()) {
            activeTweens.addAll(pendingAdd);
            pendingAdd.clear();
        }

        
        Iterator<Tween> it = activeTweens.iterator();
        while (it.hasNext()) {
            Tween tween = it.next();
            boolean alive = tween.tick();
            if (!alive) {
                it.remove();
            }
        }
    }

    
    public void flush() {
        for (Tween t : activeTweens) {
            t.finish();
        }
        activeTweens.clear();
        pendingAdd.clear();
    }

    
    public int activeCount() {
        return activeTweens.size();
    }

    

    
    public Tween tween(double from, double to, long durationMs) {
        return new Tween(from, to, durationMs, EasingFunctions.SMOOTHSTEP);
    }

    
    public Tween tween(double from, double to, long durationMs, EasingFunctions easing) {
        return new Tween(from, to, durationMs, easing);
    }

    

    
    public ParallelTween parallel(Tween... tweens) {
        return new ParallelTween(this, tweens);
    }

    
    public Tween sequence(Tween first, Tween... rest) {
        Tween current = first;
        for (Tween next : rest) {
            current.chain(next);
            current = next;
        }
        return first;
    }

    

    
    void register(Tween tween) {
        if (!enabled) {
            tween.snapTo(tween.getValue());
            tween.finish();
            return;
        }
        pendingAdd.add(tween);
    }

    

    
    public static final class ParallelTween {

        private final List<Tween> children;
        private Runnable onComplete;

        ParallelTween(AnimationEngine engine, Tween... tweens) {
            this.children = new ArrayList<>(List.of(tweens));
            
            for (Tween t : tweens) {
                engine.register(t);
            }
        }

        
        public ParallelTween start() {
            for (Tween t : children) {
                t.start();
            }
            return this;
        }

        
        public ParallelTween onComplete(Runnable callback) {
            Runnable wrapped = () -> {
                
                boolean allFinished = true;
                for (Tween t : children) {
                    if (!t.isFinished()) {
                        allFinished = false;
                        break;
                    }
                }
                if (allFinished && callback != null) {
                    callback.run();
                }
            };
            for (Tween t : children) {
                t.onComplete(wrapped);
            }
            return this;
        }
    }
}
