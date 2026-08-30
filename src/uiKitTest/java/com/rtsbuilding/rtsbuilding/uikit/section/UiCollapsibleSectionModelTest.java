package com.rtsbuilding.rtsbuilding.uikit.section;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiCollapsibleSectionModelTest {
    @Test
    void 折叠只保留标题高度() {
        UiCollapsibleSectionModel section = new UiCollapsibleSectionModel(18, 82, false);
        assertEquals(18, section.totalHeight(0), 0.0001);
        assertEquals(0, section.targetProgress(), 0.0001);
    }

    @Test
    void 展开目标与切换保持确定() {
        UiCollapsibleSectionModel section = new UiCollapsibleSectionModel(18, 82, false);
        section.toggle();
        assertTrue(section.isExpanded());
        assertEquals(1, section.targetProgress(), 0.0001);
    }

    @Test
    void 动画进度计算可见内容高度() {
        UiCollapsibleSectionModel section = new UiCollapsibleSectionModel(20, 80, true);
        assertEquals(60, section.totalHeight(0.5), 0.0001);
    }

    @Test
    void 动画进度会钳制() {
        UiCollapsibleSectionModel section = new UiCollapsibleSectionModel(20, 80, true);
        assertEquals(20, section.totalHeight(-1), 0.0001);
        assertEquals(100, section.totalHeight(2), 0.0001);
    }

    @Test
    void 内容重新测量后立即使用新高度() {
        UiCollapsibleSectionModel section = new UiCollapsibleSectionModel(20, 80, true);
        section.setContentHeight(120);
        assertEquals(140, section.totalHeight(1), 0.0001);
    }

    @Test
    void 拒绝负数和非有限高度() {
        assertThrows(IllegalArgumentException.class,
                () -> new UiCollapsibleSectionModel(-1, 10, false));
        assertThrows(IllegalArgumentException.class,
                () -> new UiCollapsibleSectionModel(10, Double.NaN, false));
    }
}
