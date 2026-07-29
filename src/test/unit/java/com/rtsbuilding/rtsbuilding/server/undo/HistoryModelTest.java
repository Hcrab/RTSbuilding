package com.rtsbuilding.rtsbuilding.server.undo;

import com.rtsbuilding.rtsbuilding.common.RtsHistoryConstants;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.HistoryCapacityPolicy;
import com.rtsbuilding.rtsbuilding.server.history.HistoryEntry;
import com.rtsbuilding.rtsbuilding.server.history.HistoryOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryModelTest {
    @Test
    void operationModeAndSourceSlotAreFrozenAtRecordTime() {
        HistoryEntry entry = new HistoryEntry(
                HistoryOperation.SURVIVAL_BREAK,
                List.of(record(new BlockPos(1, 2, 3))),
                Direction.DOWN, Level.OVERWORLD, 6);

        assertEquals(HistoryOperation.SURVIVAL_BREAK, entry.getOperation());
        assertTrue(entry.isDestructive());
        assertFalse(entry.getOperation().creative());
        assertEquals(6, entry.getSourceSlot());
    }

    @Test
    void partialUndoRemovesExactPositionsInsteadOfListPrefix() {
        BlockPos first = new BlockPos(1, 2, 3);
        BlockPos middle = new BlockPos(2, 2, 3);
        BlockPos last = new BlockPos(3, 2, 3);
        HistoryEntry entry = new HistoryEntry(
                HistoryOperation.CREATIVE_BREAK,
                List.of(record(first), record(middle), record(last)),
                Direction.DOWN, Level.OVERWORLD, -1);

        HistoryEntry remaining = entry.remainingAfter(Set.of(middle));

        assertEquals(List.of(first, last),
                remaining.getBlocks().stream().map(HistoryBlockRecord::pos).toList());
        assertNull(entry.remainingAfter(Set.of(first, middle, last)));
        assertEquals(List.of(middle),
                entry.completedOnly(Set.of(middle)).getBlocks().stream()
                        .map(HistoryBlockRecord::pos).toList());
    }

    @Test
    void capacityRejectsWholeOversizedEntry() {
        HistoryBlockRecord record = record(BlockPos.ZERO);
        List<HistoryBlockRecord> oversized = Collections.nCopies(
                RtsHistoryConstants.MAX_BLOCKS_PER_ENTRY + 1, record);

        assertFalse(HistoryCapacityPolicy.accepts(oversized));
        assertTrue(HistoryCapacityPolicy.accepts(List.of(record)));
    }

    @Test
    void capacityRejectsWholeEntryWhenCompressedNbtIsTooLarge() {
        byte[] payload = new byte[4_096];
        new Random(42L).nextBytes(payload);
        CompoundTag nbt = new CompoundTag();
        nbt.putByteArray("payload", payload);

        assertFalse(HistoryCapacityPolicy.accepts(
                List.of(new HistoryBlockRecord(BlockPos.ZERO, null, nbt, null)), 10, 64));
    }

    @Test
    void snapshotDefensivelyCopiesBlockEntityNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("Items", 4);
        CompoundTag afterNbt = new CompoundTag();
        afterNbt.putInt("Items", 8);
        HistoryBlockRecord record = new HistoryBlockRecord(
                BlockPos.ZERO, null, nbt, null, afterNbt);
        nbt.putInt("Items", 99);
        afterNbt.putInt("Items", 199);

        assertEquals(4, record.blockEntityData().getInt("Items"));
        CompoundTag leakedCopy = record.blockEntityData();
        leakedCopy.putInt("Items", 123);
        assertEquals(4, record.blockEntityData().getInt("Items"));
        assertEquals(8, record.afterBlockEntityData().getInt("Items"));
        CompoundTag leakedAfterCopy = record.afterBlockEntityData();
        leakedAfterCopy.putInt("Items", 321);
        assertEquals(8, record.afterBlockEntityData().getInt("Items"));
    }

    private static HistoryBlockRecord record(BlockPos pos) {
        return new HistoryBlockRecord(pos, null, null, null);
    }
}
