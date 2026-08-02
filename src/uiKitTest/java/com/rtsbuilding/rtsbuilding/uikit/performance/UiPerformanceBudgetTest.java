package com.rtsbuilding.rtsbuilding.uikit.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiPerformanceBudgetTest {
    @Test
    void 预算内统计通过() {
        UiRenderStats stats = stats();
        assertDoesNotThrow(() -> budget().verify(stats.snapshot()));
    }

    @Test
    void 九宫格超过固定预算立即失败() {
        UiRenderStats stats = stats();
        stats.addNineSliceQuads(1);
        assertThrows(IllegalStateException.class, () -> budget().verify(stats.snapshot()));
    }

    @Test
    void 静止帧布局重建立即失败() {
        UiRenderStats stats = stats();
        stats.addLayoutRebuilds(1);
        assertThrows(IllegalStateException.class, () -> budget().verify(stats.snapshot()));
    }

    @Test
    void 计数器拒绝负增量() {
        assertThrows(IllegalArgumentException.class, () -> new UiRenderStats().addPrimitives(-1));
    }

    @Test
    void reset清空全部统计() {
        UiRenderStats stats = stats();
        stats.reset();
        UiRenderStats.Snapshot snapshot = stats.snapshot();
        assertEquals(0, snapshot.primitives);
        assertEquals(0, snapshot.nineSliceQuads);
        assertEquals(0, snapshot.scannedItems);
    }

    private static UiRenderStats stats() {
        UiRenderStats stats = new UiRenderStats();
        stats.addPrimitives(120);
        stats.addNineSliceQuads(36);
        stats.addFlushes(1);
        stats.addScannedItems(40);
        stats.addSorts(1);
        return stats;
    }

    private static UiPerformanceBudget budget() {
        return new UiPerformanceBudget(150, 36, 1, 0, 64, 1);
    }
}
