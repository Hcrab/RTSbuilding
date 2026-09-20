package com.rtsbuilding.rtsbuilding.server.task.destruction;

import com.rtsbuilding.rtsbuilding.server.data.RtsDimensionKeys;
import com.rtsbuilding.rtsbuilding.server.task.DestructionTaskPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Forge detached destruction codec 的 schema3 凭据和旧 inline history 契约。 */
class DestructionTaskCodecTest {
    @Test
    void roundTripPreservesCredentialBearingHistory() {
        UUID owner = UUID.randomUUID();
        ResourceKey<Level> dimension =
                RtsDimensionKeys.create(new ResourceLocation("minecraft", "overworld"));
        DestructionTaskState state = new DestructionTaskState(
                List.of(new BlockPos(4, 5, 6)), (byte) 2, true, false,
                11, 1, 1, 0, List.of(new BlockPos(4, 5, 6)),
                List.of(historyTag()), true);
        DestructionTaskPayload payload = new DestructionTaskPayload(owner, dimension, 11, state);

        DestructionTaskPayload decoded = DestructionTaskCodec.decode(
                DestructionTaskCodec.encode(payload));

        assertEquals(owner, decoded.ownerId());
        assertEquals(dimension, decoded.dimension());
        assertEquals(11, decoded.workflowEntryId());
        assertEquals(List.of(new BlockPos(4, 5, 6)), decoded.state().destroyedPositions());
        assertEquals(state.historyRecords(), decoded.state().historyRecords());
        assertEquals(true, decoded.state().creativeOperation());
    }

    @Test
    void schemaTwoInlineHistoryWithoutCredentialsRemainsReadable() {
        CompoundTag legacy = DestructionTaskCodec.encode(new DestructionTaskPayload(
                UUID.randomUUID(),
                RtsDimensionKeys.create(new ResourceLocation("minecraft", "overworld")),
                -1,
                new DestructionTaskState(
                        List.of(BlockPos.ZERO), (byte) 0, false, false,
                        -1, 1, 1, 0, List.of(BlockPos.ZERO), List.of(historyTag()), false)));
        legacy.putInt("schema", 2);
        legacy.remove("historyPositions");
        legacy.remove("history_states");
        legacy.remove("history_state_indices");
        legacy.remove("history_credentials");
        CompoundTag inline = historyTag();
        inline.remove("credential_before");
        legacy.put("history", new net.minecraft.nbt.ListTag());
        legacy.getList("history", net.minecraft.nbt.Tag.TAG_COMPOUND).add(inline);

        assertEquals(1, DestructionTaskCodec.decode(legacy).state().historyRecords().size());
    }

    @Test
    void malformedCredentialFieldFailsClosed() {
        CompoundTag payload = DestructionTaskCodec.encode(new DestructionTaskPayload(
                UUID.randomUUID(),
                RtsDimensionKeys.create(new ResourceLocation("minecraft", "overworld")),
                -1,
                new DestructionTaskState(
                        List.of(BlockPos.ZERO), (byte) 0, false, false,
                        -1, 1, 1, 0, List.of(BlockPos.ZERO), List.of(historyTag()), false)));
        CompoundTag record = new CompoundTag();
        record.putLong("pos", BlockPos.ZERO.asLong());
        record.put("state", stateTag("minecraft:stone"));
        record.putString("credential_before", "not-a-compound");
        payload.put("history", new net.minecraft.nbt.ListTag());
        payload.getList("history", net.minecraft.nbt.Tag.TAG_COMPOUND).add(record);
        payload.remove("historyPositions");
        payload.remove("history_states");
        payload.remove("history_state_indices");
        payload.remove("history_credentials");
        payload.putInt("schema", 2);

        assertThrows(IllegalArgumentException.class, () -> DestructionTaskCodec.decode(payload));
    }

    private static CompoundTag historyTag() {
        CompoundTag history = new CompoundTag();
        history.putLong("pos", BlockPos.ZERO.asLong());
        history.put("state", stateTag("minecraft:stone"));
        history.put("credential_before", credentialTag());
        return history;
    }

    private static CompoundTag stateTag(String name) {
        CompoundTag state = new CompoundTag();
        state.putString("Name", name);
        return state;
    }

    private static CompoundTag credentialTag() {
        CompoundTag credential = new CompoundTag();
        credential.putString("block", "minecraft:stone");
        credential.putUUID("owner", UUID.randomUUID());
        credential.putLong("generation", 9L);
        credential.putByte("kind", (byte) 0);
        return credential;
    }
}
