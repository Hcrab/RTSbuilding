package com.rtsbuilding.rtsbuilding.server.task.placement;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationReason;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 覆盖单格结果到 slice 的原因映射，不启动世界或调度器。 */
class PlacementOutcomeTest {
    @Test
    void executionFactoriesKeepDistinctReasonsAndMissingItems() {
        assertTrue(PlacementExecutionResult.success().keepGoing());
        assertEquals(RtsOperationReason.SUCCESS, PlacementExecutionResult.success().reason());

        PlacementExecutionResult skipped = PlacementExecutionResult.skipped("occupied");
        assertTrue(skipped.keepGoing());
        assertEquals(RtsOperationReason.SKIPPED, skipped.reason());

        PlacementExecutionResult waiting = PlacementExecutionResult.waiting(
                RtsOperationReason.RESOURCE_MISSING, "missing:stone", List.of("minecraft:stone"));
        assertFalse(waiting.keepGoing());
        assertEquals(RtsOperationReason.RESOURCE_MISSING, waiting.reason());
        assertEquals(List.of("minecraft:stone"), waiting.missingItems());

        PlacementExecutionResult denied = PlacementExecutionResult.failed(
                RtsOperationReason.PERMISSION_DENIED, "claim");
        assertFalse(denied.keepGoing());
        assertEquals(RtsOperationReason.PERMISSION_DENIED, denied.reason());
        assertTrue(denied.missingItems().isEmpty());

        assertEquals(RtsOperationReason.TOOL_MISSING,
                PlacementExecutionResult.waiting(RtsOperationReason.TOOL_MISSING,
                        "tool_missing", List.of()).reason());
        assertEquals(RtsOperationReason.CHUNK_UNLOADED,
                PlacementExecutionResult.waiting(RtsOperationReason.CHUNK_UNLOADED,
                        "chunk_unloaded", List.of()).reason());
        assertEquals(RtsOperationReason.CONFIG_DISABLED,
                PlacementExecutionResult.failed(RtsOperationReason.CONFIG_DISABLED,
                        "remote_place_disabled").reason());
        assertEquals(RtsOperationReason.EXECUTION_ERROR,
                PlacementExecutionResult.failed(RtsOperationReason.EXECUTION_ERROR,
                        "third_party_failure").reason());
    }

    @Test
    void sliceCarriesWaitingReasonAndRejectsImpossibleCounters() {
        CompoundTag definition = new CompoundTag();
        definition.putString("itemId", "minecraft:stone");
        PlacementTaskState state = new PlacementTaskState(
                definition, 17, 10, 0, 0, 0, List.of());

        PlacementSliceResult result = new PlacementSliceResult(
                state, 1, 1, 0, 0,
                PlacementSliceResult.Outcome.WAITING_RESOURCE,
                RtsOperationReason.RESOURCE_MISSING,
                "missing:stone", List.of("minecraft:stone"));
        assertEquals(RtsOperationReason.RESOURCE_MISSING, result.reason());
        assertEquals(List.of("minecraft:stone"), result.missingItems());

        PlacementSliceResult chunk = new PlacementSliceResult(
                state, 0, 0, 0, 0,
                PlacementSliceResult.Outcome.WAITING_CHUNK,
                RtsOperationReason.CHUNK_UNLOADED, "chunk_unloaded", List.of());
        assertEquals(RtsOperationReason.CHUNK_UNLOADED, chunk.reason());

        assertThrows(IllegalArgumentException.class, () -> new PlacementSliceResult(
                state, 1, 1, 2, 0, PlacementSliceResult.Outcome.CONTINUE));
        assertThrows(IllegalArgumentException.class, () -> new PlacementSliceResult(
                state, -1, 0, 0, 0, PlacementSliceResult.Outcome.CONTINUE));
    }
}
