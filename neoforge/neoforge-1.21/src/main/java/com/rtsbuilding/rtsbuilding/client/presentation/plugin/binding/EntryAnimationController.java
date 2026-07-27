package com.rtsbuilding.rtsbuilding.client.presentation.plugin.binding;

import java.util.HashMap;
import java.util.Map;

public final class EntryAnimationController {

    private final Map<Integer, Float> entryContentY = new HashMap<>();
    private final Map<Integer, Float> barHoverProgress = new HashMap<>();
    private final Map<Integer, Boolean> barHoverState = new HashMap<>();

    private static final float ENTRY_SMOOTH_FACTOR = 0.15f;
    private static final float BAR_HOVER_SMOOTH_FACTOR = 0.28f;
    private static final float EPSILON = 0.001f;

    public void tick(int count) {
        if (entryContentY.size() > count) {
            entryContentY.keySet().removeIf(key -> key >= count);
        }
    }

    public float updateEntryAnimY(int origIdx, int targetBaseY) {
        Float animY = entryContentY.get(origIdx);
        if (animY == null) {
            animY = (float) targetBaseY;
        } else {
            animY += (targetBaseY - animY) * ENTRY_SMOOTH_FACTOR;
            if (Math.abs(animY - targetBaseY) < 0.5f) {
                animY = (float) targetBaseY;
            }
        }
        entryContentY.put(origIdx, animY);
        return animY;
    }

    public float tickBarHover(int barIndex, boolean shouldHover) {
        Float progress = barHoverProgress.get(barIndex);
        if (progress == null) {
            float val = shouldHover ? 1f : 0f;
            barHoverProgress.put(barIndex, val);
            barHoverState.put(barIndex, shouldHover);
            return val;
        }
        Boolean prevState = barHoverState.get(barIndex);
        if (prevState == null || prevState != shouldHover) {
            barHoverState.put(barIndex, shouldHover);
        }
        float target = shouldHover ? 1f : 0f;
        progress += (target - progress) * BAR_HOVER_SMOOTH_FACTOR;
        if (Math.abs(progress - target) < EPSILON) {
            progress = target;
        }
        barHoverProgress.put(barIndex, progress);
        return progress;
    }
}
