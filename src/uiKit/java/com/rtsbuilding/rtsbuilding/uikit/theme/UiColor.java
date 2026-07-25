package com.rtsbuilding.rtsbuilding.uikit.theme;

/** 不依赖渲染 API 的 ARGB 颜色值。 */
public final class UiColor {
    private final int argb;

    public UiColor(int argb) {
        this.argb = argb;
    }

    public static UiColor opaque(int red, int green, int blue) {
        return argb(255, red, green, blue);
    }

    public static UiColor argb(int alpha, int red, int green, int blue) {
        requireChannel(alpha, "alpha");
        requireChannel(red, "red");
        requireChannel(green, "green");
        requireChannel(blue, "blue");
        return new UiColor(alpha << 24 | red << 16 | green << 8 | blue);
    }

    public int toArgb() {
        return argb;
    }

    public int alpha() {
        return argb >>> 24 & 0xFF;
    }

    public int red() {
        return argb >>> 16 & 0xFF;
    }

    public int green() {
        return argb >>> 8 & 0xFF;
    }

    public int blue() {
        return argb & 0xFF;
    }

    public UiColor withAlpha(int alpha) {
        return argb(alpha, red(), green(), blue());
    }

    /** 固定输入即可重复的线性通道插值；进度会钳制到 0..1。 */
    public static UiColor interpolate(UiColor from, UiColor to, double progress) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("colors must not be null");
        }
        if (Double.isNaN(progress) || Double.isInfinite(progress)) {
            throw new IllegalArgumentException("progress must be finite");
        }
        double t = Math.max(0.0D, Math.min(1.0D, progress));
        return argb(
                channel(from.alpha(), to.alpha(), t),
                channel(from.red(), to.red(), t),
                channel(from.green(), to.green(), t),
                channel(from.blue(), to.blue(), t));
    }

    private static int channel(int from, int to, double progress) {
        return (int) Math.round(from + (to - from) * progress);
    }

    private static void requireChannel(int channel, String name) {
        if (channel < 0 || channel > 255) {
            throw new IllegalArgumentException(name + " must be in 0..255");
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof UiColor && argb == ((UiColor) other).argb;
    }

    @Override
    public int hashCode() {
        return argb;
    }

    @Override
    public String toString() {
        return String.format("UiColor{%08X}", argb);
    }
}
