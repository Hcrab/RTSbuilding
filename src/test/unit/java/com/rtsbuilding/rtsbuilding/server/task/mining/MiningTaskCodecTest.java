package com.rtsbuilding.rtsbuilding.server.task.mining;

import com.rtsbuilding.rtsbuilding.server.task.MiningTaskPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MiningTaskCodecTest {

    @Test
    void roundTripPreservesDetachedMiningSnapshot() {
        UUID owner = UUID.randomUUID();
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, Identifier.parse("minecraft:overworld"));
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, 9,
                List.of(new BlockPos(4, 5, 6)),
                3, 2, 1, 1, Direction.NORTH, 4,
                true, false, 0.0F, -1, List.of(historyTag()));
        MiningTaskPayload payload = new MiningTaskPayload(owner, dimension, 9, state);

        MiningTaskPayload decoded = MiningTaskCodec.decode(MiningTaskCodec.encode(payload));

        assertEquals(owner, decoded.ownerId());
        assertEquals(dimension, decoded.dimension());
        assertEquals(9, decoded.workflowEntryId());
        assertEquals(2, decoded.state().cursorUnits());
        assertEquals(Direction.NORTH, decoded.state().face());
        assertEquals(List.of(new BlockPos(4, 5, 6)), decoded.state().remainingTargets());
    }

    @Test
    void unknownSchemaAndOversizedTargetCountFailClosed() {
        CompoundTag invalidSchema = validTag();
        invalidSchema.putInt("schema", 77);
        assertThrows(IllegalArgumentException.class, () -> MiningTaskCodec.decode(invalidSchema));

        CompoundTag oversized = validTag();
        oversized.putInt("total", MiningTaskCodec.MAX_TARGETS + 1);
        assertThrows(IllegalArgumentException.class, () -> MiningTaskCodec.decode(oversized));
    }

    @Test
    void payloadRejectsWorkflowDrift() {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, Identifier.parse("minecraft:overworld"));
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, 2, List.of(new BlockPos(0, 0, 0)),
                1, 0, 0, 0, Direction.DOWN, 0,
                false, true, 0.0F, -1, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new MiningTaskPayload(UUID.randomUUID(), dimension, 3, state));
    }

    private static CompoundTag validTag() {
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, -1, List.of(new BlockPos(0, 0, 0)),
                1, 0, 0, 0, Direction.DOWN, 0,
                false, true, 0.0F, -1, List.of());
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, Identifier.parse("minecraft:overworld"));
        return MiningTaskCodec.encode(new MiningTaskPayload(UUID.randomUUID(), dimension, -1, state));
    }

    private static CompoundTag historyTag() {
        CompoundTag history = new CompoundTag();
        history.putLong("pos", 1L);
        history.put("state", new CompoundTag());
        return history;
    }
}
