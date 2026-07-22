package com.rtsbuilding.rtsbuilding.uicore.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiGeometryTest {
    @Test
    void 半开边界只命中左上不命中右下() {
        UiRect rect = new UiRect(10, 20, 30, 40);
        assertTrue(rect.contains(10, 20));
        assertFalse(rect.contains(40, 60));
    }

    @Test
    void 零面积矩形不可命中() {
        assertFalse(new UiRect(0, 0, 0, 10).contains(0, 0));
    }

    @Test
    void 相邻边界不算重叠() {
        assertFalse(new UiRect(0, 0, 10, 10).intersects(new UiRect(10, 0, 5, 5)));
    }

    @Test
    void 交集返回真实公共区域() {
        assertEquals(new UiRect(5, 6, 5, 4),
                new UiRect(0, 0, 10, 10).intersection(new UiRect(5, 6, 10, 10)));
    }

    @Test
    void 内边距不会产生负尺寸() {
        assertEquals(UiRect.EMPTY.translate(8, 8),
                new UiRect(5, 5, 6, 6).inset(UiInsets.all(3)));
    }

    @Test
    void 超大矩形钳制到容器尺寸() {
        assertEquals(new UiRect(10, 20, 100, 50),
                new UiRect(-50, -50, 500, 500).clampWithin(new UiRect(10, 20, 100, 50)));
    }

    @Test
    void 内边距拒绝负数() {
        assertThrows(IllegalArgumentException.class, () -> new UiInsets(-1, 0, 0, 0));
    }

    @Test
    void 矩形拒绝非有限数() {
        assertThrows(IllegalArgumentException.class, () -> new UiRect(Double.NaN, 0, 1, 1));
    }
}
