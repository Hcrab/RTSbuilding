package com.rtsbuilding.rtsbuilding.server.undo;

import com.rtsbuilding.rtsbuilding.server.history.HistoryRecordCodec;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBudget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryRecordCodecTest {
    @Test
    void compactHistoryRoundTripPreservesSparseFields() {
        CompoundTag first = record(11L, "stone");
        first.putInt("index", 42);
        CompoundTag second = record(22L, "oak_door");
        CompoundTag parent = new CompoundTag();

        HistoryRecordCodec.encode(parent, "history", "history_positions", List.of(first, second));

        assertEquals(List.of(first, second),
                HistoryRecordCodec.decode(parent, "history", "history_positions", 4, true));
        assertEquals(2, parent.getIntArray(HistoryRecordCodec.STATE_INDICES_KEY).length);
    }

    @Test
    void compactHistoryRoundTripPreservesCredentialColumns() {
        CompoundTag first = record(11L, "stone");
        first.put("credential_before", credential("minecraft:stone", 41L));
        CompoundTag second = record(22L, "oak_door");
        second.put("credential_after", credential("minecraft:oak_door", 42L));
        CompoundTag parent = new CompoundTag();

        HistoryRecordCodec.encode(parent, "history", "history_positions", List.of(first, second));

        assertTrue(parent.contains("history_credentials"));
        assertEquals(List.of(first, second),
                HistoryRecordCodec.decode(parent, "history", "history_positions", 4, true));
    }

    @Test
    void credentialColumnCountsGenerationAndTemplateCost() {
        CompoundTag plain = record(1L, "stone");
        CompoundTag withCredential = record(2L, "stone");
        withCredential.put("credential_before", credential("minecraft:stone", 99L));

        HistoryBudget plainBudget = HistoryBudget.forTargets(1, List.of(plain));
        HistoryBudget credentialBudget = HistoryBudget.forTargets(1, List.of(withCredential));

        assertTrue(credentialBudget.estimatedPayloadBytes() > plainBudget.estimatedPayloadBytes());
    }

    @Test
    void malformedCredentialColumnIsRejected() {
        CompoundTag parent = new CompoundTag();
        CompoundTag source = record(1L, "stone");
        source.put("credential_before", credential("minecraft:stone", 7L));
        HistoryRecordCodec.encode(parent, "history", "history_positions",
                List.of(source));
        CompoundTag columns = parent.getCompound("history_credentials");
        CompoundTag invalid = new CompoundTag();
        invalid.putIntArray("records", new int[]{0});
        invalid.putLongArray("generations", new long[]{0L});
        invalid.putIntArray("indices", new int[]{0});
        invalid.put("templates", new ListTag());
        columns.put("credential_before", invalid);

        assertThrows(IllegalArgumentException.class,
                () -> HistoryRecordCodec.decode(parent, "history", "history_positions", 1, true));
    }

    @Test
    void legacyInlineHistoryRemainsReadable() {
        CompoundTag parent = new CompoundTag();
        ListTag history = new ListTag();
        history.add(record(7L, "stone"));
        parent.put("history", history);

        assertEquals(List.of(record(7L, "stone")),
                HistoryRecordCodec.decode(parent, "history", "history_positions", 1, false));
    }

    @Test
    void compactHistoryRejectsMismatchedArrays() {
        CompoundTag parent = new CompoundTag();
        HistoryRecordCodec.encode(parent, "history", "history_positions", List.of(record(1L, "stone")));
        parent.putLongArray("history_positions", new long[0]);

        assertThrows(IllegalArgumentException.class,
                () -> HistoryRecordCodec.decode(parent, "history", "history_positions", 1, true));
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
