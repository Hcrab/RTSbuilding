package com.rtsbuilding.rtsbuilding.server.task.mining;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.nbt.NBTTagCompound;
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
        NBTTagCompound history = historyTag();
        List<NBTTagCompound> histories = new ArrayList<>(List.of(history));
        MiningTaskState state = state(MiningTaskState.Mode.BATCH, targets, 2, 1, 1, 0, histories);

        targets.clear();
        history.setString("outside", "mutated");
        histories.clear();
        NBTTagCompound leaked = state.historyRecords().getFirst();
        leaked.setString("accessor", "mutated");

        assertEquals(List.of(new BlockPos(1, 2, 3)), state.remainingTargets());
        assertFalse(state.historyRecords().getFirst().hasKey("outside"));
        assertFalse(state.historyRecords().getFirst().hasKey("accessor"));
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
    void onlyBatchModeHasCrossedTheHeldFirstBlockBoundary() {
        MiningTaskState heldFirstBlock = state(
                MiningTaskState.Mode.PROGRESSIVE_SINGLE,
                List.of(new BlockPos(1, 2, 3)), 2, 0, 0, 0, List.of());
        MiningTaskState committedChain = heldFirstBlock.next(
                MiningTaskState.Mode.BATCH,
                List.of(new BlockPos(2, 2, 3)), 1, 1, 0, 0.0F, -1, List.of(historyTag()));

        assertFalse(heldFirstBlock.committedBatch());
        assertTrue(committedChain.committedBatch());
    }

    @Test
    void invalidProgressAndCounterRelationshipsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> state(MiningTaskState.Mode.BATCH, List.of(), 1, 2, 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MiningTaskState(
                        MiningTaskState.Mode.PROGRESSIVE_SINGLE, -1, List.of(),
                        1, 0, 0, 0, EnumFacing.DOWN, 0,
                        false, true, 0.0F, -1, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MiningTaskState(
                        MiningTaskState.Mode.BATCH, -1, List.of(new BlockPos(0, 0, 0)),
                        1, 0, 0, 0, EnumFacing.DOWN, 0,
                        false, true, 1.0F, -1, List.of()));
    }

    private static MiningTaskState state(
            MiningTaskState.Mode mode, List<BlockPos> targets,
            int total, int cursor, int succeeded, int failed, List<NBTTagCompound> history) {
        return new MiningTaskState(mode, -1, targets, total, cursor, succeeded, failed,
                EnumFacing.DOWN, 0, false, true, 0.0F, -1, history);
    }

    private static NBTTagCompound historyTag() {
        NBTTagCompound history = new NBTTagCompound();
        history.setLong("pos", 1L);
        history.setTag("state", new NBTTagCompound());
        return history;
    }
}
