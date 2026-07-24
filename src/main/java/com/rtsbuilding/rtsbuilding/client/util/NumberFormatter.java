package com.rtsbuilding.rtsbuilding.client.util;


public final class NumberFormatter {

    
    private static final long EFFECTIVELY_INFINITE_COUNT = Long.MAX_VALUE;

    

    
    private static String stripDecimalZeros(String formatted) {
        int dotIdx = formatted.indexOf('.');
        if (dotIdx < 0) return formatted;
        int end = formatted.length();
        while (end > dotIdx + 1 && formatted.charAt(end - 1) == '0') end--;
        if (end == dotIdx) return formatted.substring(0, dotIdx);
        return formatted.substring(0, end);
    }

    private NumberFormatter() {}

    

    
    public static String compactCount(long value) {
        long positive = Math.max(0L, value);
        if (positive >= EFFECTIVELY_INFINITE_COUNT) return "INF";
        if (positive < 1_000L) return Long.toString(positive);
        if (positive < 10_000L) return stripDecimalZeros(String.format("%.2f", positive / 1_000.0)) + "K";
        if (positive < 100_000L) return stripDecimalZeros(String.format("%.1f", positive / 1_000.0)) + "K";
        if (positive < 1_000_000L) return (positive / 1_000L) + "K";
        if (positive < 10_000_000L) return stripDecimalZeros(String.format("%.2f", positive / 1_000_000.0)) + "M";
        if (positive < 100_000_000L) return stripDecimalZeros(String.format("%.1f", positive / 1_000_000.0)) + "M";
        if (positive < 1_000_000_000L) return (positive / 1_000_000L) + "M";
        if (positive < 10_000_000_000L) return stripDecimalZeros(String.format("%.2f", positive / 1_000_000_000.0)) + "B";
        if (positive < 100_000_000_000L) return stripDecimalZeros(String.format("%.1f", positive / 1_000_000_000.0)) + "B";
        return (positive / 1_000_000_000L) + "B";
    }

    

    
    public static String compactFluidAmount(long milliBuckets) {
        long buckets = Math.max(0L, milliBuckets / 1000L);
        if (buckets >= 1_000_000L) return String.format("%.1fM B", buckets / 1_000_000.0);
        if (buckets >= 1_000L) return String.format("%.1fK B", buckets / 1_000.0);
        return buckets + " B";
    }
}
