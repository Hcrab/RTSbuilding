package com.rtsbuilding.rtsbuilding.server.task.destruction;

import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.task.DestructionTaskPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestructionTaskStateTest {
    @Test
    void payloadRoundTripPreservesPureDetachedState() {
        UUID owner = UUID.randomUUID();
        DestructionTaskState state = stateWithOneDestroyedTarget(true);
        DestructionTaskPayload payload = new DestructionTaskPayload(
                owner, Level.OVERWORLD, 17, state);

        DestructionTaskPayload decoded = DestructionTaskCodec.decode(
                DestructionTaskCodec.encode(payload));

        assertEquals(owner, decoded.ownerId());
        assertEquals(Level.OVERWORLD, decoded.dimension());
        assertEquals(17, decoded.workflowEntryId());
        assertEquals(state, decoded.state());
        assertTrue(decoded.state().complete());
        assertTrue(decoded.state().creativeOperation());
        assertEquals(PlacedBlockTrackerData.CredentialKind.V2,
                PlacedBlockTrackerData.decodeSnapshot(
                        decoded.state().historyRecords().getFirst().getCompound("credentialAfter")).kind());
    }

    @Test
    void constructorDefensivelyCopiesPositionsAndHistoryNbt() {
        List<BlockPos> targets = new ArrayList<>(List.of(new BlockPos(1, 2, 3)));
        CompoundTag history = history(new BlockPos(1, 2, 3));
        List<CompoundTag> histories = new ArrayList<>(List.of(history));
        DestructionTaskState state = new DestructionTaskState(
                targets, (byte) 2, true, false, 3,
                1, 1, 0, targets, histories);

        targets.clear();
        histories.clear();
        history.putString("mutated", "outside");
        CompoundTag exposed = state.historyRecords().getFirst();
        exposed.putString("mutated", "getter");

        assertEquals(1, state.targets().size());
        assertEquals(1, state.destroyedPositions().size());
        assertFalse(state.historyRecords().getFirst().contains("mutated"));
        assertThrows(UnsupportedOperationException.class,
                () -> state.targets().add(BlockPos.ZERO));
    }

    @Test
    void invalidCountersDuplicatesAndForeignDestroyedTargetsFailClosed() {
        BlockPos first = new BlockPos(1, 2, 3);
        BlockPos second = new BlockPos(4, 5, 6);
        assertThrows(IllegalArgumentException.class, () -> new DestructionTaskState(
                List.of(first), (byte) 0, false, false, 1,
                2, 0, 0, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DestructionTaskState(
                List.of(first, first), (byte) 0, false, false, 1,
                0, 0, 0, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DestructionTaskState(
                List.of(first), (byte) 0, false, false, 1,
                1, 1, 0, List.of(second), List.of()));
    }

    @Test
    void codecRejectsWrongHistoryElementTypeAndNonCanonicalDimension() {
        DestructionTaskPayload payload = new DestructionTaskPayload(
                UUID.randomUUID(), Level.OVERWORLD, 17, stateWithOneDestroyedTarget(false));
        CompoundTag wrongHistory = DestructionTaskCodec.encode(payload);
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("not-a-history-record"));
        wrongHistory.put("history", strings);
        assertThrows(IllegalArgumentException.class,
                () -> DestructionTaskCodec.decode(wrongHistory));

        CompoundTag wrongDimension = DestructionTaskCodec.encode(payload);
        wrongDimension.putString("dimension", "Minecraft:Overworld");
        assertThrows(IllegalArgumentException.class,
                () -> DestructionTaskCodec.decode(wrongDimension));
    }

    @Test
    void legacyHistoryWithoutCredentialFieldsStillDecodes() {
        DestructionTaskPayload payload = new DestructionTaskPayload(
                UUID.randomUUID(), Level.OVERWORLD, 17, stateWithOneDestroyedTarget(false));
        CompoundTag encoded = DestructionTaskCodec.encode(payload);
        CompoundTag history = encoded.getList("history", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        history.remove("credentialBefore");
        history.remove("credentialAfter");

        DestructionTaskPayload decoded = DestructionTaskCodec.decode(encoded);

        assertFalse(decoded.state().historyRecords().getFirst().contains("credentialBefore"));
        assertFalse(decoded.state().historyRecords().getFirst().contains("credentialAfter"));
    }

    private static DestructionTaskState stateWithOneDestroyedTarget(boolean creativeOperation) {
        BlockPos target = new BlockPos(1, 2, 3);
        return new DestructionTaskState(
                List.of(target), (byte) 2, true, false, 17,
                1, 1, 0, List.of(target), List.of(history(target)), creativeOperation);
    }

    private static CompoundTag history(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.asLong());
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:stone");
        tag.put("state", state);
        tag.put("credentialBefore", PlacedBlockTrackerData.encodeSnapshot(
                PlacedBlockTrackerData.CredentialSnapshot.legacy(null, 5L)));
        tag.put("credentialAfter", PlacedBlockTrackerData.encodeSnapshot(
                new PlacedBlockTrackerData.CredentialSnapshot(
                        UUID.fromString("00000000-0000-0000-0000-000000000003"),
                        net.minecraft.resources.ResourceLocation.parse("minecraft:stone"), 6L,
                        PlacedBlockTrackerData.CredentialKind.V2)));
        return tag;
    }
}
