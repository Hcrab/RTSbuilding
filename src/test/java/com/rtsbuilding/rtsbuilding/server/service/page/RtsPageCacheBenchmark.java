package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.server.benchmark.BenchmarkReporter;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 极限性能测试 / Extreme Performance Benchmarks for {@link RtsPageCache}.
 *
 * <p>Focuses on LRU eviction overhead, hash-table lookup degradation at
 * max capacity, large {@link RtsPageCache.CachedPage} data handling, and
 * mixed workloads. Results are printed to stdout in a structured format.</p>
 *
 * <p>All benchmarks avoid {@code ItemStack} to run in a plain unit-test
 * environment without a bootstrapped Minecraft runtime.</p>
 */
class RtsPageCacheBenchmark {

    private static final int WARMUP = 3;
    private static final int ITERATIONS = 10;
    private static final int MAX_CAPACITY = 256;

    private RtsPageCache cache;

    @BeforeEach
    void setUp() {
        cache = new RtsPageCache();
    }

    @AfterEach
    void tearDown() {
        cache.clear();
    }

    // ======================================================================
    //  Section 1: LRU eviction throughput at max capacity
    //  Simulates 256 concurrent players, then extra players trigger eviction.
    // ======================================================================

    @Test
    void benchmarkLruEvictionAtMaxCapacity() {
        int extraPuts = 5000;

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            runLruBench(MAX_CAPACITY, extraPuts);
        }

        // Measure
        long totalNanos = 0L;
        long totalOps = (long) MAX_CAPACITY + extraPuts;
        for (int i = 0; i < ITERATIONS; i++) {
            totalNanos += runLruBench(MAX_CAPACITY, extraPuts);
        }
        long avgNanos = totalNanos / ITERATIONS;
        double opsPerSec = (double) totalOps / avgNanos * 1_000_000_000.0;

