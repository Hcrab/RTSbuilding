package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/** 浮窗水平滑块的轨道、滑块和值映射；输入边界采用半开矩形。 */
public final class WindowSliderLayout {
    public static final int TRACK_H = 4;
    public static final int TRACK_INSET = 1;
    public static final int KNOB_W = 8;
    public static final int KNOB_H = 12;

    private WindowSliderLayout() {
    }

    public static Geometry geometry(UiRect bounds, int minimum, int maximum, int value) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds");
        }
        int safeMaximum = Math.max(minimum, maximum);
        int safeValue = clamp(value, minimum, safeMaximum);
        int x = (int) bounds.getX();
        int y = (int) bounds.getY();
        int width = (int) bounds.getWidth();
        int height = (int) bounds.getHeight();
        int centerY = y + height / 2;
        int knobX = knobPosition(x, width, minimum, safeMaximum, safeValue);
        return new Geometry(
                bounds,
                new UiRect(x, centerY - TRACK_H / 2, width, TRACK_H),
                new UiRect(x + TRACK_INSET,
                        centerY - TRACK_H / 2 + TRACK_INSET,
                        Math.max(0, width - TRACK_INSET * 2),
                        Math.max(0, TRACK_H - TRACK_INSET * 2)),
                new UiRect(knobX - KNOB_W / 2, centerY - KNOB_H / 2,
                        KNOB_W, KNOB_H),
                safeValue);
    }

    public static int valueAt(UiRect bounds, int minimum, int maximum, double mouseX) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds");
        }
        int safeMaximum = Math.max(minimum, maximum);
        double fraction = (mouseX - bounds.getX())
                / Math.max(1.0D, bounds.getWidth());
        double clamped = Math.max(0.0D, Math.min(1.0D, fraction));
        return clamp((int) Math.round(minimum + clamped * (safeMaximum - minimum)),
                minimum, safeMaximum);
    }

    private static int knobPosition(int x, int width, int minimum, int maximum, int value) {
        if (maximum <= minimum) {
            return x;
        }
        double fraction = (value - minimum) / (double) (maximum - minimum);
        return x + (int) Math.round(fraction * width);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class Geometry {
        public final UiRect bounds;
        public final UiRect track;
        public final UiRect trackFill;
        public final UiRect knob;
        public final int value;

        private Geometry(UiRect bounds, UiRect track, UiRect trackFill,
                         UiRect knob, int value) {
            this.bounds = bounds;
            this.track = track;
            this.trackFill = trackFill;
            this.knob = knob;
            this.value = value;
        }
    }
}
