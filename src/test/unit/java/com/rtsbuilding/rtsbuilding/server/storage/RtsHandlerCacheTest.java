package com.rtsbuilding.rtsbuilding.server.storage;

import net.minecraftforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RtsHandlerCache} ??focuses on cache state management
 * and query API that does NOT reach {@code ItemStack.EMPTY} or
 * {@code CachedSlot.EMPTY} static initializers.
 *
 * <p><b>Why these tests and not more:</b> The {@code update(IItemHandler)}
 * method internally calls {@code readSlot()} which accesses
 * {@code BuiltInRegistries.ITEM} and returns {@code CachedSlot.EMPTY}
 * (which references {@code ItemStack.EMPTY}). Both require a fully
 * bootstrapped Minecraft environment not available in unit tests.
 *
 * <p>The tests below cover all code paths that operate solely on
 * {@code Map<String, Long>} counts, dirty flags, and buffer-size queries.
 */
@ExtendWith(MockitoExtension.class)
class RtsHandlerCacheTest {

    private RtsHandlerCache cache;

    @Mock private IItemHandler handler;

    @BeforeEach
    void setUp() {
        cache = new RtsHandlerCache();
    }

    // ======================================================================
    //  Empty / Null guards
    // ======================================================================

    @Test
    void updateOnZeroSlotHandlerReturnsEmpty() {
        when(handler.getSlots()).thenReturn(0);
        assertTrue(cache.update(handler).isEmpty());
    }

    @Test
    void updateThrowsOnNullHandler() {
        assertThrows(NullPointerException.class, () -> cache.update(null));
    }

    @Test
    void freshCacheCountsAreZero() {
        assertEquals(0L, cache.getCount("minecraft:diamond"));
        assertEquals(0L, cache.getCount((String) null));
    }

    @Test
    void getCachedSlotCountStartsZero() {
        assertEquals(0, cache.getCachedSlotCount());
    }

    @Test
    void isDirtyDefaultsToFalse() {
        assertFalse(cache.isDirty());
        cache.clearDirty();
        assertFalse(cache.isDirty());
    }

    // ======================================================================
    //  Cache state management
    // ======================================================================

    @Test
    void invalidateResetsEverything() {
        cache.invalidate();
        assertEquals(0, cache.getCachedSlotCount());
        assertEquals(0L, cache.getCount("any"));
        assertTrue(cache.isDirty());
    }

    @Test
    void releaseResetsDirtyFlag() {
        cache.release();
        assertEquals(0, cache.getCachedSlotCount());
        assertFalse(cache.isDirty());
    }

    @Test
    void invalidateMarksDirty() {
        assertFalse(cache.isDirty());
        cache.invalidate();
        assertTrue(cache.isDirty());
    }

    @Test
    void clearDirtyAfterInvalidate() {
        cache.invalidate();
        assertTrue(cache.isDirty());
        cache.clearDirty();
        assertFalse(cache.isDirty());
    }

    // ======================================================================
    //  getAvailableItems
    // ======================================================================

    @Test
    void getAvailableItemsOnEmptyCacheReturnsEmptyMap() {
        Map<String, Long> out = new HashMap<>();
        cache.getAvailableItems(out);
        assertTrue(out.isEmpty());
    }

    @Test
    void getAvailableItemsDoesNotAlterExistingUnrelatedKeys() {
        Map<String, Long> out = new HashMap<>();
        out.put("existing.key", 42L);
        cache.getAvailableItems(out);
        assertEquals(42L, out.get("existing.key"));
    }
}
