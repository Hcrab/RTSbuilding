package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PluginManagementLayoutTest {
    @Test
    void desktopGeometryPreservesProductionPanelAndInventoryGrid() {
        PluginManagementLayout.Layout layout = PluginManagementLayout.resolve(1920, 1080);

        assertEquals(new PluginManagementLayout.Rect(745, 411, 430, 246), layout.panel);
        assertEquals(new PluginManagementLayout.Rect(757, 446, 184, 171), layout.installed);
        assertEquals(new PluginManagementLayout.Rect(953, 446, 210, 46), layout.install);
        assertEquals(new PluginManagementLayout.Rect(977, 520, 162, 72), layout.inventoryGrid);
        assertEquals(new PluginManagementLayout.Rect(1834, 1052, 74, 20), layout.back);
    }

    @Test
    void smallScreenKeepsAllInteractiveRegionsInsideTheScreen() {
        PluginManagementLayout.Layout layout = PluginManagementLayout.resolve(320, 240);

        assertEquals(300, layout.panel.width);
        assertEquals(214, layout.panel.height);
        assertTrue(layout.panel.x >= 0 && layout.panel.y >= 0);
        assertTrue(layout.inventoryGrid.right() <= 320);
        assertTrue(layout.back.right() <= 320 && layout.back.bottom() <= 240);
    }

    @Test
    void installedRowsClampScrollAndInventorySlotsShareOneGrid() {
        PluginManagementLayout.Layout layout = PluginManagementLayout.resolve(1920, 1080);
        PluginManagementLayout.InstalledRows rows =
                PluginManagementLayout.installedRows(layout, true, 20, 99);

        assertEquals(480, rows.firstRowY);
        assertEquals(5, rows.visibleRows);
        assertEquals(15, rows.maxScroll);
        assertEquals(15, rows.scroll);
        assertEquals(layout.inventoryGrid.x, PluginManagementLayout.inventorySlot(layout, 0).x);
        assertEquals(layout.inventoryGrid.right(), PluginManagementLayout.inventorySlot(layout, 8).right());
        assertThrows(IllegalArgumentException.class,
                () -> PluginManagementLayout.inventorySlot(layout, 36));
    }
}
