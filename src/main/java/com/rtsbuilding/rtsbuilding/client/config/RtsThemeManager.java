package com.rtsbuilding.rtsbuilding.client.config;

/**
 * HSV ↔ ARGB 颜色空间转换工具。
 *
 * <p>提供 hsvToArgb 纯工具方法，以及 HSV 状态的读取/设置。</p>
 */
public final class RtsThemeManager {

    // ======================== HSV 当前值 ========================

    /** 色相 0~360 */
    private static float hue = 210f;
    /** 饱和度 0~1 */
    private static float saturation = 0.25f;
    /** 明度 0~1 */
    private static float brightness = 0.90f;

    private RtsThemeManager() {}

    // ======================== 读写 HSV ========================

    public static float getHue() { return hue; }
    public static float getSaturation() { return saturation; }
    public static float getBrightness() { return brightness; }

    public static void setHsv(float h, float s, float v) {
        hue = clampHue(h);
        saturation = clamp01(s);
        brightness = clamp01(v);
    }

    // ======================== HSV ↔ ARGB 转换 ========================

    /**
     * HSV 转 ARGB 颜色值。
     *
     * @param alpha 透明度 0~255
     * @param h     色相 0~360
     * @param s     饱和度 0~1
     * @param v     明度 0~1
     * @return ARGB 颜色 int
     */
    public static int hsvToArgb(int alpha, float h, float s, float v) {
        float hueNorm = h / 60f;
        int sector = (int) Math.floor(hueNorm) % 6;
        if (sector < 0) sector += 6;
        float f = hueNorm - (int) Math.floor(hueNorm);
        if (f < 0) f += 1f;

        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));

        float r, g, b;
        switch (sector) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }

        int ri = clampByte(Math.round(r * 255f));
        int gi = clampByte(Math.round(g * 255f));
        int bi = clampByte(Math.round(b * 255f));
        return (alpha & 0xFF) << 24 | (ri & 0xFF) << 16 | (gi & 0xFF) << 8 | (bi & 0xFF);
    }

    // ======================== 工具 ========================

    private static float clampHue(float h) {
        h = h % 360f;
        return h < 0 ? h + 360f : h;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }

}
