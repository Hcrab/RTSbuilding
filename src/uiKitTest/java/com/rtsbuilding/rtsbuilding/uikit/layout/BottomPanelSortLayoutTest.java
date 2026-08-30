package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BottomPanelSortLayoutTest {
    @Test
    void fourControlsPreserveProductionGeometry() {
        BottomPanelSortLayout layout = BottomPanelSortLayout.resolve(100, 200);

        assertEquals(100, layout.cycleSort.x);
        assertEquals(220, layout.toggleDirection.y);
        assertEquals(142, layout.increaseHeight.x);
        assertEquals(220, layout.decreaseHeight.y);
        assertEquals(120, layout.labelX());
        assertEquals(206, layout.labelY());
    }

    @Test
    void hitTestingUsesHalfOpenButtonBoundsAndRejectsGaps() {
        BottomPanelSortLayout layout = BottomPanelSortLayout.resolve(100, 200);

        assertEquals(
                BottomPanelSortLayout.Control.CYCLE_SORT,
                layout.controlAt(100, 200));
        assertNull(layout.controlAt(116, 200));
        assertNull(layout.controlAt(100, 216));
        assertEquals(
                BottomPanelSortLayout.Control.TOGGLE_DIRECTION,
                layout.controlAt(115, 235));
        assertEquals(
                BottomPanelSortLayout.Control.INCREASE_HEIGHT,
                layout.controlAt(142, 200));
        assertEquals(
                BottomPanelSortLayout.Control.DECREASE_HEIGHT,
                layout.controlAt(157, 235));
    }
}
