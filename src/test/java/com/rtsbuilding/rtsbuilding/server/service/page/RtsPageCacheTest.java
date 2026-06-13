package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RtsPageCache} LRU eviction and basic operations.
 */
class RtsPageCacheTest {

    private RtsPageCache cache;

    @BeforeEach
    void setUp() {
        cache = new RtsPageCache();
    }

    @AfterEach
    void tearDown() {
        cache.clear();
    }

    // ---- null guards -----------------------------------------------------------

    @Test
    void nullUuidReturnsNull() {
        assertNull(cache.get(null));
    }

    @Test
    void nullUuidPutDoesNothing() {
        cache.put(null, createPage(1));
        assertEquals(0, cache.size());
    }

    @Test
    void nullPagePutDoesNothing() {
        cache.put(UUID.randomUUID(), null);
        assertEquals(0, cache.size());
    }

    @Test
    void nullUuidRemoveDoesNothing() {
        UUID id = UUID.randomUUID();
        cache.put(id, createPage(1));
        cache.remove(null);
        assertEquals(1, cache.size());
    }

    // ---- basic put / get / remove ----------------------------------------------

    @Test
    void putAndGet() {
        UUID id = UUID.randomUUID();
        RtsPageCache.CachedPage page = createPage(100);
        cache.put(id, page);
        assertSame(page, cache.get(id));
        assertEquals(1, cache.size());
    }

    @Test
    void getReturnsNullForUnknownUuid() {
        assertNull(cache.get(UUID.randomUUID()));
    }

    @Test
    void removeRemovesEntry() {
        UUID id = UUID.randomUUID();
        cache.put(id, createPage(1));
        cache.remove(id);
        assertNull(cache.get(id));
        assertEquals(0, cache.size());
    }

    @Test
    void clearEmptiesCache() {
        cache.put(UUID.randomUUID(), createPage(1));
        cache.put(UUID.randomUUID(), createPage(2));
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void putOverwritesExistingEntry() {
        UUID id = UUID.randomUUID();
        RtsPageCache.CachedPage first = createPage(10);
        RtsPageCache.CachedPage second = createPage(20);
        cache.put(id, first);
        cache.put(id, second);
        assertSame(second, cache.get(id));
        assertEquals(1, cache.size());
    }

    @Test
    void sizeTracksCacheEntries() {
        assertEquals(0, cache.size());
        cache.put(UUID.randomUUID(), createPage(1));
        assertEquals(1, cache.size());
        cache.put(UUID.randomUUID(), createPage(2));
        assertEquals(2, cache.size());
    }

    // ---- access order / get behavior -----------------------------------------

    @Test
    void getReturnsSameInstanceAfterRefresh() {
        UUID id = UUID.randomUUID();
        RtsPageCache.CachedPage page = createPage(42);
        cache.put(id, page);
        // Multiple gets should return the same cached instance
        assertSame(cache.get(id), cache.get(id));
    }

    // ---- helpers ---------------------------------------------------------------

    /**
     * Creates a minimal CachedPage with the given data version and no entries.
     */
    static RtsPageCache.CachedPage createPage(long dataVersion) {
        RtsPageCache.CachedPageKey key = new RtsPageCache.CachedPageKey(
                "", RtsStorageSort.NAME, "all", true, 90, false, false);
        return new RtsPageCache.CachedPage(
                key, dataVersion,
                List.of(),          // sortedEntries — empty, no ItemStack needed
                List.of(),          // sortedFluidEntries
                Map.of(),           // counts
                Map.of(),           // namespaceTotals
                List.of("all"));    // categories
    }
}
