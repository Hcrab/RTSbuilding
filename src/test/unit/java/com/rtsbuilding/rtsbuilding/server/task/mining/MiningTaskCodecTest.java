package com.rtsbuilding.rtsbuilding.server.task.mining;

import com.rtsbuilding.rtsbuilding.server.task.MiningTaskPayload;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MiningTaskCodecTest {

    @Test
    void roundTripPreservesDetachedMiningSnapshot() {
        UUID owner = UUID.randomUUID();
        int dimension = 0;
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, 9,
                List.of(new BlockPos(4, 5, 6)),
                3, 2, 1, 1, EnumFacing.NORTH, 4,
                true, false, 0.0F, -1, List.of(historyTag()));
        MiningTaskPayload payload = new MiningTaskPayload(owner, dimension, 9, state);

        MiningTaskPayload decoded = MiningTaskCodec.decode(MiningTaskCodec.encode(payload));

        assertEquals(owner, decoded.ownerId());
        assertEquals(dimension, decoded.dimension());
        assertEquals(9, decoded.workflowEntryId());
        assertEquals(2, decoded.state().cursorUnits());
        assertEquals(EnumFacing.NORTH, decoded.state().face());
        assertEquals(List.of(new BlockPos(4, 5, 6)), decoded.state().remainingTargets());
    }

    @Test
    void unknownSchemaAndOversizedTargetCountFailClosed() {
        NBTTagCompound invalidSchema = validTag();
        invalidSchema.setInteger("schema", 77);
        assertThrows(IllegalArgumentException.class, () -> MiningTaskCodec.decode(invalidSchema));

        NBTTagCompound oversized = validTag();
        oversized.setInteger("total", MiningTaskCodec.MAX_TARGETS + 1);
        assertThrows(IllegalArgumentException.class, () -> MiningTaskCodec.decode(oversized));
    }

    @Test
    void payloadRejectsWorkflowDrift() {
        int dimension = 0;
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, 2, List.of(new BlockPos(0, 0, 0)),
                1, 0, 0, 0, EnumFacing.DOWN, 0,
                false, true, 0.0F, -1, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new MiningTaskPayload(UUID.randomUUID(), dimension, 3, state));
    }

    private static NBTTagCompound validTag() {
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, -1, List.of(new BlockPos(0, 0, 0)),
                1, 0, 0, 0, EnumFacing.DOWN, 0,
                false, true, 0.0F, -1, List.of());
        int dimension = 0;
        return MiningTaskCodec.encode(new MiningTaskPayload(UUID.randomUUID(), dimension, -1, state));
    }

    private static NBTTagCompound historyTag() {
        NBTTagCompound history = new NBTTagCompound();
        history.setLong("pos", 1L);
        history.setTag("state", new NBTTagCompound());
        return history;
    }
}
