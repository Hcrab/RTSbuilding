package com.rtsbuilding.rtsbuilding.server.task.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningTaskStateTest {

    @Test
    void stateDefensivelyCopiesTargetsAndHistoryNbt() {
        List<BlockPos> targets = new ArrayList<>(List.of(new BlockPos(1, 2, 3)));
        CompoundTag history = historyTag();
        List<CompoundTag> histories = new ArrayList<>(List.of(history));
        MiningTaskState state = state(MiningTaskState.Mode.BATCH, targets, 2, 1, 1, 0, histories);

        targets.clear();
        history.putString("outside", "mutated");
        histories.clear();
        CompoundTag leaked = state.historyRecords().getFirst();
        leaked.putString("accessor", "mutated");

        assertEquals(List.of(new BlockPos(1, 2, 3)), state.remainingTargets());
        assertFalse(state.historyRecords().getFirst().contains("outside"));
        assertFalse(state.historyRecords().getFirst().contains("accessor"));
    }

    @Test
    void nextSnapshotOwnsCursorAndResultsWithoutChangingPrevious() {
        MiningTaskState before = state(
                MiningTaskState.Mode.BATCH,
                List.of(new BlockPos(1, 1, 1), new BlockPos(2, 2, 2)),
                2, 0, 0, 0, List.of());
        MiningTaskState after = before.next(
                MiningTaskState.Mode.BATCH,
                List.of(new BlockPos(2, 2, 2)),
                1, 1, 0, 0.0F, -1, List.of(historyTag()));

        assertEquals(0, before.cursorUnits());
        assertEquals(1, after.cursorUnits());
        assertEquals(1, after.succeededUnits());
        assertFalse(before.complete());
        assertFalse(after.complete());
    }

    @Test
    void emptyRemainingTargetsAreTerminalEvenForLegacyTotalGap() {
        MiningTaskState state = state(
                MiningTaskState.Mode.BATCH, List.of(), 4, 2, 1, 1, List.of());
        assertTrue(state.complete());
    }

    @Test
    void invalidProgressAndCounterRelationshipsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> state(MiningTaskState.Mode.BATCH, List.of(), 1, 2, 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MiningTaskState(
                        MiningTaskState.Mode.PROGRESSIVE_SINGLE, -1, List.of(),
                        1, 0, 0, 0, Direction.DOWN, 0,
                        false, true, 0.0F, -1, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MiningTaskState(
                        MiningTaskState.Mode.BATCH, -1, List.of(new BlockPos(0, 0, 0)),
                        1, 0, 0, 0, Direction.DOWN, 0,
                        false, true, 1.0F, -1, List.of()));
    }

    private static MiningTaskState state(
            MiningTaskState.Mode mode, List<BlockPos> targets,
            int total, int cursor, int succeeded, int failed, List<CompoundTag> history) {
        return new MiningTaskState(mode, -1, targets, total, cursor, succeeded, failed,
                Direction.DOWN, 0, false, true, 0.0F, -1, history);
    }

    private static CompoundTag historyTag() {
        CompoundTag history = new CompoundTag();
        history.putLong("pos", 1L);
        history.put("state", new CompoundTag());
        return history;
    }
}
