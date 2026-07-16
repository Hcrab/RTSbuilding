package com.rtsbuilding.rtsbuilding.server.storage;
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
    private final AtomicInteger releases = new AtomicInteger();
    private final RtsEndpointLeaseCache cache = new RtsEndpointLeaseCache(handler -> releases.incrementAndGet());

    @AfterEach
    void clear() {
        cache.invalidatePlayer(playerId);
    }

    @Test
    void stableEndpointResolvesOnlyOnceUntilBlockEntityChanges() {
        AtomicInteger resolves = new AtomicInteger();
        Object firstBlockEntity = new Object();
        IItemHandler first = mock(IItemHandler.class);
        IItemHandler second = mock(IItemHandler.class);

        IItemHandler a = cache.resolveItem(playerId, Level.OVERWORLD,
                BlockPos.ZERO, null, firstBlockEntity, () -> {
                    resolves.incrementAndGet();
                    return first;
                });
        IItemHandler b = cache.resolveItem(playerId, Level.OVERWORLD,
                BlockPos.ZERO, null, firstBlockEntity, () -> {
                    resolves.incrementAndGet();
                    return second;
                });

        assertSame(a, b);
        assertEquals(1, resolves.get());

        IItemHandler c = cache.resolveItem(playerId, Level.OVERWORLD,
                BlockPos.ZERO, null, new Object(), () -> {
                    resolves.incrementAndGet();
                    return second;
                });
        assertSame(second, c);
        assertEquals(2, resolves.get());
        assertEquals(1, releases.get(), "替换租约必须释放旧处理器一次");

        cache.invalidatePlayer(playerId);
        cache.invalidatePlayer(playerId);
        assertEquals(2, releases.get(), "同一租约只能释放一次");
    }
}