        BenchmarkReporter.record("[RtsPageCache] LRU eviction @ max cap (%d): put %,d total ops \u2192 avg %d ns/op  (%,.0f ops/sec)",
                MAX_CAPACITY, totalOps, avgNanos, opsPerSec);
    }

    private long runLruBench(int capacity, int extraPuts) {
        for (int i = 0; i < capacity; i++) {
            cache.put(UUID.randomUUID(), createEmptyPage(i));
        }
        long start = System.nanoTime();
        for (int i = 0; i < extraPuts; i++) {
            cache.put(UUID.randomUUID(), createEmptyPage(capacity + i));
        }
        long end = System.nanoTime();
        cache.clear();
        return end - start;
    }

    // ======================================================================
    //  Section 2: sequential get() under full cache
    //  Measures LinkedHashMap access-order iterator overhead on repeated get().
    // ======================================================================

    @Test
    void benchmarkSequentialGetUnderFullCache() {
        int lookups = 100_000;

        UUID[] keys = new UUID[MAX_CAPACITY];
        for (int i = 0; i < MAX_CAPACITY; i++) {
            keys[i] = UUID.randomUUID();
            cache.put(keys[i], createEmptyPage(i));
        }

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < lookups / MAX_CAPACITY; i++) {
                for (UUID key : keys) {
                    cache.get(key);
                }
            }
        }

        long start = System.nanoTime();
        for (int i = 0; i < lookups; i++) {
            cache.get(keys[i % MAX_CAPACITY]);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / lookups;

        BenchmarkReporter.record("[RtsPageCache] sequential get() @ full cache: %,d lookups \u2192 avg %d ns/op  (%,.0f ops/sec)",
                lookups, avgNanos, 1_000_000_000.0 / avgNanos);
    }

    // ======================================================================
    //  Section 3: random UUID miss (cache miss every time)
    //  Worst-case: every get() generates a new random UUID that doesn't exist.
    // ======================================================================

    @Test
    void benchmarkRandomLookupMiss() {
        int lookups = 100_000;

        // Fill cache so it's not empty
        for (int i = 0; i < MAX_CAPACITY; i++) {
            cache.put(UUID.randomUUID(), createEmptyPage(i));
        }

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < lookups / 10; i++) {
                cache.get(UUID.randomUUID());
            }
        }

        long start = System.nanoTime();
        for (int i = 0; i < lookups; i++) {
            cache.get(UUID.randomUUID());
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / lookups;

        BenchmarkReporter.record("[RtsPageCache] random UUID miss: %,d lookups \u2192 avg %d ns/op  (%,.0f ops/sec)",
                lookups, avgNanos, 1_000_000_000.0 / avgNanos);
    }

    // ======================================================================
    //  Section 4: large CachedPage — put/get with 10K-item counts map
    //  Simulates a player with hundreds of thousands of items in storage.
    // ======================================================================

    @Test
    void benchmarkLargeCachedPagePutGet() {
        int pageCount = 50;
        int mapSize = 10_000;

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            runLargePageBench(pageCount, mapSize);
        }

        long totalPut = 0L, totalGet = 0L;
        for (int i = 0; i < ITERATIONS; i++) {
            long[] r = runLargePageBench(pageCount, mapSize);
            totalPut += r[0];
            totalGet += r[1];
        }
        long avgPutNanos = totalPut / ITERATIONS / pageCount;
        long avgGetNanos = totalGet / ITERATIONS / pageCount;

        BenchmarkReporter.record("[RtsPageCache] large CachedPage (%,d map entries): put avg %d ns/op, get avg %d ns/op",
                mapSize, avgPutNanos, avgGetNanos);
    }

    private long[] runLargePageBench(int count, int mapSize) {
        UUID[] uuids = new UUID[count];
        RtsPageCache.CachedPage[] pages = new RtsPageCache.CachedPage[count];
        for (int i = 0; i < count; i++) {
            uuids[i] = UUID.randomUUID();
            pages[i] = createLargePage(i, mapSize);
        }
        long putStart = System.nanoTime();
        for (int i = 0; i < count; i++) {
            cache.put(uuids[i], pages[i]);
        }
        long putEnd = System.nanoTime();
        long getStart = System.nanoTime();
        for (int i = 0; i < count; i++) {
            cache.get(uuids[i]);
        }
        long getEnd = System.nanoTime();
        cache.clear();
        return new long[]{putEnd - putStart, getEnd - getStart};
    }

    // ======================================================================
    //  Section 5: mixed workload — put/get/remove interleaved
    //  Simulates real-world usage: players joining/leaving, cache churn.
    // ======================================================================

    @Test
    void benchmarkMixedWorkload() {
        int ops = 50_000;

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            runMixedBench(ops);
        }

        long start = System.nanoTime();
        runMixedBench(ops);
        long end = System.nanoTime();
        long avgNanos = (end - start) / ops;

        BenchmarkReporter.record("[RtsPageCache] mixed workload (put+get+remove): %,d ops \u2192 avg %d ns/op  (%,.0f ops/sec)",
                ops, avgNanos, 1_000_000_000.0 / avgNanos);
    }

    private void runMixedBench(int ops) {
        Random rng = ThreadLocalRandom.current();
        for (int i = 0; i < ops; i++) {
            switch (rng.nextInt(4)) {
                case 0 -> cache.put(UUID.randomUUID(), createEmptyPage(rng.nextInt(1000)));
                case 1 -> cache.get(UUID.randomUUID());
                case 2 -> cache.remove(UUID.randomUUID());
                case 3 -> cache.size();
            }
        }
        cache.clear();
    }

    // ======================================================================
    //  Section 6: remove() throughput under full cache
    // ======================================================================

    @Test
    void benchmarkRemoveUnderFullCache() {
        int removes = 10_000;

        // Fill then measure
        long totalNanos = 0L;
        for (int i = 0; i < ITERATIONS; i++) {
            UUID[] keys = new UUID[removes];
            for (int j = 0; j < removes; j++) {
                keys[j] = UUID.randomUUID();
                cache.put(keys[j], createEmptyPage(j));
            }
            long start = System.nanoTime();
            for (int j = 0; j < removes; j++) {
                cache.remove(keys[j]);
            }
            long end = System.nanoTime();
            totalNanos += (end - start);
            cache.clear();
        }
        long avgNanos = totalNanos / ITERATIONS / removes;

        BenchmarkReporter.record("[RtsPageCache] remove() \u00d7 %,d: avg %d ns/op  (%,.0f ops/sec)",
                removes, avgNanos, 1_000_000_000.0 / avgNanos);
    }

    // ======================================================================
    //  Section 7: clear() throughput
    // ======================================================================

    @Test
    void benchmarkClear() {
        long totalNanos = 0L;
        for (int i = 0; i < ITERATIONS; i++) {
            for (int j = 0; j < MAX_CAPACITY; j++) {
                cache.put(UUID.randomUUID(), createEmptyPage(j));
            }
            long start = System.nanoTime();
            cache.clear();
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgNanos = totalNanos / ITERATIONS;

        BenchmarkReporter.record("[RtsPageCache] clear() @ full cache: avg %d ns/op", avgNanos);
    }

    // ======================================================================
    //  Helpers
    // ======================================================================

    private static RtsPageCache.CachedPage createEmptyPage(long dataVersion) {
        var key = new RtsPageCache.CachedPageKey(
                "", RtsStorageSort.NAME, "all", true, 90, false, false);
        return new RtsPageCache.CachedPage(
                key, dataVersion,
                List.of(),           // sortedEntries — empty, no ItemStack
                List.of(),           // sortedFluidEntries
                Map.of(),            // counts
                Map.of(),            // namespaceTotals
                List.of("all"));
    }

    private static RtsPageCache.CachedPage createLargePage(long dataVersion, int mapSize) {
        var key = new RtsPageCache.CachedPageKey(
                "", RtsStorageSort.NAME, "all", true, 90, false, false);
        Map<String, Long> counts = new HashMap<>(mapSize);
        Map<String, Long> namespaceTotals = new HashMap<>();
        for (int i = 0; i < mapSize; i++) {
            counts.put("minecraft:item_" + i, (long) i * 64L);
        }
        namespaceTotals.put("minecraft", (long) mapSize * 32L);
        return new RtsPageCache.CachedPage(
                key, dataVersion,
                List.of(),
                List.of(),
                counts,
                namespaceTotals,
                List.of("all", "minecraft", "building_blocks"));
    }
}
