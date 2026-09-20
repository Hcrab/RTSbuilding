package com.rtsbuilding.rtsbuilding.server.task.mining;

import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.task.MiningTaskPayload;
import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MiningTaskCodecTest {

    @Test
    void roundTripPreservesDetachedMiningSnapshot() {
        UUID owner = UUID.randomUUID();
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
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
        CompoundTag decodedHistory = decoded.state().historyRecords().getFirst();
        assertEquals(new PlacedBlockTrackerData.CredentialSnapshot(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        ResourceLocation.parse("minecraft:stone"), 8L,
                        PlacedBlockTrackerData.CredentialKind.V2),
                PlacedBlockTrackerData.decodeSnapshot(decodedHistory.getCompound("credential_before")));
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
    void maximumVolumeIsAcceptedWhileLargerStateIsRejected() {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, -1, List.of(new BlockPos(4, 5, 6)),
                MiningLimits.MAX_VOLUME, MiningLimits.MAX_VOLUME - 1,
                MiningLimits.MAX_VOLUME - 1, 0, Direction.DOWN, 0,
                false, true, 0.0F, -1, List.of());

        MiningTaskPayload decoded = MiningTaskCodec.decode(MiningTaskCodec.encode(
                new MiningTaskPayload(UUID.randomUUID(), dimension, -1, state)));

        assertEquals(MiningLimits.MAX_VOLUME, decoded.state().totalUnits());
        assertEquals(List.of(new BlockPos(4, 5, 6)), decoded.state().remainingTargets());
        assertThrows(IllegalArgumentException.class, () -> new MiningTaskState(
                MiningTaskState.Mode.BATCH, -1, List.of(new BlockPos(4, 5, 6)),
                MiningLimits.MAX_VOLUME + 1, MiningLimits.MAX_VOLUME,
                MiningLimits.MAX_VOLUME, 0, Direction.DOWN, 0,
                false, true, 0.0F, -1, List.of()));
    }

    @Test
    void schemaTwoInlineHistoryRemainsReadableAfterCompactSchemaUpgrade() {
        CompoundTag legacy = validTag();
        long position = 123L;
        // 构造真实的旧格式记录，避免依赖当前紧凑格式的标记。
        net.minecraft.nbt.ListTag records = new net.minecraft.nbt.ListTag();
        CompoundTag record = legacyHistoryTag();
        record.putLong("pos", position);
        records.add(record);
        legacy.put("history", records);
        legacy.remove("history_positions");
        legacy.putInt("schema", 2);

        MiningTaskPayload decoded = MiningTaskCodec.decode(legacy);

        assertEquals(position, decoded.state().historyRecords().getFirst().getLong("pos"));
    }

    @Test
    void compactHistoryRejectsWrongBlockEntityTypeInsteadOfDroppingNbt() {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        CompoundTag validHistory = historyTag();
        validHistory.put("block_entity", new CompoundTag());
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, -1, List.of(),
                1, 1, 1, 0, Direction.DOWN, 0,
                false, true, 0.0F, -1, List.of(validHistory));
        CompoundTag encoded = MiningTaskCodec.encode(new MiningTaskPayload(
                UUID.randomUUID(), dimension, -1, state));
        encoded.getList("history", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).getCompound("values")
                .putString("block_entity", "not-a-compound");

        assertThrows(IllegalArgumentException.class, () -> MiningTaskCodec.decode(encoded));
    }

    @Test
    void payloadRejectsWorkflowDrift() {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, 2, List.of(new BlockPos(0, 0, 0)),
                1, 0, 0, 0, Direction.DOWN, 0,
                false, true, 0.0F, -1, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new MiningTaskPayload(UUID.randomUUID(), dimension, 3, state));
    }

    @Test
    void legacyHistoryWithoutCredentialFieldsStillDecodes() {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, -1, List.of(new BlockPos(0, 0, 0)),
                1, 0, 0, 0, Direction.DOWN, 0,
                false, true, 0.0F, -1, List.of(legacyHistoryTag()));

        MiningTaskPayload decoded = MiningTaskCodec.decode(MiningTaskCodec.encode(
                new MiningTaskPayload(UUID.randomUUID(), dimension, -1, state)));

        assertFalse(decoded.state().historyRecords().getFirst().contains("credential_before"));
        assertFalse(decoded.state().historyRecords().getFirst().contains("credential_after"));
    }

    private static CompoundTag validTag() {
        MiningTaskState state = new MiningTaskState(
                MiningTaskState.Mode.BATCH, -1, List.of(new BlockPos(0, 0, 0)),
                1, 0, 0, 0, Direction.DOWN, 0,
                false, true, 0.0F, -1, List.of());
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        return MiningTaskCodec.encode(new MiningTaskPayload(UUID.randomUUID(), dimension, -1, state));
    }

    private static CompoundTag historyTag() {
        CompoundTag history = new CompoundTag();
        history.putLong("pos", 1L);
        history.put("state", new CompoundTag());
        history.put("credential_before", PlacedBlockTrackerData.encodeSnapshot(
                new PlacedBlockTrackerData.CredentialSnapshot(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        ResourceLocation.parse("minecraft:stone"), 8L,
                        PlacedBlockTrackerData.CredentialKind.V2)));
        history.put("credential_after", PlacedBlockTrackerData.encodeSnapshot(
                PlacedBlockTrackerData.CredentialSnapshot.legacy(
                        ResourceLocation.parse("minecraft:dirt"), 9L)));
        return history;
    }

    private static CompoundTag legacyHistoryTag() {
        CompoundTag history = new CompoundTag();
        history.putLong("pos", 1L);
        history.put("state", new CompoundTag());
        return history;
    }
}
