package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class WorkflowWindowLayoutTest {
    @Test
    void columnsAndHeightMatchProduction() {
        assertEquals(154, WorkflowWindowLayout.rowWidth());
        assertEquals(156, WorkflowWindowLayout.protectX(0));
        assertEquals(192, WorkflowWindowLayout.deleteX(0));
        assertEquals(77, WorkflowWindowLayout.totalHeight(20, 2));
    }

    @Test
    void geometryOwnsEveryRowAndActionColumn() {
        WorkflowWindowLayout.Geometry geometry =
                WorkflowWindowLayout.geometry(10, 30, 2);

        assertEquals(2, geometry.rows.size());
        assertEquals(new UiRect(10, 30, 154, 22),
                geometry.rows.get(0).row);
        assertEquals(new UiRect(14, 42, 146, 6),
                geometry.rows.get(0).progress);
        assertEquals(new UiRect(166, 30, 16, 22),
                geometry.rows.get(0).protect);
        assertEquals(new UiRect(184, 30, 16, 22),
                geometry.rows.get(0).action);
        assertEquals(new UiRect(202, 30, 16, 22),
                geometry.rows.get(0).delete);
        assertEquals(new UiRect(10, 52, 154, 22),
                geometry.rows.get(1).row);
    }

    @Test
    void hitTestingIsHalfOpenAndDoesNotTurnGapsIntoActions() {
        WorkflowWindowLayout.Geometry geometry =
                WorkflowWindowLayout.geometry(10, 30, 1);

        assertHit(geometry.hitAt(166, 30), 0,
                WorkflowWindowLayout.Control.PROTECT);
        assertHit(geometry.hitAt(184, 30), 0,
                WorkflowWindowLayout.Control.ACTION);
        assertHit(geometry.hitAt(202, 30), 0,
                WorkflowWindowLayout.Control.DELETE);
        assertNull(geometry.hitAt(165, 30));
        assertNull(geometry.hitAt(182, 30));
        assertNull(geometry.hitAt(200, 30));
        assertNull(geometry.hitAt(218, 30));
        assertNull(geometry.hitAt(166, 52));
        assertNull(geometry.hitAt(10, 30));
    }

    private static void assertHit(
            WorkflowWindowLayout.Hit hit,
            int rowIndex,
            WorkflowWindowLayout.Control control) {
        assertEquals(rowIndex, hit.rowIndex);
        assertEquals(control, hit.control);
    }
}
