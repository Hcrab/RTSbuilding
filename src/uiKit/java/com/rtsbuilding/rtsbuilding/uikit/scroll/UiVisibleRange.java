package com.rtsbuilding.rtsbuilding.uikit.scroll;

/** 长列表只扫描可见项和有界预加载项的索引范围。 */
public final class UiVisibleRange {
    private final int firstInclusive;
    private final int lastExclusive;

    private UiVisibleRange(int firstInclusive, int lastExclusive) {
        this.firstInclusive = firstInclusive;
        this.lastExclusive = lastExclusive;
    }

    public static UiVisibleRange calculate(int itemCount, double itemExtent,
                                           double viewportExtent, double offset,
                                           int overscanItems) {
        if (itemCount < 0 || !finite(itemExtent) || !finite(viewportExtent) || !finite(offset)
                || itemExtent <= 0.0D || viewportExtent < 0.0D
                || offset < 0.0D || overscanItems < 0) {
            throw new IllegalArgumentException("visible-range inputs must be non-negative and item extent positive");
        }
        int firstVisible = Math.min(itemCount, (int) Math.floor(offset / itemExtent));
        int visibleCount = (int) Math.min(itemCount,
                Math.min(Integer.MAX_VALUE, Math.ceil(viewportExtent / itemExtent) + 1.0D));
        int first = (int) Math.max(0L, (long) firstVisible - (long) overscanItems);
        int last = (int) Math.min((long) itemCount,
                (long) firstVisible + (long) visibleCount + (long) overscanItems);
        return new UiVisibleRange(first, Math.max(first, last));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public int getFirstInclusive() {
        return firstInclusive;
    }

    public int getLastExclusive() {
        return lastExclusive;
    }

    public int size() {
        return lastExclusive - firstInclusive;
    }
}
