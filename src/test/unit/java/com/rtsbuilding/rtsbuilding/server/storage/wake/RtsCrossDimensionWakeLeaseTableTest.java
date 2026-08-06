package com.rtsbuilding.rtsbuilding.server.storage.wake;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 跨维区块短租约的纯内存边界测试，不启动 Minecraft 服务端。 */
class RtsCrossDimensionWakeLeaseTableTest {
    private static final long LIFESPAN = 100L;

    @Test
    void refreshingSameEndpointDoesNotConsumeAnotherSlot() {
        RtsCrossDimensionWakeLeaseTable table = new RtsCrossDimensionWakeLeaseTable();
        UUID player = UUID.randomUUID();
        var endpoint = endpoint(Level.NETHER, 12L);

        assertEquals(RtsCrossDimensionWakeLeaseTable.TouchResult.ADMITTED,
                table.touch(player, endpoint, 10L, 1, LIFESPAN));
        assertEquals(RtsCrossDimensionWakeLeaseTable.TouchResult.REFRESHED,
                table.touch(player, endpoint, 20L, 1, LIFESPAN));
        assertEquals(1, table.size(player));
    }

    @Test
    void rejectsNewEndpointAtCapacityWithoutEvictingExistingLease() {
        RtsCrossDimensionWakeLeaseTable table = new RtsCrossDimensionWakeLeaseTable();
        UUID player = UUID.randomUUID();
        var admitted = endpoint(Level.NETHER, 12L);
        var rejected = endpoint(Level.END, 13L);

        table.touch(player, admitted, 10L, 1, LIFESPAN);

        assertEquals(RtsCrossDimensionWakeLeaseTable.TouchResult.CAPACITY_REACHED,
                table.touch(player, rejected, 11L, 1, LIFESPAN));
        assertEquals(1, table.size(player));
        assertTrue(table.ownersOf(admitted).contains(player));
        assertTrue(table.ownersOf(rejected).isEmpty());
    }

    @Test
    void expiredLeaseReleasesCapacityForAnotherEndpoint() {
        RtsCrossDimensionWakeLeaseTable table = new RtsCrossDimensionWakeLeaseTable();
        UUID player = UUID.randomUUID();
        var expired = endpoint(Level.NETHER, 12L);
        var replacement = endpoint(Level.END, 13L);

        table.touch(player, expired, 10L, 1, LIFESPAN);

        assertEquals(RtsCrossDimensionWakeLeaseTable.TouchResult.ADMITTED,
                table.touch(player, replacement, 111L, 1, LIFESPAN));
        assertTrue(table.ownersOf(expired).isEmpty());
        assertTrue(table.ownersOf(replacement).contains(player));
    }

    @Test
    void ownersUseExactDimensionAndChunkIdentity() {
        RtsCrossDimensionWakeLeaseTable table = new RtsCrossDimensionWakeLeaseTable();
        UUID player = UUID.randomUUID();
        var nether = endpoint(Level.NETHER, 42L);
        var endAtSameChunk = endpoint(Level.END, 42L);

        table.touch(player, nether, 10L, 2, LIFESPAN);

        assertTrue(table.ownersOf(nether).contains(player));
        assertTrue(table.ownersOf(endAtSameChunk).isEmpty());
    }

    @Test
    void releaseReturnsAllPlayerEndpointsAndClearsOwnership() {
        RtsCrossDimensionWakeLeaseTable table = new RtsCrossDimensionWakeLeaseTable();
        UUID player = UUID.randomUUID();
        var first = endpoint(Level.NETHER, 12L);
        var second = endpoint(Level.END, 13L);
        table.touch(player, first, 10L, 2, LIFESPAN);
        table.touch(player, second, 11L, 2, LIFESPAN);

        assertEquals(2, table.release(player).size());
        assertEquals(0, table.size(player));
        assertTrue(table.ownersOf(first).isEmpty());
        assertTrue(table.ownersOf(second).isEmpty());
    }

    private static RtsCrossDimensionWakeLeaseTable.WakeEndpoint endpoint(
            ResourceKey<Level> dimension, long chunkPos) {
        return new RtsCrossDimensionWakeLeaseTable.WakeEndpoint(dimension, chunkPos);
    }
}
