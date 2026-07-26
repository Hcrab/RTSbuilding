package com.rtsbuilding.rtsbuilding.client.util.animate;

@FunctionalInterface
public interface Easing {
    float apply(float t);

    Easing LINEAR = t -> t;
    Easing SMOOTHSTEP = t -> t * t * (3f - 2f * t);
    Easing EASE_OUT_QUAD = t -> { float u = 1f - t; return 1f - u * u; };
    Easing EASE_OUT_CUBIC = t -> { float u = 1f - t; return 1f - u * u * u; };
    Easing EASE_OUT_QUART = t -> { float u = 1f - t; return 1f - u * u * u * u; };
    Easing EASE_OUT_BACK = t -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    };
    Easing EASE_OUT_ELASTIC = t -> {
        if (t == 0f || t == 1f) return t;
        float c4 = (float) (2f * Math.PI / 3f);
        return (float) (Math.pow(2f, -10f * t) * Math.sin((t * 10f - 0.75f) * c4)) + 1f;
    };
    Easing EASE_OUT_BOUNCE = t -> bounceOut(t);
    Easing EASE_IN_OUT_CIRC = t -> t < 0.5f
        ? (1f - (float) Math.sqrt(1f - 4f * t * t)) / 2f
        : ((float) Math.sqrt(1f - (2f * t - 2f) * (2f * t - 2f)) + 1f) / 2f;
    Easing EASE_IN_OUT_BACK = t -> {
        float c1 = 1.70158f;
        float c2 = c1 * 1.525f;
        return t < 0.5f
            ? (float) (Math.pow(2f * t, 2) * ((c2 + 1f) * 2f * t - c2)) / 2f
            : (float) (Math.pow(2f * t - 2f, 2) * ((c2 + 1f) * (t * 2f - 2f) + c2) + 2f) / 2f;
    };
    Easing EASE_IN_ELASTIC = t -> {
        if (t == 0f || t == 1f) return t;
        float c4 = (float) (2f * Math.PI / 3f);
        return -(float) (Math.pow(2f, 10f * t - 10f) * Math.sin((t * 10f - 10.75f) * c4));
    };
    Easing EASE_IN_BOUNCE = t -> 1f - bounceOut(1f - t);

    private static float bounceOut(float t) {
        if (t < 1f / 2.75f) return 7.5625f * t * t;
        if (t < 2f / 2.75f) { t -= 1.5f / 2.75f; return 7.5625f * t * t + 0.75f; }
        if (t < 2.5f / 2.75f) { t -= 2.25f / 2.75f; return 7.5625f * t * t + 0.9375f; }
        t -= 2.625f / 2.75f;
        return 7.5625f * t * t + 0.984375f;
    }
}
