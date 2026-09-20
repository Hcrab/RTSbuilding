package com.rtsbuilding.rtsbuilding.server.workflow.core;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationReason;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 工作流投影的缺料详情、旧 NBT 和终态清理回归。 */
class WorkflowReasonLifecycleTest {
    @Test
    void reasonAndMissingItemsSurviveNbtRoundTripWithStableId() {
        RtsWorkflowEntry entry = new RtsWorkflowEntry(7);
        entry.setType(RtsWorkflowType.PLACE_BATCH);
        entry.setTotalBlocks(12);
        entry.setReason(RtsOperationReason.RESOURCE_MISSING);
        entry.addMissingItems(List.of("minecraft:stone"));
        entry.setDetailMessage("missing:stone needed=1 available=0");

        RtsWorkflowEntry decoded = RtsWorkflowEntry.fromNbt(entry.toNbt());
        assertEquals(RtsOperationReason.RESOURCE_MISSING, decoded.reason());
        assertEquals(List.of("minecraft:stone"), decoded.missingItems());
        assertEquals("missing:stone needed=1 available=0", decoded.detailMessage());

        CompoundTag legacy = entry.toNbt();
        legacy.remove("reason_id");
        RtsWorkflowEntry legacyDecoded = RtsWorkflowEntry.fromNbt(legacy);
        assertEquals(RtsOperationReason.UNKNOWN, legacyDecoded.reason());
    }

    @Test
    void terminalCancellationClearsStaleWaitingProjection() {
        RtsWorkflowEntry entry = new RtsWorkflowEntry(8);
        entry.setType(RtsWorkflowType.QUICK_BUILD);
        entry.setReason(RtsOperationReason.RESOURCE_MISSING);
        entry.addMissingItems(List.of("minecraft:iron"));
        entry.setDetailMessage("missing:iron");

        entry.setReason(RtsOperationReason.CANCELLED);
        entry.clearMissingItems();
        entry.setDetailMessage("");
        entry.markTerminal();

        assertEquals(RtsOperationReason.CANCELLED, entry.snapshot().reason());
        assertTrue(entry.snapshot().missingItems().isEmpty());
        assertEquals("", entry.snapshot().detailMessage());
        assertTrue(entry.terminal());
        assertFalse(entry.hasActiveWorkflow());
    }
}
