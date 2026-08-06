package com.rtsbuilding.rtsbuilding.server.storage.wake;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证异维储存票据名额的刷新、上限与自然过期，不依赖 Minecraft 世界启动。 */
class RtsCrossDimensionWakeLeaseTableTest {
    @Test
    void refreshesKnownEndpointButRejectsASecondEndpointAtCapacity() {
        RtsCrossDimensionWakeLeaseTable table = new RtsCrossDimensionWakeLeaseTable();
        UUID player = UUID.randomUUID();
        RtsCrossDimensionWakeLeaseTable.WakeEndpoint first = endpoint(-1, 3L);

        assertEquals(RtsCrossDimensionWakeLeaseTable.TouchResult.ADMITTED,
                table.touch(player, first, 10L, 1, 100L));
        assertEquals(RtsCrossDimensionWakeLeaseTable.TouchResult.REFRESHED,
                table.touch(player, first, 20L, 1, 100L));
        assertEquals(RtsCrossDimensionWakeLeaseTable.TouchResult.CAPACITY_REACHED,
                table.touch(player, endpoint(1, 4L), 20L, 1, 100L));
        assertEquals(1, table.size(player));
    }

    @Test
    void expiresUnusedEndpointAndReleasesItsCapacity() {
        RtsCrossDimensionWakeLeaseTable table = new RtsCrossDimensionWakeLeaseTable();
        UUID player = UUID.randomUUID();
        RtsCrossDimensionWakeLeaseTable.WakeEndpoint first = endpoint(-1, 3L);
        table.touch(player, first, 10L, 1, 5L);

        List<RtsCrossDimensionWakeLeaseTable.OwnedEndpoint> expired = table.releaseExpired(16L, 5L);

        assertEquals(1, expired.size());
        assertEquals(player, expired.get(0).playerId());
        assertEquals(first, expired.get(0).endpoint());
        assertEquals(0, table.size(player));
        assertEquals(RtsCrossDimensionWakeLeaseTable.TouchResult.ADMITTED,
                table.touch(player, endpoint(1, 4L), 16L, 1, 5L));
    }

    @Test
    void releasingOneFailedEndpointKeepsThePlayersOtherLease() {
        RtsCrossDimensionWakeLeaseTable table = new RtsCrossDimensionWakeLeaseTable();
        UUID player = UUID.randomUUID();
        RtsCrossDimensionWakeLeaseTable.WakeEndpoint first = endpoint(-1, 3L);
        RtsCrossDimensionWakeLeaseTable.WakeEndpoint second = endpoint(1, 4L);
        table.touch(player, first, 10L, 2, 100L);
        table.touch(player, second, 10L, 2, 100L);

        assertTrue(table.release(player, second));
        assertEquals(1, table.size(player));
        assertEquals(RtsCrossDimensionWakeLeaseTable.TouchResult.REFRESHED,
                table.touch(player, first, 11L, 2, 100L));
        assertEquals(RtsCrossDimensionWakeLeaseTable.TouchResult.ADMITTED,
                table.touch(player, endpoint(7, 5L), 11L, 2, 100L));
    }

    private static RtsCrossDimensionWakeLeaseTable.WakeEndpoint endpoint(int dimension, long chunk) {
        return new RtsCrossDimensionWakeLeaseTable.WakeEndpoint(dimension, chunk);
    }
}
