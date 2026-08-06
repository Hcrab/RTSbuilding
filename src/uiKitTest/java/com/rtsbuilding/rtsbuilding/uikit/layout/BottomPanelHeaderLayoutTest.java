package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTab;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BottomPanelHeaderLayoutTest {
    @Test
    void 宽屏头部统一排布页签状态和右侧入口() {
        BottomPanelHeaderLayout layout = BottomPanelHeaderLayout.resolve(
                10, 20, 800, 120,
                true, true, 60, true);

        assertEquals(3, layout.tabs.size());
        assertEquals(BottomBarUiTab.CREATIVE, layout.tabs.get(0).tab);
        assertEquals(48, layout.tabs.get(0).area.x);
        assertEquals(58, layout.tabs.get(0).area.width);
        assertEquals(BottomBarUiTab.STORAGE, layout.tabs.get(1).tab);
        assertEquals(110, layout.tabs.get(1).area.x);
        assertEquals(76, layout.tabs.get(1).area.width);
        assertEquals(BottomBarUiTab.BLUEPRINTS, layout.tabs.get(2).tab);
        assertEquals(190, layout.tabs.get(2).area.x);
        assertEquals(86, layout.tabs.get(2).area.width);
        assertEquals(286, layout.selectedStatus.x);
        assertEquals(96, layout.selectedStatus.width);
        assertEquals(774, layout.refresh.x);
        assertEquals(790, layout.guide.x);
        assertEquals(696, layout.plugin.x);
        assertTrue(layout.pluginVisible);
    }

    @Test
    void 权限直接决定可见页签而不是让预览端补画() {
        BottomPanelHeaderLayout layout = BottomPanelHeaderLayout.resolve(
                0, 0, 640, 100,
                false, false, 40, true);

        assertEquals(1, layout.tabs.size());
        assertEquals(BottomBarUiTab.STORAGE, layout.tabs.get(0).tab);
        assertEquals(38, layout.tabs.get(0).area.x);
        assertEquals(124, layout.selectedStatus.x);
    }

    @Test
    void 窄屏隐藏插件入口并允许状态区域收缩到零() {
        BottomPanelHeaderLayout layout = BottomPanelHeaderLayout.resolve(
                10, 20, 360, 100,
                true, true, 180, true);

        assertEquals(0, layout.selectedStatus.width);
        assertFalse(layout.pluginVisible);
        assertNull(layout.controlAt(
                layout.plugin.x + 1, layout.plugin.y + 1));
        assertSame(
                BottomPanelHeaderLayout.Control.REFRESH,
                layout.controlAt(
                        layout.refresh.x + 1, layout.refresh.y + 1));
    }

    @Test
    void 命中采用半开边界并拒绝页签间隙() {
        BottomPanelHeaderLayout layout = BottomPanelHeaderLayout.resolve(
                10, 20, 800, 120,
                true, true, 60, true);
        BottomPanelHeaderLayout.Area creative =
                layout.tabs.get(0).area;

        assertEquals(
                BottomBarUiTab.CREATIVE,
                layout.tabAt(creative.x, creative.y));
        assertNull(layout.tabAt(creative.right(), creative.y + 1));
        assertNull(layout.tabAt(creative.right() + 1, creative.y + 1));
        assertNull(layout.controlAt(
                layout.guide.right(), layout.guide.y + 1));
        assertTrue(layout.containsHeader(10, 20));
        assertTrue(layout.containsHeader(809, 37));
        assertFalse(layout.containsHeader(810, 20));
        assertFalse(layout.containsHeader(10, 38));
    }
}
