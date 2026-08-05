package com.rtsbuilding.rtsbuilding.util;

import java.util.Map;

/**
 * Shared count normalization for storage summaries, recent entries, and UI
 * payload totals. A count only becomes {@code Long.MAX_VALUE} when arithmetic
 * really saturates there; normal large storage networks should remain finite.
 */
public final class RtsCountUtil {
    private RtsCountUtil() {
    }

    public static void mergeCount(Map<String, Long> counts, String key, long amount) {
        if (counts == null || key == null || key.isBlank()) {
            return;
        }
        long sanitized = sanitizeCount(amount);
        if (sanitized <= 0L) {
            return;
        }
        counts.merge(key, sanitized, RtsCountUtil::saturatedAdd);
    }

    public static long saturatedAdd(long a, long b) {
        long left = sanitizeCount(a);
        long right = sanitizeCount(b);
        if (left == Long.MAX_VALUE || right == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    public static long sanitizeCount(long value) {
        return value <= 0L ? 0L : value;
    }
}
