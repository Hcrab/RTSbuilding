package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class FunnelBufferLayoutTest {
    @Test
    void matchesProductionRightEdgeAndRows() {
        assertEquals(1460, FunnelBufferLayout.panelX(1600));
        assertEquals(1532, FunnelBufferLayout.toggleX(1600));
        assertEquals(8, FunnelBufferLayout.visibleRows(196));
    }

    @Test
    void geometryOwnsToggleRowsSlotsAndPanelConsumption() {
        FunnelBufferLayout.Geometry geometry = FunnelBufferLayout.geometry(1600, 70, 196);

        assertEquals(1532.0D, geometry.toggle.getX());
        assertEquals(1460.0D, geometry.panel.getX());
        assertEquals(1464.0D, geometry.row(0).getX());
        assertEquals(1466.0D, geometry.slot(0).getX());
        assertEquals(FunnelBufferLayout.Target.TOGGLE,
                geometry.hitAt(1532, 76, 3, true).target);
        assertEquals(1, geometry.hitAt(1465, 134, 3, true).visibleRowIndex);
        assertEquals(FunnelBufferLayout.Target.PANEL,
                geometry.hitAt(1461, 100, 3, true).target);
    }

    @Test
    void hitTestingIsHalfOpenAndHiddenPanelDoesNotConsume() {
        FunnelBufferLayout.Geometry geometry = FunnelBufferLayout.geometry(1600, 70, 196);

        assertEquals(FunnelBufferLayout.Target.NONE,
                geometry.hitAt(1592, 76, 3, true).target);
        assertEquals(FunnelBufferLayout.Target.NONE,
                geometry.hitAt(1592, 96, 3, true).target);
        assertEquals(FunnelBufferLayout.Target.NONE,
                geometry.hitAt(1465, 112, 3, false).target);
        assertFalse(FunnelBufferLayout.geometry(1600, 70, 19).panelRenderable);
    }
}
