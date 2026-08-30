package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacedBlockTrackerDataTest {
    private static final ResourceLocation DIRT = ResourceLocation.fromNamespaceAndPath("minecraft", "dirt");
    private static final ResourceLocation STONE = ResourceLocation.fromNamespaceAndPath("minecraft", "stone");

    @Test
    void v2CredentialMatchesOwnerAndBlockIdOnly() {
        PlacedBlockTrackerData data = new PlacedBlockTrackerData();
        BlockPos pos = new BlockPos(4, 64, 8);
        UUID owner = UUID.randomUUID();

        PlacedBlockTrackerData.CredentialSnapshot first = data.markPlaced(pos, owner, DIRT);
        PlacedBlockTrackerData.CredentialSnapshot duplicate = data.markPlaced(pos, owner, DIRT);
        assertEquals(first, duplicate, "重复放置通知不得更换稳定 generation");

        assertEquals(PlacedBlockTrackerData.RecoveryStatus.MATCH,
                data.checkRecovery(pos, owner, DIRT, false).status());
        assertEquals(PlacedBlockTrackerData.RecoveryStatus.OWNER_MISMATCH,
                data.checkRecovery(pos, UUID.randomUUID(), DIRT, false).status());
        assertTrue(data.isPlaced(pos), "owner 不匹配只能阻断瞬时回收，不能消费凭据");
        assertEquals(PlacedBlockTrackerData.RecoveryStatus.BLOCK_ID_MISMATCH,
                data.checkRecovery(pos, owner, STONE, false).status());
        assertFalse(data.isPlaced(pos), "不同注册 ID 必须清除陈旧凭据");
    }

    @Test
    void legacyCredentialBindsCurrentBlockWithoutOwnerClaim() {
        PlacedBlockTrackerData data = new PlacedBlockTrackerData();
        BlockPos pos = new BlockPos(1, 70, 2);
        data.markLegacy(pos);

        PlacedBlockTrackerData.RecoveryCheck check =
                data.checkRecovery(pos, UUID.randomUUID(), DIRT, false);
        assertEquals(PlacedBlockTrackerData.RecoveryStatus.LEGACY_MATCH, check.status());
        assertNull(check.credential().owner());
        assertEquals(DIRT, check.credential().blockId());
        assertEquals(PlacedBlockTrackerData.CredentialKind.LEGACY, check.credential().kind());
    }

    @Test
    void credentialSnapshotCodecKeepsLegacyAndV2Shape() {
        PlacedBlockTrackerData.CredentialSnapshot original =
                new PlacedBlockTrackerData.CredentialSnapshot(
                        UUID.randomUUID(), STONE, 42L, PlacedBlockTrackerData.CredentialKind.V2);
        CompoundTag encoded = PlacedBlockTrackerData.encodeSnapshot(original);
        assertEquals(original, PlacedBlockTrackerData.decodeSnapshot(encoded));

        PlacedBlockTrackerData.CredentialSnapshot legacy =
                PlacedBlockTrackerData.CredentialSnapshot.legacy(null, 7L);
        assertEquals(legacy, PlacedBlockTrackerData.decodeSnapshot(
                PlacedBlockTrackerData.encodeSnapshot(legacy)));
        assertThrows(IllegalArgumentException.class,
                () -> PlacedBlockTrackerData.decodeSnapshot(new CompoundTag()));
    }

    @Test
    void v1ShadowLoadsAsLegacyAndV2RoundTripsWithoutReinterpretingShadow() {
        BlockPos legacyPos = new BlockPos(2, 65, 3);
        CompoundTag v1 = new CompoundTag();
        v1.putLongArray("placed", new long[]{legacyPos.asLong()});
        PlacedBlockTrackerData legacyData = PlacedBlockTrackerData.load(v1, null);
        assertEquals(PlacedBlockTrackerData.RecoveryStatus.LEGACY_MATCH,
                legacyData.checkRecovery(legacyPos, UUID.randomUUID(), DIRT, false).status());

        PlacedBlockTrackerData v2Data = new PlacedBlockTrackerData();
        BlockPos v2Pos = new BlockPos(9, 65, 3);
        UUID owner = UUID.randomUUID();
        v2Data.markPlaced(v2Pos, owner, STONE);
        PlacedBlockTrackerData.CredentialSnapshot original = v2Data.captureSnapshot(v2Pos);
        CompoundTag saved = v2Data.save(new CompoundTag(), null);
        PlacedBlockTrackerData loaded = PlacedBlockTrackerData.load(saved, null);
        assertEquals(original, loaded.captureSnapshot(v2Pos),
                "V2 round-trip 必须保留 owner、block ID 和 generation");
        assertEquals(PlacedBlockTrackerData.RecoveryStatus.MATCH,
                loaded.checkRecovery(v2Pos, owner, STONE, false).status());
        assertEquals(PlacedBlockTrackerData.RecoveryStatus.NOT_TRACKED,
                loaded.checkRecovery(legacyPos, owner, DIRT, false).status(),
                "V2 loader 不得把 shadow 坐标重复解释成 legacy");
    }

    @Test
    void corruptV2ArrayLengthsFailClosed() {
        CompoundTag corrupt = new CompoundTag();
        corrupt.putInt("schema", PlacedBlockTrackerData.SCHEMA_VERSION);
        corrupt.putLongArray("positions", new long[]{BlockPos.ZERO.asLong()});
        corrupt.put("block_palette", new ListTag());
        corrupt.putIntArray("block_indices", new int[0]);
        corrupt.putLongArray("owner_palette", new long[0]);
        corrupt.putIntArray("owner_indices", new int[0]);
        corrupt.putLongArray("generations", new long[0]);
        corrupt.putByteArray("kinds", new byte[0]);
        PlacedBlockTrackerData loaded = PlacedBlockTrackerData.load(corrupt, null);
        assertFalse(loaded.isPlaced(BlockPos.ZERO));
    }

    @Test
    void mismatchedShadowPositionsFailClosedInsteadOfDowngradingV2() {
        PlacedBlockTrackerData data = new PlacedBlockTrackerData();
        BlockPos tracked = new BlockPos(3, 64, 5);
        data.markPlaced(tracked, UUID.randomUUID(), STONE);
        CompoundTag saved = data.save(new CompoundTag(), null);
        saved.putLongArray("placed", new long[]{BlockPos.ZERO.asLong()});

        PlacedBlockTrackerData loaded = PlacedBlockTrackerData.load(saved, null);

        assertFalse(loaded.isPlaced(tracked), "损坏 shadow 不得触发部分加载或 legacy 降级");
        assertFalse(loaded.isPlaced(BlockPos.ZERO), "损坏 shadow 坐标不得被重新解释为 legacy");
    }
}
