package com.rtsbuilding.rtsbuilding.client.presentation.panel.color;


public final class ColorMath {

    private ColorMath() {}

    
    public static float[] rgbToHsv(int argb) {
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
        float s = (max == 0.0f) ? 0.0f : delta / max;
        float v = max;

        if (delta != 0.0f) {
            if (max == rf) {
                h = ((gf - bf) / delta) % 6.0f;
            } else if (max == gf) {
                h = ((bf - rf) / delta) + 2.0f;
            } else {
                h = ((rf - gf) / delta) + 4.0f;
            }
            h *= 60.0f;
            if (h < 0) h += 360.0f;
        }

        return new float[]{h / 360.0f, s, v};
    }

    
    public static int hsvToRgb(float h, float s, float v) {
        float hueDeg = h * 360.0f;
        float c = v * s;
        float x = c * (1.0f - Math.abs((hueDeg / 60.0f) % 2.0f - 1.0f));
        float m = v - c;

        float rF, gF, bF;
        int hRegion = ((int) hueDeg) / 60;
        switch (hRegion) {
            case 0:
                rF = c; gF = x; bF = 0; break;
            case 1:
                rF = x; gF = c; bF = 0; break;
            case 2:
                rF = 0; gF = c; bF = x; break;
            case 3:
                rF = 0; gF = x; bF = c; break;
            case 4:
                rF = x; gF = 0; bF = c; break;
            default:
                rF = c; gF = 0; bF = x; break;
        }

        int r = (int) ((rF + m) * 255.0f);
        int g = (int) ((gF + m) * 255.0f);
        int b = (int) ((bF + m) * 255.0f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    
    public static boolean isDarkColor(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r * 0.299 + g * 0.587 + b * 0.114) < 128;
    }

    
    public static int blendGrayscale(int base, float grayscaleT) {
        float t = 1.0f - grayscaleT;
        int br = (base >> 16) & 0xFF;
        int bg = (base >> 8) & 0xFF;
        int bb = base & 0xFF;
        int r = (int) (br * t);
        int g = (int) (bg * t);
        int bn = (int) (bb * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bn;
    }
}
