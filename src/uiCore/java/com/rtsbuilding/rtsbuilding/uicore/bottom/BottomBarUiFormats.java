package com.rtsbuilding.rtsbuilding.uicore.bottom;

import java.util.Locale;

/** 生产与离屏共用的储存数量短写规则。 */
public final class BottomBarUiFormats {
    private static final long EFFECTIVELY_INFINITE_COUNT = Long.MAX_VALUE / 4L;
    private BottomBarUiFormats() {}

    public static String compactCount(long value) {
        long positive = Math.max(0L, value);
        if (positive >= EFFECTIVELY_INFINITE_COUNT) return "INF";
        if (positive < 1_000L) return Long.toString(positive);
        if (positive < 10_000L) return trim(String.format(Locale.ROOT, "%.2fK", positive / 1_000.0), "K");
        if (positive < 100_000L) return trim(String.format(Locale.ROOT, "%.1fK", positive / 1_000.0), "K");
        if (positive < 1_000_000L) return (positive / 1_000L) + "K";
        if (positive < 10_000_000L) return trim(String.format(Locale.ROOT, "%.2fM", positive / 1_000_000.0), "M");
        if (positive < 100_000_000L) return trim(String.format(Locale.ROOT, "%.1fM", positive / 1_000_000.0), "M");
        if (positive < 1_000_000_000L) return (positive / 1_000_000L) + "M";
        if (positive < 10_000_000_000L) return trim(String.format(Locale.ROOT, "%.2fB", positive / 1_000_000_000.0), "B");
        if (positive < 100_000_000_000L) return trim(String.format(Locale.ROOT, "%.1fB", positive / 1_000_000_000.0), "B");
        return (positive / 1_000_000_000L) + "B";
    }

    public static String compactFluidAmount(long milliBuckets) {
        long buckets = Math.max(0L, milliBuckets / 1000L);
        if (buckets >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM B", buckets / 1_000_000.0);
        if (buckets >= 1_000L) return String.format(Locale.ROOT, "%.1fK B", buckets / 1_000.0);
        return buckets + " B";
    }

    private static String trim(String value, String suffix) {
        String number = value.substring(0, value.length() - suffix.length());
        while (number.endsWith("0")) number = number.substring(0, number.length() - 1);
        if (number.endsWith(".")) number = number.substring(0, number.length() - 1);
        return number + suffix;
    }
}
