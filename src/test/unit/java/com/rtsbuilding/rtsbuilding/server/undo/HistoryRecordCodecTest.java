package com.rtsbuilding.rtsbuilding.server.undo;

import com.rtsbuilding.rtsbuilding.server.history.HistoryRecordCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HistoryRecordCodecTest {
    @Test
    void compactHistoryRoundTripPreservesCollateralOrderAndPayload() {
        CompoundTag first = record(11L, "stone");
        first.putInt("index", 42);
        CompoundTag collateral = record(22L, "oak_door");
        CompoundTag parent = new CompoundTag();

        HistoryRecordCodec.encode(parent, "history", "history_positions", List.of(first, collateral));

        ListTag encoded = parent.getList("history", net.minecraft.nbt.Tag.TAG_COMPOUND);
        assertFalse(encoded.getCompound(0).contains("pos"));
        assertEquals(2, parent.getList(HistoryRecordCodec.STATES_KEY,
                net.minecraft.nbt.Tag.TAG_COMPOUND).size());
        assertEquals(2, parent.getIntArray(HistoryRecordCodec.STATE_INDICES_KEY).length);
        assertEquals(List.of(11L, 22L),
                java.util.Arrays.stream(parent.getLongArray("history_positions")).boxed().toList());
        assertEquals(List.of(first, collateral),
                HistoryRecordCodec.decode(parent, "history", "history_positions", 4, true));
    }

    @Test
    void legacyHistoryStillReadsInlinePositions() {
        CompoundTag parent = new CompoundTag();
        ListTag history = new ListTag();
        history.add(record(7L, "stone"));
        parent.put("history", history);

        assertEquals(List.of(record(7L, "stone")),
                HistoryRecordCodec.decode(parent, "history", "history_positions", 1, false));
    }

    @Test
    void compactHistoryRejectsMismatchedPositionArray() {
        CompoundTag parent = new CompoundTag();
        HistoryRecordCodec.encode(parent, "history", "history_positions", List.of(record(1L, "stone")));
        parent.putLongArray("history_positions", new long[0]);

        assertThrows(IllegalArgumentException.class,
                () -> HistoryRecordCodec.decode(parent, "history", "history_positions", 1, true));
    }

    @Test
    void compactHistoryRejectsOutOfRangeStateIndexAndDuplicateExtraIndex() {
        CompoundTag parent = new CompoundTag();
        HistoryRecordCodec.encode(parent, "history", "history_positions",
                List.of(record(1L, "stone"), record(2L, "stone")));
        parent.putIntArray(HistoryRecordCodec.STATE_INDICES_KEY, new int[] {0, 4});
        assertThrows(IllegalArgumentException.class,
                () -> HistoryRecordCodec.decode(parent, "history", "history_positions", 2, true));

        HistoryRecordCodec.encode(parent, "history", "history_positions",
                List.of(record(1L, "stone"), record(2L, "stone")));
        ListTag extras = parent.getList("history", net.minecraft.nbt.Tag.TAG_COMPOUND);
        extras.getCompound(1).getCompound("values").putString("marker2", "preserved");
        extras.getCompound(1).putInt(HistoryRecordCodec.EXTRA_INDEX_KEY, 0);
        assertThrows(IllegalArgumentException.class,
                () -> HistoryRecordCodec.decode(parent, "history", "history_positions", 2, true));
    }

    @Test
    void camelCaseCredentialColumnsRemainReadable() {
        CompoundTag source = record(1L, "stone");
        source.put("credentialBefore", credential("minecraft:stone", 17L));
        CompoundTag parent = new CompoundTag();

        HistoryRecordCodec.encode(parent, "history", "history_positions", List.of(source));
        CompoundTag columns = parent.getCompound("history_credentials");
        CompoundTag encoded = columns.contains("credentialBefore")
                ? columns.getCompound("credentialBefore")
                : columns.getCompound("credential_before");
        columns.remove("credentialBefore");
        columns.remove("credential_before");
        columns.put("credentialBefore", encoded);

        assertEquals(List.of(source),
                HistoryRecordCodec.decode(parent, "history", "history_positions", 1, true));
    }

    private static CompoundTag record(long pos, String block) {
        CompoundTag record = new CompoundTag();
        record.putLong("pos", pos);
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:" + block);
        record.put("state", state);
        record.putString("marker", "preserved");
        return record;
    }

    private static CompoundTag credential(String block, long generation) {
        CompoundTag credential = new CompoundTag();
        credential.putString("block", block);
        credential.putLong("generation", generation);
        credential.putByte("kind", (byte) 0);
        credential.putUUID("owner", UUID.randomUUID());
        return credential;
    }
}
