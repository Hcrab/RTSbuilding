package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 生产工作流必须先命中最后绘制的 entryId，再用新快照重新确认资格。 */
final class WorkflowUiHitTargetTest {
    @Test
    void scrolledRowsResolveStableEntryIdInsteadOfLocalRowIndex() {
        List<WorkflowUiRow> drawnRows = new ArrayList<>();
        for (int index = 0; index < 18; index++) {
            drawnRows.add(row(1000 + index));
        }
        WorkflowWindowLayout.Geometry geometry =
                WorkflowWindowLayout.geometry(10, 30, 1, 17);

        WorkflowUiHitTarget.Target target = WorkflowUiHitTarget.resolve(
                geometry, drawnRows, 20, 35);

        assertTrue(target.hit());
        assertEquals(1017, target.entryId());
        assertEquals(17, target.rowIndex());
        assertTrue(target.isBody());
    }

    @Test
    void controlAndRemovedEntryNeverSwitchToReplacementAtTheSamePixel() {
        WorkflowUiRow drawn = row(41);
        WorkflowUiRow drawnBelow = row(99);
        WorkflowWindowLayout.Geometry geometry =
                WorkflowWindowLayout.geometry(10, 30, 2);

        WorkflowUiHitTarget.Target target = WorkflowUiHitTarget.resolve(
                geometry, Arrays.asList(drawn, drawnBelow), 170, 35);
        List<WorkflowUiRow> replacement = Arrays.asList(drawnBelow);

        assertEquals(41, target.entryId());
        assertEquals(WorkflowWindowLayout.Control.PROTECT, target.control());
        assertNull(WorkflowUiHitTarget.findByEntryId(replacement, target.entryId()));
        assertFalse(WorkflowUiHitTarget.findByEntryId(
                Arrays.asList(drawnBelow, drawn), target.entryId()) == null);
    }

    private static WorkflowUiRow row(int entryId) {
        return new WorkflowUiRow(entryId, "place_batch", "Batch", "1/2",
                1, 2, 0, 1, false, false, false, false);
    }
}
