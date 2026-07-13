package com.rtsbuilding.rtsbuilding.server.storage.cache;

import com.rtsbuilding.rtsbuilding.server.storage.RtsEndpointLeaseCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class RtsEndpointLeaseCacheTest {
    private final UUID playerId = UUID.randomUUID();

    @AfterEach
    void clear() {
        RtsEndpointLeaseCache.INSTANCE.invalidatePlayer(playerId);
    }

    @Test
    void stableEndpointResolvesOnlyOnceUntilBlockEntityChanges() {
        AtomicInteger resolves = new AtomicInteger();
        Object firstBlockEntity = new Object();
        IItemHandler first = mock(IItemHandler.class);
        IItemHandler second = mock(IItemHandler.class);

        IItemHandler a = RtsEndpointLeaseCache.INSTANCE.resolveItem(playerId, Level.OVERWORLD,
                BlockPos.ZERO, null, firstBlockEntity, () -> {
                    resolves.incrementAndGet();
                    return first;
                });
        IItemHandler b = RtsEndpointLeaseCache.INSTANCE.resolveItem(playerId, Level.OVERWORLD,
                BlockPos.ZERO, null, firstBlockEntity, () -> {
                    resolves.incrementAndGet();
                    return second;
                });

        assertSame(a, b);
        assertEquals(1, resolves.get());

        IItemHandler c = RtsEndpointLeaseCache.INSTANCE.resolveItem(playerId, Level.OVERWORLD,
                BlockPos.ZERO, null, new Object(), () -> {
                    resolves.incrementAndGet();
                    return second;
                });
        assertSame(second, c);
        assertEquals(2, resolves.get());
    }
}
