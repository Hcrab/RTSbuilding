package com.rtsbuilding.rtsbuilding.server.workflow.core;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsWorkflowSlotManagerProtectionTest {

    @Test
    void protectedEntriesAreSkippedWhenAutoReplacing() {
        RtsWorkflowSlotManager slots = new RtsWorkflowSlotManager();
        RtsWorkflowEntry protectedEntry = addOccupiedEntry(slots);
        RtsWorkflowEntry firstReplaceable = addOccupiedEntry(slots);
        RtsWorkflowEntry secondReplaceable = addOccupiedEntry(slots);

        protectedEntry.setCreatedAtRaw(1L);
        firstReplaceable.setCreatedAtRaw(2L);
        secondReplaceable.setCreatedAtRaw(3L);
        protectedEntry.setProtectedWorkflow(true);

        RtsWorkflowEntry removed = slots.removeOldestReplaceableEntry();

        assertSame(firstReplaceable, removed);
        assertTrue(slots.findEntryById(protectedEntry.id()).protectedWorkflow());
        assertEquals(-1, slots.findIndexByEntryId(firstReplaceable.id()));
        assertNotNull(slots.findEntryById(secondReplaceable.id()));
    }

    @Test
    void protectedEntriesAreSkippedByStaleCleanup() {
        RtsWorkflowSlotManager slots = new RtsWorkflowSlotManager();
        RtsWorkflowEntry protectedEntry = addOccupiedEntry(slots);
        RtsWorkflowEntry replaceableEntry = addOccupiedEntry(slots);
        protectedEntry.setProtectedWorkflow(true);

        List<Integer> removed = slots.removeStaleEntries(-1L);

        assertEquals(List.of(replaceableEntry.id()), removed);
        assertNotNull(slots.findEntryById(protectedEntry.id()));
        assertEquals(-1, slots.findIndexByEntryId(replaceableEntry.id()));
    }

    @Test
    void protectedFlagSurvivesNbtRoundTrip() {
        RtsWorkflowSlotManager slots = new RtsWorkflowSlotManager();
        RtsWorkflowEntry entry = addOccupiedEntry(slots);
        entry.setProtectedWorkflow(true);

        RtsWorkflowSlotManager loaded = RtsWorkflowSlotManager.loadFromNbt(slots.saveToNbt());
        RtsWorkflowEntry loadedEntry = loaded.findEntryById(entry.id());

        assertNotNull(loadedEntry);
        assertTrue(loadedEntry.protectedWorkflow());
        assertFalse(loaded.removeStaleEntries(-1L).contains(entry.id()));
    }

    private static RtsWorkflowEntry addOccupiedEntry(RtsWorkflowSlotManager slots) {
        RtsWorkflowEntry entry = slots.addEntry(RtsWorkflowPriority.NORMAL);
        assertNotNull(entry);
        entry.setType(RtsWorkflowType.PLACE_BATCH);
        return entry;
    }
}
