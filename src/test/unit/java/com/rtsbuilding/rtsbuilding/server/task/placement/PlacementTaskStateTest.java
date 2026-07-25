package com.rtsbuilding.rtsbuilding.server.task.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementTaskStateTest {

    @Test
    void stateDefensivelyCopiesDefinitionAndHistory() {
        CompoundTag definition = definition();
        List<BlockPos> placed = new java.util.ArrayList<>(List.of(new BlockPos(1, 2, 3)));
        PlacementTaskState state = new PlacementTaskState(definition, 7, 4, 2, 1, 0, placed);

        definition.putString("mutated", "outside");
        placed.clear();
        CompoundTag leaked = state.definition();
        leaked.putString("mutated", "accessor");

        assertFalse(state.definition().contains("mutated"));
        assertEquals(List.of(new BlockPos(1, 2, 3)), state.placedPositions());
        assertFalse(state.complete());
    }

    @Test
    void advanceReturnsNewAuthoritativeSnapshotWithoutMutatingPrevious() {
        PlacementTaskState before = new PlacementTaskState(definition(), 7, 4, 1, 0, 0, List.of());
        PlacementTaskState after = before.advance(3, 1, 1, List.of(new BlockPos(4, 5, 6)));

        assertEquals(1, before.cursorUnits());
        assertEquals(3, after.cursorUnits());
        assertEquals(1, after.succeededUnits());
        assertEquals(1, after.failedUnits());
        assertEquals(PlacementResumePolicy.DEFAULT, after.resumePolicy());
    }

    @Test
    void resumePolicyChangesWithoutChangingAuthoritativeProgress() {
        PlacementTaskState before = new PlacementTaskState(definition(), 7, 4, 1, 0, 0, List.of());
        PlacementTaskState after = before.withResumePolicy(PlacementResumePolicy.SKIP_CONFLICTS);

        assertEquals(1, after.cursorUnits());
        assertEquals(0, after.failedUnits());
        assertEquals(PlacementResumePolicy.SKIP_CONFLICTS, after.resumePolicy());
        assertEquals(PlacementResumePolicy.DEFAULT, before.resumePolicy());
    }

    @Test
    void rejectsCountersThatCannotDescribeHistory() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlacementTaskState(definition(), 7, 4, 5, 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PlacementTaskState(definition(), 7, 4, 1, 1, 1, List.of(new BlockPos(0, 0, 0))));
        assertThrows(IllegalArgumentException.class,
                () -> new PlacementTaskState(definition(), 7, 4, 1, 0, 0, List.of(new BlockPos(0, 0, 0))));
    }

    @Test
    void completeDependsOnlyOnAuthoritativeCursor() {
        PlacementTaskState state = new PlacementTaskState(
                definition(), -1, 2, 2, 0, 0, List.of());
        assertTrue(state.complete());
    }

    private static CompoundTag definition() {
        CompoundTag definition = new CompoundTag();
        definition.putLongArray("positions", new long[]{1L, 2L, 3L, 4L});
        return definition;
    }
}
