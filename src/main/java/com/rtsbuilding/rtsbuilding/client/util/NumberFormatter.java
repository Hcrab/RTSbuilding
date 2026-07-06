package com.rtsbuilding.rtsbuilding.client.util;

/**
 * 数值格式化工具——提供紧凑数值、流体容量等格式化方法。
 */
public final class NumberFormatter {

    /** 被视为无限的超大数值 */
    private static final long EFFECTIVELY_INFINITE_COUNT = Long.MAX_VALUE;

    // ======================== 内部辅助 ========================

    /**
     * 去除格式化小数末尾的冗余零，保留必要的小数部分。
     * <p>如 "1.20" → "1.2"，"1.00" → "1"，"10.50" → "10.5"。</p>
     *
     * @param formatted 原始格式化字符串（如 "1.20"）
     * @return 去零后的字符串
     */
    private static String stripDecimalZeros(String formatted) {
        int dotIdx = formatted.indexOf('.');
        if (dotIdx < 0) return formatted;
        int end = formatted.length();
        while (end > dotIdx + 1 && formatted.charAt(end - 1) == '0') end--;
        if (end == dotIdx) return formatted.substring(0, dotIdx);
        return formatted.substring(0, end);
    }

    private NumberFormatter() {}

    // ======================== 物品数量紧凑显示 ========================

    /**
     * 将物品数量格式化为紧凑可读形式。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>&ge; INF → "INF"</li>
     *   <li>&lt; 1K → 原值</li>
     *   <li>&lt; 10K → "X.XXK"（去除末尾零）</li>
     *   <li>&lt; 100K → "X.XK"（去除末尾零）</li>
     *   <li>&lt; 1M → "XK"</li>
     *   <li>&lt; 10M → "X.XXM"</li>
     *   <li>&lt; 100M → "X.XM"</li>
     *   <li>&lt; 1B → "XM"</li>
     *   <li>&lt; 10B → "X.XXB"</li>
     *   <li>&lt; 100B → "X.XB"</li>
     *   <li>&ge; 100B → "XB"</li>
     * </ul>
     *
     * @param value 原始物品数量（负数视为 0）
     * @return 格式化后的字符串
     */
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

    // ======================== 流体容量紧凑显示 ========================

    /**
     * 将流体容量（毫桶）格式化为紧凑可读形式。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>&ge; 1M 桶 → "X.XM B"</li>
     *   <li>&ge; 1K 桶 → "X.XK B"</li>
     *   <li>&lt; 1K 桶 → "X B"</li>
     * </ul>
     *
     * @param milliBuckets 流体容量（毫桶）
     * @return 格式化后的字符串
     */
    public static String compactFluidAmount(long milliBuckets) {
        long buckets = Math.max(0L, milliBuckets / 1000L);
        if (buckets >= 1_000_000L) return String.format("%.1fM B", buckets / 1_000_000.0);
        if (buckets >= 1_000L) return String.format("%.1fK B", buckets / 1_000.0);
        return buckets + " B";
    }
}
