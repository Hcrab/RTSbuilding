package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskCodec;
import com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** RTS 自己建造的普通方块都有唯一 generation，不能只验证无凭据石头的最大体积。 */
class MiningOwnedVolumeCodecTest {
    private static final UUID OWNER = UUID.fromString("e5a46c58-a445-4020-8780-48b3c8943fc2");

    @Test
    void fullOwnedStairSelectionFitsTaskBudgetAndRoundTripsWithoutLosingCredentials() {
        List<CompoundTag> history = new ArrayList<>(MiningLimits.MAX_VOLUME);
        CompoundTag state = stairState();
        for (int index = 0; index < MiningLimits.MAX_VOLUME; index++) {
            CompoundTag record = new CompoundTag();
            record.putLong("pos", new BlockPos(index & 63, (index >> 6) & 63, index >> 12).asLong());
            record.put("state", state);
            record.put("credential_before", credential(index + 1L));
            history.add(record);
        }
        MiningTaskPayload payload = completed(history);
        history.clear();
        CompoundTag encoded = assertDoesNotThrow(() -> MiningTaskCodec.encode(payload));
        long encodedBytes = new TaskCodec().estimatePayloadBytes(encoded);
        assertTrue(encodedBytes < TaskCodec.MAX_TASK_PAYLOAD_BYTES);
        assertTrue(payload.state().historyBudget().estimatedPayloadBytes()
                >= encodedBytes);
        MiningTaskPayload restored = assertDoesNotThrow(() -> MiningTaskCodec.decode(encoded));
        assertEquals(MiningLimits.MAX_VOLUME, restored.state().succeededUnits());
        assertEquals(encoded, MiningTaskCodec.encode(restored),
                "所有位置、属性和独立 generation 都必须完整恢复，不能丢弃凭据来降低节点数");
    }

    @Test
    void optionalBeforeAndAfterSnapshotsSurviveSharedCompactHistory() {
        CompoundTag record = new CompoundTag();
        record.putLong("pos", BlockPos.ZERO.asLong());
        record.put("state", stairState());
        record.put("credential_before", credential(12));
        record.put("credential_after", credential(13));
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.putString("note", "前后快照均保留");
        record.put("block_entity", blockEntity);
        record.put("after_block_entity", blockEntity.copy());
        record.put("after_state", stairState());
        CompoundTag encoded = MiningTaskCodec.encode(completed(List.of(record)));
        List<CompoundTag> restored = MiningTaskCodec.decode(encoded).state().historyRecords();
        assertEquals(List.of(record), restored);
    }

    @Test
    void corruptCredentialColumnsCannotSilentlyDropOrReassignOwnership() {
        CompoundTag record = new CompoundTag();
        record.putLong("pos", BlockPos.ZERO.asLong());
        record.put("state", stairState());
        record.put("credential_before", credential(12));
        CompoundTag encoded = MiningTaskCodec.encode(completed(List.of(record)));
        CompoundTag column = encoded.getCompound("history_credentials").getCompound("credential_before");
        column.putLongArray("generations", new long[] {0});
        assertThrows(IllegalArgumentException.class, () -> MiningTaskCodec.decode(encoded));
        column.putLongArray("generations", new long[] {12});
        column.putIntArray("indices", new int[] {1});
        assertThrows(IllegalArgumentException.class, () -> MiningTaskCodec.decode(encoded));
        column.putIntArray("indices", new int[] {0});
        column.putIntArray("records", new int[] {-1});
        assertThrows(IllegalArgumentException.class, () -> MiningTaskCodec.decode(encoded));
    }

    private static MiningTaskPayload completed(List<CompoundTag> history) {
        int count = history.size();
        return new MiningTaskPayload(OWNER, Level.OVERWORLD, 1, new MiningTaskState(
                MiningTaskState.Mode.BATCH, 1, List.of(), count, count, count, 0,
                Direction.DOWN, 0, false, true, 0.0F, -1, history, true));
    }

    private static CompoundTag stairState() {
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:oak_stairs");
        CompoundTag properties = new CompoundTag();
        properties.putString("facing", "north");
        properties.putString("half", "bottom");
        properties.putString("shape", "straight");
        properties.putString("waterlogged", "false");
        state.put("Properties", properties);
        return state;
    }

    private static CompoundTag credential(long generation) {
        CompoundTag credential = new CompoundTag();
        credential.putString("block", "minecraft:oak_stairs");
        credential.putLong("generation", generation);
        credential.putByte("kind", (byte) 0);
        credential.putUUID("owner", OWNER);
        return credential;
    }
}
