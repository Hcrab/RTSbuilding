package com.rtsbuilding.rtsbuilding.server.task.destruction;

import com.rtsbuilding.rtsbuilding.server.task.DestructionTaskPayload;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.init.Blocks;
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
        DestructionTaskState state = stateWithOneDestroyedTarget();
        DestructionTaskPayload payload = new DestructionTaskPayload(
                owner, 0, 17, state);

        DestructionTaskPayload decoded = DestructionTaskCodec.decode(
                DestructionTaskCodec.encode(payload));

        assertEquals(owner, decoded.ownerId());
        assertEquals(0, decoded.dimension());
        assertEquals(17, decoded.workflowEntryId());
        assertEquals(state, decoded.state());
        assertTrue(decoded.state().complete());
    }

    @Test
    void constructorDefensivelyCopiesPositionsAndHistoryNbt() {
        List<BlockPos> targets = new ArrayList<>(List.of(new BlockPos(1, 2, 3)));
        NBTTagCompound history = history(new BlockPos(1, 2, 3));
        List<NBTTagCompound> histories = new ArrayList<>(List.of(history));
        DestructionTaskState state = new DestructionTaskState(
                targets, (byte) 2, true, false, 3,
                1, 1, 0, targets, histories);

        targets.clear();
        histories.clear();
        history.setString("mutated", "outside");
        NBTTagCompound exposed = state.historyRecords().getFirst();
        exposed.setString("mutated", "getter");

        assertEquals(1, state.targets().size());
        assertEquals(1, state.destroyedPositions().size());
        assertFalse(state.historyRecords().getFirst().hasKey("mutated"));
        assertThrows(UnsupportedOperationException.class,
                () -> state.targets().add(BlockPos.ORIGIN));
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
                UUID.randomUUID(), 0, 17, stateWithOneDestroyedTarget());
        NBTTagCompound wrongHistory = DestructionTaskCodec.encode(payload);
        NBTTagList strings = new NBTTagList();
        strings.appendTag(new NBTTagString("not-a-history-record"));
        wrongHistory.setTag("history", strings);
        assertThrows(IllegalArgumentException.class,
                () -> DestructionTaskCodec.decode(wrongHistory));

        NBTTagCompound wrongDimension = DestructionTaskCodec.encode(payload);
        wrongDimension.setString("dimension", "Minecraft:Overworld");
        assertThrows(IllegalArgumentException.class,
                () -> DestructionTaskCodec.decode(wrongDimension));
    }

    private static DestructionTaskState stateWithOneDestroyedTarget() {
        BlockPos target = new BlockPos(1, 2, 3);
        return new DestructionTaskState(
                List.of(target), (byte) 2, true, false, 17,
                1, 1, 0, List.of(target), List.of(history(target)));
    }

    private static NBTTagCompound history(BlockPos pos) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("pos", pos.toLong());
        NBTTagCompound state = new NBTTagCompound();
        state.setString("Name", "minecraft:stone");
        tag.setTag("state", state);
        return tag;
    }
}
