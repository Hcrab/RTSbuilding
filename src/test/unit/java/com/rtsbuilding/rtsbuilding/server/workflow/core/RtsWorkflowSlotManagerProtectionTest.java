package com.rtsbuilding.rtsbuilding.server.workflow.core;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsWorkflowSlotManagerProtectionTest {
    @Test
    void allProtectedEntriesRejectAutoReplacement() {
        RtsWorkflowSlotManager slots = new RtsWorkflowSlotManager();
        for (int i = 0; i < RtsWorkflowSlotManager.MAX_SLOTS; i++) {
            addOccupiedEntry(slots).setProtectedWorkflow(true);
        }

        assertNull(slots.removeOldestReplaceableEntry(),
                "八个槽位都被保护时，新任务必须失败，绝不能自动淘汰任意 pinned 条目");
        assertEquals(RtsWorkflowSlotManager.MAX_SLOTS, slots.occupiedCount());
    }


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

    @Test
    void cancelledOrFailedEntryRemainsVisibleButIsNoLongerActive() {
        RtsWorkflowSlotManager slots = new RtsWorkflowSlotManager();
        RtsWorkflowEntry entry = addOccupiedEntry(slots);

        entry.markTerminal();

        assertTrue(entry.isOccupied(), "取消或失败状态仍应短暂占用一个可见面板槽位");
        assertTrue(entry.terminal());
        assertFalse(entry.hasActiveWorkflow(), "已结束任务不能继续被当成运行中任务");
        assertEquals(0, slots.activeCount());
        assertEquals(1, slots.occupiedCount());
    }

    @Test
    void terminalFlagSurvivesNbtRoundTrip() {
        RtsWorkflowSlotManager slots = new RtsWorkflowSlotManager();
        RtsWorkflowEntry entry = addOccupiedEntry(slots);
        entry.markTerminal();

        RtsWorkflowSlotManager loaded = RtsWorkflowSlotManager.loadFromNbt(slots.saveToNbt());
        RtsWorkflowEntry loadedEntry = loaded.findEntryById(entry.id());

        assertNotNull(loadedEntry);
        assertTrue(loadedEntry.terminal());
        assertFalse(loadedEntry.hasActiveWorkflow());
    }

    @Test
    void restoredEntryKeepsDurableIdAndAdvancesNextId() {
        RtsWorkflowSlotManager slots = new RtsWorkflowSlotManager();
        RtsWorkflowEntry restored = new RtsWorkflowEntry(42);
        restored.setType(RtsWorkflowType.QUICK_BUILD);

        assertTrue(slots.addRestoredEntry(restored));
        assertSame(restored, slots.findEntryById(42));

        RtsWorkflowEntry next = slots.addEntry(RtsWorkflowPriority.NORMAL);
        assertNotNull(next);
        assertEquals(43, next.id());
    }

    @Test
    void restoredEntryRejectsDuplicateId() {
        RtsWorkflowSlotManager slots = new RtsWorkflowSlotManager();
        RtsWorkflowEntry first = new RtsWorkflowEntry(7);
        first.setType(RtsWorkflowType.PLACE_BATCH);
        RtsWorkflowEntry duplicate = new RtsWorkflowEntry(7);
        duplicate.setType(RtsWorkflowType.QUICK_BUILD);

        assertTrue(slots.addRestoredEntry(first));
        assertFalse(slots.addRestoredEntry(duplicate));
        assertSame(first, slots.findEntryById(7));
    }

    private static RtsWorkflowEntry addOccupiedEntry(RtsWorkflowSlotManager slots) {
        RtsWorkflowEntry entry = slots.addEntry(RtsWorkflowPriority.NORMAL);
        assertNotNull(entry);
        entry.setType(RtsWorkflowType.PLACE_BATCH);
        return entry;
    }
}
