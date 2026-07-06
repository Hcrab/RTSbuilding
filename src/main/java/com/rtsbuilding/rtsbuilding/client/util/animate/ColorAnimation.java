package com.rtsbuilding.rtsbuilding.client.util.animate;

/**
 * 颜色插值动画工具——在 HSV 颜色空间中进行平滑插值。
 *
 * <p><b>为什么要用 HSV 而非 RGB 插值？</b></p>
 * <ul>
 *   <li>RGB 线性插值：红→蓝会经过灰紫色，中间色饱和度丢失</li>
 *   <li>HSV 插值：沿色环走最短路径，始终保持鲜艳</li>
 * </ul>
 *
 * <p><b>用法：</b></p>
 * <pre>{@code
 * // 一次插值（不带动画器）
 * int result = ColorAnimation.lerpHSV(0xFF0000FF, 0xFFFF0000, 0.5f);
 *
 * // 配合 FloatAnimation 做动画
 * FloatAnimation anim = FloatAnimation.builder()
 *         .to(1.0f).duration(300).easing(EASE_OUT_QUART).build();
 * anim.start();
 * // 每帧：
 * int color = ColorAnimation.lerpHSV(startColor, endColor, anim.getValue());
 * }</pre>
 */
public final class ColorAnimation {

    private ColorAnimation() {}

    /**
     * 在 HSV 空间中对两个 ARGB 颜色进行插值。
     * <p>自动沿色环最短路径插值 hue，避免绕远路。</p>
     *
     * @param colorA 起始 ARGB 颜色
     * @param colorB 目标 ARGB 颜色
     * @param t      插值因子 [0, 1]（0=全部 colorA，1=全部 colorB）
     * @return 插值后的 ARGB 颜色
     */
    public static int lerpHSV(int colorA, int colorB, float t) {
        if (t <= 0.0f) return colorA;
        if (t >= 1.0f) return colorB;

        float[] hsvA = toHsv(colorA);
        float[] hsvB = toHsv(colorB);

        float hA = hsvA[0], sA = hsvA[1], vA = hsvA[2];
        float hB = hsvB[0], sB = hsvB[1], vB = hsvB[2];

        // 处理色相环最短路径
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

    /**
     * RGB 通道线性插值（不做 HSV 转换的简单版本）。
     *
     * @param colorA 起始 ARGB 颜色
     * @param colorB 目标 ARGB 颜色
     * @param t      插值因子 [0, 1]
     * @return 插值后的 ARGB 颜色
     */
    public static int lerpRGB(int colorA, int colorB, float t) {
        int a = lerpChannel(colorA >> 24 & 0xFF, colorB >> 24 & 0xFF, t);
        int r = lerpChannel(colorA >> 16 & 0xFF, colorB >> 16 & 0xFF, t);
        int g = lerpChannel(colorA >> 8 & 0xFF, colorB >> 8 & 0xFF, t);
        int b = lerpChannel(colorA & 0xFF, colorB & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 对颜色各 RGB 通道进行统一亮化/暗化。
     *
     * @param color  原始 ARGB 颜色
     * @param factor 缩放因子（&lt;1 暗化，&gt;1 亮化）
     * @return 缩放后的 ARGB 颜色（Alpha 保持不变）
     */
    public static int scale(int color, float factor) {
        int a = color >> 24 & 0xFF;
        int r = clamp((int) ((color >> 16 & 0xFF) * factor));
        int g = clamp((int) (((color >> 8) & 0xFF) * factor));
        int b = clamp((int) ((color & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ======================== 内部帮助方法 ========================

    private static int lerpChannel(int a, int b, float t) {
        return (int) (a + (b - a) * t);
    }

    private static int clamp(int value) {
        return Math.min(255, Math.max(0, value));
    }

    /**
     * 将 ARGB 颜色转换为 HSV 分量数组 [h, s, v]。
     * <p>h ∈ [0, 1), s ∈ [0, 1], v ∈ [0, 1]</p>
     */
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

    /**
     * 将 HSV 分量转换为 RGB 整型（0x00RRGGBB，无 Alpha）。
     */
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
