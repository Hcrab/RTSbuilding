package com.rtsbuilding.rtsbuilding.client.util.animate;


public final class ColorAnimation {

    private ColorAnimation() {}

    
    public static int lerpHSV(int colorA, int colorB, float t) {
        if (t <= 0.0f) return colorA;
        if (t >= 1.0f) return colorB;

        float[] hsvA = toHsv(colorA);
        float[] hsvB = toHsv(colorB);

        float hA = hsvA[0], sA = hsvA[1], vA = hsvA[2];
        float hB = hsvB[0], sB = hsvB[1], vB = hsvB[2];

        
        float dh = hB - hA;
        if (dh > 0.5f) dh -= 1.0f;
        else if (dh < -0.5f) dh += 1.0f;

        float h = (hA + dh * t + 1.0f) % 1.0f;
        float s = sA + (sB - sA) * t;
        float v = vA + (vB - vA) * t;

        int a = lerpChannel(colorA >> 24 & 0xFF, colorB >> 24 & 0xFF, t);
        int rgb = hsvToRgb(h, s, v);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    
    public static int lerpRGB(int colorA, int colorB, float t) {
        int a = lerpChannel(colorA >> 24 & 0xFF, colorB >> 24 & 0xFF, t);
        int r = lerpChannel(colorA >> 16 & 0xFF, colorB >> 16 & 0xFF, t);
        int g = lerpChannel(colorA >> 8 & 0xFF, colorB >> 8 & 0xFF, t);
        int b = lerpChannel(colorA & 0xFF, colorB & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    
    public static int scale(int color, float factor) {
        int a = color >> 24 & 0xFF;
        int r = clamp((int) ((color >> 16 & 0xFF) * factor));
        int g = clamp((int) (((color >> 8) & 0xFF) * factor));
        int b = clamp((int) ((color & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    

    private static int lerpChannel(int a, int b, float t) {
        return (int) (a + (b - a) * t);
    }

    private static int clamp(int value) {
        return Math.min(255, Math.max(0, value));
    }

    
    private static float[] toHsv(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        float rf = r / 255.0f;
        float gf = g / 255.0f;
        float bf = b / 255.0f;

        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        float h = 0.0f;
        float s = (max > 0.0f) ? delta / max : 0.0f;
        float v = max;

        if (delta > 0.0001f) {
            if (max == rf) {
                h = ((gf - bf) / delta) % 6.0f;
            } else if (max == gf) {
                h = (bf - rf) / delta + 2.0f;
            } else {
                h = (rf - gf) / delta + 4.0f;
            }
            h *= 60.0f;
            if (h < 0) h += 360.0f;
            h /= 360.0f;
        }

        return new float[] { h, s, v };
    }

    
    private static int hsvToRgb(float h, float s, float v) {
        int hi = (int) (h * 6.0f) % 6;
        float f = h * 6.0f - (int) (h * 6.0f);
        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float t = v * (1.0f - (1.0f - f) * s);

        float r, g, b;
        switch (hi) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }

        return ((int) (r * 255.0f) << 16)
             | ((int) (g * 255.0f) << 8)
             | (int) (b * 255.0f);
    }
}
