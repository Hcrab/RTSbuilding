package com.rtsbuilding.rtsbuilding.client.util.state;

import com.rtsbuilding.rtsbuilding.client.util.animate.EasingFunctions;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import net.minecraft.client.gui.GuiGraphics;


public class HoverStateManager {

    
    private static final long DURATION_MS = 120L;

    private boolean lastHovered;
    private float currentValue;
    private long animStartTime;
    private float animFromValue;
    private float animToValue;
    private boolean animating;

    public HoverStateManager() {
        this.currentValue = 0f;
    }

    

    
    private static final HoverSuppression FLOATING_WINDOW_SUPPRESSION
            = new HoverSuppression();

    
    public static HoverSuppression floatingWindowSuppression() {
        return FLOATING_WINDOW_SUPPRESSION;
    }

    

    
    public float update(boolean hovered) {
        boolean effective = hovered && !FLOATING_WINDOW_SUPPRESSION.isSuppressed();
        if (effective != this.lastHovered) {
            this.lastHovered = effective;
            this.animFromValue = this.currentValue;
            this.animToValue = effective ? 1.0f : 0.0f;
            this.animStartTime = System.currentTimeMillis();
            this.animating = true;
        }
        if (animating) {
            long elapsed = System.currentTimeMillis() - animStartTime;
            if (elapsed >= DURATION_MS) {
                currentValue = animToValue;
                animating = false;
            } else {
                float t = (float) elapsed / (float) DURATION_MS;
                float eased = EasingFunctions.SMOOTHSTEP.apply(t);
                currentValue = animFromValue + (animToValue - animFromValue) * eased;
            }
        }
        return currentValue;
    }

    
    public float getValue() {
        return currentValue;
    }

    
    public boolean isActive() {
        return this.lastHovered;
    }

    
    public boolean isAnimating() {
        return animating;
    }

    
    public void snapTo(boolean hovered) {
        this.lastHovered = hovered;
        this.currentValue = hovered ? 1.0f : 0.0f;
        this.animating = false;
    }

    
    public void renderCrossFade(GuiGraphics g, Runnable normal, Runnable hovered) {
        CrossFadeRenderer.render(g, this.currentValue, normal, hovered);
    }
}
