package com.rtsbuilding.rtsbuilding.uikit.performance;

/** 对一帧统计执行明确上限检查，超过任一红线立即失败。 */
public final class UiPerformanceBudget {
    private final long maximumPrimitives;
    private final long maximumNineSliceQuads;
    private final long maximumFlushes;
    private final long maximumLayoutRebuilds;
    private final long maximumScannedItems;
    private final long maximumSorts;

    public UiPerformanceBudget(long maximumPrimitives, long maximumNineSliceQuads,
                               long maximumFlushes, long maximumLayoutRebuilds,
                               long maximumScannedItems, long maximumSorts) {
        this.maximumPrimitives = requireLimit(maximumPrimitives);
        this.maximumNineSliceQuads = requireLimit(maximumNineSliceQuads);
        this.maximumFlushes = requireLimit(maximumFlushes);
        this.maximumLayoutRebuilds = requireLimit(maximumLayoutRebuilds);
        this.maximumScannedItems = requireLimit(maximumScannedItems);
        this.maximumSorts = requireLimit(maximumSorts);
    }

    public void verify(UiRenderStats.Snapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        requireWithin("primitives", snapshot.primitives, maximumPrimitives);
        requireWithin("nineSliceQuads", snapshot.nineSliceQuads, maximumNineSliceQuads);
        requireWithin("flushes", snapshot.flushes, maximumFlushes);
        requireWithin("layoutRebuilds", snapshot.layoutRebuilds, maximumLayoutRebuilds);
        requireWithin("scannedItems", snapshot.scannedItems, maximumScannedItems);
        requireWithin("sorts", snapshot.sorts, maximumSorts);
    }

    private static long requireLimit(long value) {
        if (value < 0L) throw new IllegalArgumentException("budget limits must be non-negative");
        return value;
    }

    private static void requireWithin(String name, long actual, long maximum) {
        if (actual > maximum) {
            throw new IllegalStateException(name + " exceeded budget: " + actual + " > " + maximum);
        }
    }
}
