package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorkflowResumeWindowLayoutTest {
    @Test
    void placementConflictGeometryPreservesProductionPixels() {
        WorkflowResumeWindowLayout.PlacementGeometry geometry =
                WorkflowResumeWindowLayout.placement(
                        0,
                        0,
                        258,
                        179,
                        true);

        assertEquals(8, geometry.x);
        assertEquals(8, geometry.y);
        assertEquals(242, geometry.innerWidth);
        assertEquals(170, geometry.valueX);
        assertEquals(new UiRect(8, 8, 16, 16), geometry.itemIcon);
        assertEquals(new UiRect(8, 30, 242, 1), geometry.topDivider);
        assertEquals(new UiRect(8, 112, 242, 1), geometry.summaryDivider);
        assertEquals(new UiRect(8, 151, 120, 20), geometry.primaryAction);
        assertEquals(new UiRect(130, 151, 120, 20), geometry.secondaryAction);
    }

    @Test
    void placementActionHitTestingIsHalfOpenAndDisabledAware() {
        WorkflowResumeWindowLayout.PlacementGeometry geometry =
                WorkflowResumeWindowLayout.placement(
                        0,
                        0,
                        258,
                        179,
                        true);

        assertEquals(
                WorkflowResumeWindowLayout.PlacementControl.RESUME_OR_SKIP,
                geometry.hitAt(8, 151, true));
        assertEquals(
                WorkflowResumeWindowLayout.PlacementControl.OVERWRITE,
                geometry.hitAt(130, 151, true));
        assertNull(geometry.hitAt(128, 151, true));
        assertNull(geometry.hitAt(250, 151, true));
        assertNull(geometry.hitAt(8, 171, true));
        assertNull(geometry.hitAt(8, 151, false));
    }

    @Test
    void blueprintGeometryOwnsColumnsRowsAndAction() {
        WorkflowResumeWindowLayout.BlueprintGeometry geometry =
                WorkflowResumeWindowLayout.blueprint(
                        0,
                        0,
                        278,
                        219,
                        8);

        assertEquals(8, geometry.x);
        assertEquals(262, geometry.innerWidth);
        assertEquals(140, geometry.requiredColumnX);
        assertEquals(200, geometry.availableColumnX);
        assertEquals(new UiRect(8, 26, 262, 1), geometry.headerDivider);
        assertEquals(new UiRect(8, 187, 262, 1), geometry.actionDivider);
        assertEquals(new UiRect(8, 191, 262, 20), geometry.action);
        assertEquals(8, geometry.rows.size());
        assertEquals(new UiRect(8, 48, 262, 18), geometry.rows.get(0).row);
        assertEquals(new UiRect(8, 48, 16, 16), geometry.rows.get(0).itemIcon);
        assertTrue(geometry.hitAction(8, 191, true));
        assertFalse(geometry.hitAction(270, 191, true));
        assertFalse(geometry.hitAction(8, 211, true));
        assertFalse(geometry.hitAction(8, 191, false));
    }

    @Test
    void blueprintScrollIsBoundedAtBothEnds() {
        assertEquals(
                1992,
                WorkflowResumeWindowLayout.clampBlueprintScroll(
                        5000,
                        2000));
        assertEquals(
                1992,
                WorkflowResumeWindowLayout.scrollBlueprint(
                        1992,
                        2000,
                        1.0D));
        assertEquals(
                1991,
                WorkflowResumeWindowLayout.scrollBlueprint(
                        1992,
                        2000,
                        -1.0D));
        assertEquals(
                0,
                WorkflowResumeWindowLayout.scrollBlueprint(
                        0,
                        4,
                        -1.0D));
    }
}
