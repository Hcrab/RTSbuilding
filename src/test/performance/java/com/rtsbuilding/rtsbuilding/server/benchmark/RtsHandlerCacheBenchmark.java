package com.rtsbuilding.rtsbuilding.server.benchmark;

import com.rtsbuilding.rtsbuilding.server.storage.RtsHandlerCache;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.*;

import static org.mockito.Mockito.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 极限性能测试 / Extreme Performance Benchmarks for {@link RtsHandlerCache}.
 *
 * <p>Focuses on map operation throughput under high cardinality:
 * <ul>
 *   <li>{@link RtsHandlerCache#getAvailableItems} — bulk dump into caller's map</li>
 *   <li>{@link RtsHandlerCache#getCount} — HashMap lookup by string key</li>
 *   <li>{@link RtsHandlerCache#invalidate} / {@link RtsHandlerCache#release} — state reset</li>
 *   <li>{@link RtsHandlerCache#getCachedSlotCount} / {@link RtsHandlerCache#isDirty} — trivial getter</li>
 * </ul>
 *
 * <p>{@code countsByItem} map is seeded via reflection to avoid triggering
 * Minecraft's {@code ItemStack} static initializer through
 * {@code CachedSlot.EMPTY}. The reflection is safe because the map type
 * is a plain {@code HashMap<String, Long>}.</p>
 */
class RtsHandlerCacheBenchmark {

    private static final int WARMUP = 3;
    private static final int ITERATIONS = 10;

    private RtsHandlerCache cache;

    @BeforeEach
    void setUp() {
        cache = new RtsHandlerCache();
    }

    // ======================================================================
    //  Section 1: getAvailableItems() — bulk dump with 100K entries
    //  Hot path: called per-tick to aggregate counts for the UI.
    // ======================================================================

    @Test
    void benchmarkGetAvailableItemsLargeMap() {
        int entryCount = 100_000;
        seedCounts(cache, entryCount);

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            drainAvailableItems(cache);
        }

        long totalNanos = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            Map<String, Long> out = new HashMap<>(entryCount);
            long start = System.nanoTime();
            cache.getAvailableItems(out);
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgNanos = totalNanos / ITERATIONS;
        BenchmarkReporter.record("[RtsHandlerCache] getAvailableItems(%,d entries): avg %d ns/op  (%,.0f ops/sec)",
                entryCount, avgNanos, 1_000_000_000.0 / avgNanos);
    }

    @Test
    void benchmarkGetAvailableItemsSmallMap() {
        int entryCount = 256;
        seedCounts(cache, entryCount);

        long totalNanos = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            Map<String, Long> out = new HashMap<>(entryCount);
            long start = System.nanoTime();
            cache.getAvailableItems(out);
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgNanos = totalNanos / ITERATIONS;
        BenchmarkReporter.record("[RtsHandlerCache] getAvailableItems(%d entries): avg %d ns/op  (%,.0f ops/sec)",
                entryCount, avgNanos, 1_000_000_000.0 / avgNanos);
    }

    // ======================================================================
    //  Section 2: getCount() string lookup throughput
    //  Hot path: called for every item in every tick in filter loops.
    // ======================================================================

    @Test
    void benchmarkGetCountLookup() {
        int entryCount = 100_000;
        seedCounts(cache, entryCount);

        int lookups = 1_000_000;
        String[] keys = generateKeys(entryCount);

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < lookups / 10; i++) {
                cache.getCount(keys[i % entryCount]);
            }
        }

        long start = System.nanoTime();
        for (int i = 0; i < lookups; i++) {
            cache.getCount(keys[i % entryCount]);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / lookups;
        BenchmarkReporter.record("[RtsHandlerCache] getCount(%,d entries, %,d lookups): avg %d ns/op  (%,.0f ops/sec)",
                entryCount, lookups, avgNanos, 1_000_000_000.0 / avgNanos);
    }

    @Test
    void benchmarkGetCountNullLookup() {
        // null is handled by countsByItem.getOrDefault(null, 0L)
        long start = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            cache.getCount((String) null);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / 1_000_000;
        BenchmarkReporter.record("[RtsHandlerCache] getCount(null) \u00d7 1M: avg %d ns/op", avgNanos);
    }

    @Test
    void benchmarkGetCountByItem() {
        seedCounts(cache, 100);
        var item = mock(net.minecraft.world.item.Item.class);
        when(item.toString()).thenReturn("minecraft:diamond");
        long start = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            cache.getCount(item);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / 100_000;
        BenchmarkReporter.record("[RtsHandlerCache] getCount(Item mock) \u00d7 100K: avg %d ns/op", avgNanos);
    }

    // ======================================================================
    //  Section 3: invalidate() throughput
    // ======================================================================

    @Test
    void benchmarkInvalidate() {
        long totalNanos = 0;
        for (int i = 0; i < ITERATIONS * 100; i++) {
            cache.invalidate();
            seedCounts(cache, 10_000);
            long start = System.nanoTime();
            cache.invalidate();
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgNanos = totalNanos / (ITERATIONS * 100);
        BenchmarkReporter.record("[RtsHandlerCache] invalidate() (after 10K entries): avg %d ns/op", avgNanos);
    }

    @Test
    void benchmarkInvalidateEmpty() {
        long totalNanos = 0;
        int calls = 1_000_000;
        for (int i = 0; i < calls; i++) {
            long start = System.nanoTime();
            cache.invalidate();
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        BenchmarkReporter.record("[RtsHandlerCache] invalidate() (empty cache), %,d calls: avg %d ns/op",
                calls, totalNanos / calls);
    }

    // ======================================================================
    //  Section 4: release() throughput
    // ======================================================================

    @Test
    void benchmarkRelease() {
        seedCounts(cache, 50_000);
        long start = System.nanoTime();
        cache.release();
        long end = System.nanoTime();
        BenchmarkReporter.record("[RtsHandlerCache] release() (50K entries): %d ns", end - start);
    }

    // ======================================================================
    //  Section 5: trivial getter throughput
    // ======================================================================

    @Test
    void benchmarkGetterOverhead() {
        // getCachedSlotCount
        long start = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            cache.getCachedSlotCount();
        }
        long end = System.nanoTime();
        BenchmarkReporter.record("[RtsHandlerCache] getCachedSlotCount() \u00d7 1M: avg %d ns/op", (end - start) / 1_000_000);

        // isDirty
        start = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            cache.isDirty();
        }
        end = System.nanoTime();
        BenchmarkReporter.record("[RtsHandlerCache] isDirty() \u00d7 1M: avg %d ns/op", (end - start) / 1_000_000);

        // clearDirty
        start = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            cache.clearDirty();
        }
        end = System.nanoTime();
        BenchmarkReporter.record("[RtsHandlerCache] clearDirty() \u00d7 1M: avg %d ns/op", (end - start) / 1_000_000);
    }

    // ======================================================================
    //  Helpers
    // ======================================================================

    /**
     * Seeds {@code countsByItem} via reflection with {@code count} entries.
     * This avoids calling {@code update(IItemHandler)} which would trigger
     * Minecraft's {@code BuiltInRegistries.ITEM} and {@code CachedSlot.EMPTY}.
     */
    private static void seedCounts(RtsHandlerCache cache, int count) {
        try {
            Field countsField = RtsHandlerCache.class.getDeclaredField("countsByItem");
            countsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Long> counts = (Map<String, Long>) countsField.get(cache);
            counts.clear();
            Random rng = ThreadLocalRandom.current();
            for (int i = 0; i < count; i++) {
                counts.put("minecraft:item_" + i, (long) (rng.nextInt(1000) + 1));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to seed cache counts", e);
        }
    }

    private static void drainAvailableItems(RtsHandlerCache cache) {
        cache.getAvailableItems(new HashMap<>());
    }

    private static String[] generateKeys(int count) {
        String[] keys = new String[count];
        for (int i = 0; i < count; i++) {
            keys[i] = "minecraft:item_" + i;
        }
        return keys;
    }
}
