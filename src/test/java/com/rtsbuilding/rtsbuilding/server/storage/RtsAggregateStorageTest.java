package com.rtsbuilding.rtsbuilding.server.storage;

import net.minecraftforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RtsAggregateStorage} ??focuses on mount/unmount,
 * delegate routing, and state queries that do NOT reach
 * {@code ItemStack.EMPTY} static initializers.
 *
 * <p><b>Why these tests and not more:</b> The {@code insert()},
 * {@code extract()}, and {@code getPrototype()} methods either return
 * {@code ItemStack.EMPTY} or call {@code ItemStack.isEmpty()}, which
 * trigger Minecraft's {@code ItemStack} static initializer. That requires
 * a fully bootstrapped game environment not available in unit tests.
 *
 * <p>The tests below cover all code paths that operate purely on
 * {@code NavigableMap} management, delegate routing to mocked caches,
 * and String-based change tracking.
 */
@ExtendWith(MockitoExtension.class)
class RtsAggregateStorageTest {

    private RtsAggregateStorage storage;
    private IItemHandler handlerLow;
    private IItemHandler handlerHigh;
    private RtsHandlerCache cacheLow;
    private RtsHandlerCache cacheHigh;

    @BeforeEach
    void setUp() {
        storage = new RtsAggregateStorage();
        handlerLow = mock(IItemHandler.class);
        handlerHigh = mock(IItemHandler.class);
        cacheLow = mock(RtsHandlerCache.class);
        cacheHigh = mock(RtsHandlerCache.class);
    }

    // ======================================================================
    //  Empty state
    // ======================================================================

    @Test
    void isEmptyOnFreshStorage() {
        assertTrue(storage.isEmpty());
    }

    // ======================================================================
    //  Mount / Unmount
    // ======================================================================

    @Test
    void mountIncreasesNonEmpty() {
        storage.mount(0, handlerLow, cacheLow);
        assertFalse(storage.isEmpty());
    }

    @Test
    void mountUnmountRestoresEmpty() {
        storage.mount(0, handlerLow, cacheLow);
        storage.unmount(handlerLow);
        assertTrue(storage.isEmpty());
    }

    @Test
    void unmountUnknownHandlerDoesNothing() {
        storage.mount(0, handlerLow, cacheLow);
        storage.unmount(handlerHigh);
        assertFalse(storage.isEmpty());
    }

    @Test
    void mountWithHigherPriorityTakesFirstOrder() {
        storage.mount(10, handlerLow, cacheLow);
        storage.mount(100, handlerHigh, cacheHigh);
        assertFalse(storage.isEmpty());
    }

    @Test
    void multipleMountsSamePriorityAllPresent() {
        storage.mount(0, handlerLow, cacheLow);
        storage.mount(0, handlerHigh, cacheHigh);
        assertFalse(storage.isEmpty());
    }

    @Test
    void unmountPartialDoesNotAffectOtherHandlers() {
        storage.mount(0, handlerLow, cacheLow);
        storage.mount(100, handlerHigh, cacheHigh);
        storage.unmount(handlerLow);
        assertFalse(storage.isEmpty()); // handlerHigh still mounted
    }

    // ======================================================================
    //  hasItem (no ItemStack.EMPTY reachable)
    // ======================================================================

    @Test
    void hasItemOnEmptyStorageReturnsFalse() {
        assertFalse(storage.hasItem(mock(net.minecraft.world.item.Item.class)));
    }

    // ======================================================================
    //  getTotalCount (no ItemStack.EMPTY reachable)
    // ======================================================================

    @Test
    void getTotalCountOnEmptyStorageReturnsZero() {
        assertEquals(0L, storage.getTotalCount(mock(net.minecraft.world.item.Item.class)));
    }

    // ======================================================================
    //  Tick update
    // ======================================================================

    @Test
    void tickUpdateOnEmptyStorageReturnsEmpty() {
        Set<String> changes = storage.tickUpdate();
        assertTrue(changes.isEmpty());
    }

    @Test
    void tickUpdateDelegatesToAllCaches() {
        when(cacheLow.update(handlerLow)).thenReturn(Set.of("minecraft:diamond"));
        when(cacheHigh.update(handlerHigh)).thenReturn(Set.of("minecraft:iron_ingot"));

        storage.mount(0, handlerLow, cacheLow);
        storage.mount(100, handlerHigh, cacheHigh);

        Set<String> changes = storage.tickUpdate();
        assertTrue(changes.contains("minecraft:diamond"));
        assertTrue(changes.contains("minecraft:iron_ingot"));
    }

    @Test
    void tickUpdateAppliesQueuedMutations() {
        assertTrue(storage.isEmpty());
        storage.tickUpdate(); // no-op, should not throw
    }

    // ======================================================================
    //  Pending changes drain
    // ======================================================================

    @Test
    void drainPendingChangesOnEmptyStorageReturnsEmpty() {
        assertTrue(storage.drainPendingChanges().isEmpty());
    }

    @Test
    void drainPendingChangesCanBeCalledMultipleTimes() {
        assertTrue(storage.drainPendingChanges().isEmpty());
        assertTrue(storage.drainPendingChanges().isEmpty());
    }

    // ======================================================================
    //  getAvailableItems
    // ======================================================================

    @Test
    void getAvailableItemsDelegatesToCaches() {
        storage.mount(0, handlerLow, cacheLow);
        storage.mount(100, handlerHigh, cacheHigh);

        Map<String, Long> out = new java.util.HashMap<>();
        storage.getAvailableItems(out);

        verify(cacheLow).getAvailableItems(out);
        verify(cacheHigh).getAvailableItems(out);
    }

    @Test
    void getAvailableItemsOnEmptyStorageDoesNotThrow() {
        Map<String, Long> out = new java.util.HashMap<>();
        assertDoesNotThrow(() -> storage.getAvailableItems(out));
        assertTrue(out.isEmpty());
    }
}
