package com.rtsbuilding.rtsbuilding.uikit.scroll;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiScrollModelTest {
    @Test
    void 内容小于视口时不能滚动() {
        UiScrollModel model = new UiScrollModel(80, 100);
        assertFalse(model.canScroll());
        assertFalse(model.scrollBy(20));
    }

    @Test
    void 偏移被钳制到有效范围() {
        UiScrollModel model = new UiScrollModel(1000, 100);
        model.setOffset(9999);
        assertEquals(900, model.getOffset());
        model.setOffset(-50);
        assertEquals(0, model.getOffset());
    }

    @Test
    void 内容缩短时同步钳制旧偏移() {
        UiScrollModel model = new UiScrollModel(1000, 100);
        model.setOffset(800);
        model.setExtents(150, 100);
        assertEquals(50, model.getOffset());
    }

    @Test
    void 分页保留少量上下文() {
        UiScrollModel model = new UiScrollModel(1000, 100);
        model.page(1);
        assertEquals(90, model.getOffset());
    }

    @Test
    void 滚动条滑块按可见比例缩放() {
        UiScrollModel model = new UiScrollModel(1000, 100);
        assertEquals(20, model.thumbExtent(200, 12));
    }

    @Test
    void 滚动到底时滑块也到底() {
        UiScrollModel model = new UiScrollModel(1000, 100);
        model.setOffset(900);
        double thumb = model.thumbExtent(200, 12);
        assertEquals(200 - thumb, model.thumbOffset(200, thumb));
    }

    @Test
    void 两千项列表只返回可见范围和少量预加载() {
        UiVisibleRange range = UiVisibleRange.calculate(2000, 20, 300, 18000, 2);
        assertTrue(range.getFirstInclusive() > 0);
        assertTrue(range.getLastExclusive() < 2000);
        assertTrue(range.size() <= 20);
    }

    @Test
    void 空列表范围保持为空() {
        assertEquals(0, UiVisibleRange.calculate(0, 20, 300, 0, 2).size());
    }

    @Test
    void 可见范围拒绝非有限输入() {
        assertThrows(IllegalArgumentException.class,
                () -> UiVisibleRange.calculate(10, Double.NaN, 100, 0, 1));
    }
}
