package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacedBlockTrackerLargeDataTest {
    private static final int LARGE_RECORD_COUNT = 1_000_001;
    private static final int LARGE_PALETTE_SIZE = 65_537;
    private static final ResourceLocation STONE = ResourceLocation.tryParse("minecraft:stone");
    private static final UUID OWNER = new UUID(1L, 2L);
    private static final UUID HISTORICAL_OWNER = new UUID(3L, 4L);

    @Test
    void v2CompressedRoundTripKeepsEveryCredentialAcrossOldLimit() throws IOException {
        PlacedBlockTrackerData original = new PlacedBlockTrackerData();
        for (int i = 0; i < LARGE_RECORD_COUNT - 2; i++) {
            original.markPlaced(position(i), OWNER, STONE);
        }
        BlockPos legacy = position(LARGE_RECORD_COUNT - 2);
        original.markLegacy(legacy);
        BlockPos restored = position(LARGE_RECORD_COUNT - 1);
        var historical = new PlacedBlockTrackerData.CredentialSnapshot(
                HISTORICAL_OWNER, STONE, 2L * LARGE_RECORD_COUNT, PlacedBlockTrackerData.CredentialKind.V2);
        original.restoreSnapshot(restored, historical);

        PlacedBlockTrackerData loaded = compressedRoundTrip(original.save(new CompoundTag()));

        for (int i = 0; i < LARGE_RECORD_COUNT; i++) {
            BlockPos pos = position(i);
            assertEquals(original.captureSnapshot(pos), loaded.captureSnapshot(pos), pos::toString);
        }
        assertEquals(PlacedBlockTrackerData.RecoveryStatus.LEGACY_MATCH,
                loaded.checkRecovery(legacy, OWNER, STONE, false).status());
        assertEquals(PlacedBlockTrackerData.RecoveryStatus.OWNER_MISMATCH,
                loaded.checkRecovery(restored, OWNER, STONE, false).status());
        assertEquals(PlacedBlockTrackerData.RecoveryStatus.MATCH,
                loaded.checkRecovery(restored, HISTORICAL_OWNER, STONE, false).status());
        assertEquals(historical.generation() + 1L,
                loaded.markPlaced(position(LARGE_RECORD_COUNT), OWNER, STONE).generation());
    }

    @Test
    void compressedV1LoadsEveryLegacyRecordAcrossOldLimit() throws IOException {
        long[] positions = new long[LARGE_RECORD_COUNT];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = position(i).asLong();
        }
        CompoundTag legacy = new CompoundTag();
        legacy.putLongArray("placed", positions);

        PlacedBlockTrackerData loaded = compressedRoundTrip(legacy);

        for (int i = 0; i < positions.length; i++) {
            BlockPos pos = position(i);
            assertEquals(PlacedBlockTrackerData.CredentialSnapshot.legacy(null, i + 1L),
                    loaded.captureSnapshot(pos), pos::toString);
        }
    }

    @Test
    void compressedV2RoundTripKeepsEveryOwnerAndBlockAcrossOldPaletteLimit() throws IOException {
        PlacedBlockTrackerData original = new PlacedBlockTrackerData();
        for (int i = 0; i < LARGE_PALETTE_SIZE; i++) {
            original.markPlaced(position(i), new UUID(0L, i),
                    ResourceLocation.tryParse("test:block_" + i));
        }

        PlacedBlockTrackerData loaded = compressedRoundTrip(original.save(new CompoundTag()));

        for (int i = 0; i < LARGE_PALETTE_SIZE; i++) {
            BlockPos pos = position(i);
            assertEquals(original.captureSnapshot(pos), loaded.captureSnapshot(pos), pos::toString);
        }
    }

    private static BlockPos position(int index) {
        return new BlockPos(index, 64, 0);
    }

    private static PlacedBlockTrackerData compressedRoundTrip(CompoundTag tracker) throws IOException {
        CompoundTag root = new CompoundTag();
        root.put("data", tracker);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, output);
        CompoundTag decoded = NbtIo.readCompressed(
                new ByteArrayInputStream(output.toByteArray()));
        return PlacedBlockTrackerData.load(decoded.getCompound("data"));
    }
}
