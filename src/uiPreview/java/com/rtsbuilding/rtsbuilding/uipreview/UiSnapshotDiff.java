package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

/** 逐像素比较两张快照，并生成红色差异热图；可显式屏蔽已知动画区域。 */
public final class UiSnapshotDiff {
    private UiSnapshotDiff() {
    }

    public static Result compare(BufferedImage expected, BufferedImage actual) {
        return compare(expected, actual, Collections.<UiRect>emptyList());
    }

    public static Result compare(BufferedImage expected, BufferedImage actual, List<UiRect> masks) {
        if (expected == null || actual == null || masks == null) {
            throw new IllegalArgumentException("images and masks must not be null");
        }
        if (expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight()) {
            throw new IllegalArgumentException("snapshot dimensions differ");
        }
        int width = expected.getWidth();
        int height = expected.getHeight();
        BufferedImage heatmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        long changed = 0L;
        int maximumChannelDelta = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isMasked(x, y, masks)) {
                    heatmap.setRGB(x, y, 0x00000000);
                    continue;
                }
                int expectedArgb = expected.getRGB(x, y);
                int actualArgb = actual.getRGB(x, y);
                if (expectedArgb != actualArgb) {
                    changed++;
                    int delta = maximumDelta(expectedArgb, actualArgb);
                    maximumChannelDelta = Math.max(maximumChannelDelta, delta);
                    int alpha = Math.max(80, delta);
                    heatmap.setRGB(x, y, (alpha << 24) | 0x00FF0000);
                } else {
                    heatmap.setRGB(x, y, 0x10000000);
                }
            }
        }
        return new Result(changed, (long) width * (long) height, maximumChannelDelta, heatmap);
    }

    private static boolean isMasked(int x, int y, List<UiRect> masks) {
        for (UiRect mask : masks) {
            if (mask.contains(x, y)) return true;
        }
        return false;
    }

    private static int maximumDelta(int left, int right) {
        int maximum = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            maximum = Math.max(maximum, Math.abs(((left >>> shift) & 0xFF) - ((right >>> shift) & 0xFF)));
        }
        return maximum;
    }

    public static final class Result {
        private final long changedPixels;
        private final long comparedPixels;
        private final int maximumChannelDelta;
        private final BufferedImage heatmap;

        private Result(long changedPixels, long comparedPixels, int maximumChannelDelta,
                       BufferedImage heatmap) {
            this.changedPixels = changedPixels;
            this.comparedPixels = comparedPixels;
            this.maximumChannelDelta = maximumChannelDelta;
            this.heatmap = heatmap;
        }

        public long changedPixels() {
            return changedPixels;
        }

        public long comparedPixels() {
            return comparedPixels;
        }

        public int maximumChannelDelta() {
            return maximumChannelDelta;
        }

        public BufferedImage heatmap() {
            return heatmap;
        }
    }
}
