package com.rtsbuilding.rtsbuilding.server.task.mining;

import com.rtsbuilding.rtsbuilding.server.task.MiningTaskPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.rtsbuilding.rtsbuilding.server.data.RtsDimensionKeys;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
        ResourceKey<Level> dimension =
                RtsDimensionKeys.create(new ResourceLocation("minecraft", "overworld"));
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, 9,
                List.of(new BlockPos(4, 5, 6)),
                3, 2, 1, 1, Direction.NORTH, 4,
                true, false, 0.0F, -1, List.of(historyTag()), true);
        MiningTaskPayload payload = new MiningTaskPayload(owner, dimension, 9, state);

        MiningTaskPayload decoded = MiningTaskCodec.decode(MiningTaskCodec.encode(payload));

        assertEquals(owner, decoded.ownerId());
        assertEquals(dimension, decoded.dimension());
        assertEquals(9, decoded.workflowEntryId());
        assertEquals(2, decoded.state().cursorUnits());
        assertEquals(Direction.NORTH, decoded.state().face());
        assertEquals(List.of(new BlockPos(4, 5, 6)), decoded.state().remainingTargets());
        assertEquals(true, decoded.state().creativeOperation());
        assertEquals(state.historyRecords(), decoded.state().historyRecords());
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
        ResourceKey<Level> dimension =
                RtsDimensionKeys.create(new ResourceLocation("minecraft", "overworld"));
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, 2, List.of(new BlockPos(0, 0, 0)),
                1, 0, 0, 0, Direction.DOWN, 0,
                false, true, 0.0F, -1, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new MiningTaskPayload(UUID.randomUUID(), dimension, 3, state));
    }

    @Test
    void schemaTwoInlineHistoryWithoutCredentialsRemainsReadable() {
        CompoundTag legacy = validTag();
        legacy.putInt("schema", 2);
        legacy.remove("history_positions");
        legacy.remove("history_states");
        legacy.remove("history_state_indices");
        CompoundTag history = new CompoundTag();
        history.putLong("pos", BlockPos.ZERO.asLong());
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:stone");
        history.put("state", state);
        legacy.put("history", new net.minecraft.nbt.ListTag());
        legacy.getList("history", net.minecraft.nbt.Tag.TAG_COMPOUND).add(history);

        assertEquals(1, MiningTaskCodec.decode(legacy).state().historyRecords().size());
    }

    private static CompoundTag validTag() {
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, -1, List.of(new BlockPos(0, 0, 0)),
                1, 0, 0, 0, Direction.DOWN, 0,
                false, true, 0.0F, -1, List.of());
        ResourceKey<Level> dimension =
                RtsDimensionKeys.create(new ResourceLocation("minecraft", "overworld"));
        return MiningTaskCodec.encode(new MiningTaskPayload(UUID.randomUUID(), dimension, -1, state));
    }

    private static CompoundTag historyTag() {
        CompoundTag history = new CompoundTag();
        history.putLong("pos", 1L);
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:stone");
        history.put("state", state);
        CompoundTag credential = new CompoundTag();
        credential.putString("block", "minecraft:stone");
        credential.putUUID("owner", UUID.randomUUID());
        credential.putLong("generation", 4L);
        credential.putByte("kind", (byte) 0);
        history.put("credential_before", credential);
        return history;
    }
}
