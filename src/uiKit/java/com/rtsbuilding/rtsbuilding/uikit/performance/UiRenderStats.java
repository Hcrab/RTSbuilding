package com.rtsbuilding.rtsbuilding.uikit.performance;

/**
 * UI 帧性能计数器。
 *
 * <p>计数项对应抢滩红线：绘制原语、九宫格四边形、flush、布局重建、长列表
 * 扫描和排序。它不测量 GPU 时间，也不持有面板或业务数据。</p>
 */
public final class UiRenderStats {
    private long primitiveCount;
    private long nineSliceQuadCount;
    private long flushCount;
    private long layoutRebuildCount;
    private long scannedItemCount;
    private long sortCount;

    public void addPrimitives(long count) {
        primitiveCount = checkedAdd(primitiveCount, count);
    }

    public void addNineSliceQuads(long count) {
        nineSliceQuadCount = checkedAdd(nineSliceQuadCount, count);
    }

    public void addFlushes(long count) {
        flushCount = checkedAdd(flushCount, count);
    }

    public void addLayoutRebuilds(long count) {
        layoutRebuildCount = checkedAdd(layoutRebuildCount, count);
    }

    public void addScannedItems(long count) {
        scannedItemCount = checkedAdd(scannedItemCount, count);
    }

    public void addSorts(long count) {
        sortCount = checkedAdd(sortCount, count);
    }

    public Snapshot snapshot() {
        return new Snapshot(primitiveCount, nineSliceQuadCount, flushCount,
                layoutRebuildCount, scannedItemCount, sortCount);
    }

    public void reset() {
        primitiveCount = 0L;
        nineSliceQuadCount = 0L;
        flushCount = 0L;
        layoutRebuildCount = 0L;
        scannedItemCount = 0L;
        sortCount = 0L;
    }

    private static long checkedAdd(long current, long count) {
        if (count < 0L || Long.MAX_VALUE - current < count) {
            throw new IllegalArgumentException("counter increment must be non-negative and bounded");
        }
        return current + count;
    }

    public static final class Snapshot {
        public final long primitives;
        public final long nineSliceQuads;
        public final long flushes;
        public final long layoutRebuilds;
        public final long scannedItems;
        public final long sorts;

        private Snapshot(long primitives, long nineSliceQuads, long flushes,
                         long layoutRebuilds, long scannedItems, long sorts) {
            this.primitives = primitives;
            this.nineSliceQuads = nineSliceQuads;
            this.flushes = flushes;
            this.layoutRebuilds = layoutRebuilds;
            this.scannedItems = scannedItems;
            this.sorts = sorts;
        }
    }
}
