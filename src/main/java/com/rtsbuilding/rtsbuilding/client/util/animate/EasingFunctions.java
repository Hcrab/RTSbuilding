package com.rtsbuilding.rtsbuilding.client.util.animate;


public enum EasingFunctions {

    
    LINEAR(t -> t),

    
    SMOOTHSTEP(t -> t * t * (3.0f - 2.0f * t)),

    
    EASE_OUT_QUAD(t -> 1.0f - (1.0f - t) * (1.0f - t)),

    
    EASE_OUT_CUBIC(t -> 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t)),

    
    EASE_OUT_QUART(t -> {
        float u = 1.0f - t;
        return 1.0f - u * u * u * u;
    }),

    
    EASE_OUT_BACK(t -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3) + c1 * (float) Math.pow(t - 1.0f, 2);
    }),

    
    EASE_OUT_ELASTIC(t -> {
        if (t == 0.0f || t == 1.0f) return t;
        float c4 = (float) (2.0f * Math.PI / 3.0f);
        return (float) (Math.pow(2.0f, -10.0f * t) * Math.sin((t * 10.0f - 0.75f) * c4) + 1.0f);
    }),

    
    EASE_OUT_BOUNCE(t -> bounceOut(t)),

    
    EASE_IN_OUT_CIRC(t -> t < 0.5f
            ? (1.0f - (float) Math.sqrt(1.0f - 4.0f * t * t)) / 2.0f
            : ((float) Math.sqrt(1.0f - (2.0f * t - 2.0f) * (2.0f * t - 2.0f)) + 1.0f) / 2.0f),

    
    EASE_IN_OUT_BACK(t -> {
        float c1 = 1.70158f;
        float c2 = c1 * 1.525f;
        return t < 0.5f
                ? (float) (Math.pow(2.0f * t, 2) * ((c2 + 1.0f) * 2.0f * t - c2)) / 2.0f
                : (float) (Math.pow(2.0f * t - 2.0f, 2) * ((c2 + 1.0f) * (t * 2.0f - 2.0f) + c2) + 2.0f) / 2.0f;
    }),

    
    EASE_IN_ELASTIC(t -> {
        if (t == 0.0f || t == 1.0f) return t;
        float c4 = (float) (2.0f * Math.PI / 3.0f);
        return -(float) (Math.pow(2.0f, 10.0f * t - 10.0f) * Math.sin((t * 10.0f - 10.75f) * c4));
    }),

    
    EASE_IN_BOUNCE(t -> 1.0f - bounceOut(1.0f - t));

    

    
    @FunctionalInterface
    public interface EasingFunction {
        
        float apply(float t);
    }

    

    private final EasingFunction function;

    EasingFunctions(EasingFunction function) {
        this.function = function;
    }

    
    public float apply(float t) {
        if (t <= 0.0f) return 0.0f;
        if (t >= 1.0f) return 1.0f;
        return function.apply(t);
    }

    

    
    private static float bounceOut(float t) {
        if (t < 1.0f / 2.75f) {
            return 7.5625f * t * t;
        } else if (t < 2.0f / 2.75f) {
            t -= 1.5f / 2.75f;
            return 7.5625f * t * t + 0.75f;
        } else if (t < 2.5f / 2.75f) {
            t -= 2.25f / 2.75f;
            return 7.5625f * t * t + 0.9375f;
        } else {
            t -= 2.625f / 2.75f;
            return 7.5625f * t * t + 0.984375f;
        }
    }
}
