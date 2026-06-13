package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player LRU page cache for the storage browser.
 *
 * <p>Avoids O(n log n) sort + filter rebuild on pure pagination operations.
 * Each player has at most one cached page entry; when storage data changes
 * (detected via {@code dataVersion}), the cache is invalidated and the next
 * request triggers a full rebuild.
 *
 * <p>The internal map is an LRU-backed structure (access-order iteration)
 * so that infrequently accessed players are evicted first when memory
 * pressure is high. The default limit is {@link #MAX_CACHED_PLAYERS}.
 */
public final class RtsPageCache {

    public static final RtsPageCache INSTANCE = new RtsPageCache();

    /** Maximum number of players with cached page data. */
    private static final int MAX_CACHED_PLAYERS = 256;

    /** Lock-free LRU: access-order iteration lets us find the oldest entry. */
    private final Map<UUID, CachedPage> cache = new LinkedHashMap<>(
            16, 0.75f, true /* accessOrder */);

    /**
     * Package-private constructor — external code should use {@link #INSTANCE}.
     * Tests in the same package may create independent instances.
     */
    RtsPageCache() {
    }

    /**
     * Key that determines cache validity.
     */
    public record CachedPageKey(
            String search, RtsStorageSort sort, String category, boolean ascending,
            int pageSize, boolean pinyinSearchEnabled, boolean includePlayerInventory
    ) {}

    /**
     * Cached result of the expensive sort + filter + categories build phase.
     */
    public record CachedPage(
            CachedPageKey key,
            long dataVersion,
            List<Entry> sortedEntries,
            List<FluidEntry> sortedFluidEntries,
            Map<String, Long> counts,
            Map<String, Long> namespaceTotals,
            List<String> categories
    ) {}

    /**
     * Retrieves the cached page for a player, returning null if the cache
     * is empty or the data version is stale.
     */
    public synchronized CachedPage get(UUID playerUuid) {
        return playerUuid == null ? null : cache.get(playerUuid);
    }

    /**
     * Stores a page result in the cache, evicting the least-recently-accessed
     * player if the cache is full.
     */
    public synchronized void put(UUID playerUuid, CachedPage page) {
        if (playerUuid == null || page == null) {
            return;
        }
        // Evict oldest if at capacity (the eldest entry in access-order map)
        if (cache.size() >= MAX_CACHED_PLAYERS && !cache.containsKey(playerUuid)) {
            var it = cache.entrySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        cache.put(playerUuid, page);
    }

    /**
     * Removes a player's cached page data so the GC can reclaim memory
     * when they disable RTS or log out.
     */
    public synchronized void remove(UUID playerUuid) {
        if (playerUuid != null) {
            cache.remove(playerUuid);
        }
    }

    /** Removes all cached entries. */
    public synchronized void clear() {
        cache.clear();
    }

    /** Returns the number of currently cached players. */
    public synchronized int size() {
        return cache.size();
    }
}
