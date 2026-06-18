package com.rtsbuilding.rtsbuilding.server.benchmark.recententry;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.storage.RecentEntry;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageRecentEntries;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Extreme Performance Benchmarks for {@link RtsStorageRecentEntries}.
 *
 * <p>Focuses on push, dedupe, merge, and trim throughput of the recent-entries
 * deque under various access patterns: always-new items, always-same item,
 * mixed items, and fluid entries. All benchmarks avoid Minecraft's
 * {@code BuiltInRegistries} by using the string-based API.</p>
 */
class RtsStorageRecentEntriesBenchmark {

    private static final int WARMUP = 3;
    private static final int ITERATIONS = 10;

    private RtsStorageSession session;

    /**
     * Global JIT warmup — exercises all code paths.
     */
    @BeforeAll
    static void globalWarmUp() {
        RtsStorageSession s = new RtsStorageSession();

        // Warm up pushRecentEntry — always new items
        for (int i = 0; i < 5_000; i++) {
            RtsStorageRecentEntries.recordRecentItem(
                    s, "minecraft:diamond_" + (i % 1000), S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 64L);
        }

        // Warm up same-key dedupe + merge
        s.uiMemory.clearRecentEntries();
        for (int i = 0; i < 5_000; i++) {
            RtsStorageRecentEntries.recordRecentItem(
                    s, "minecraft:diamond", S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
        }

        // Warm up fluid entries
        s.uiMemory.clearRecentEntries();
        for (int i = 0; i < 5_000; i++) {
            RtsStorageRecentEntries.recordRecentItem(
                    s, "minecraft:water_" + (i % 500), S2CRtsStoragePagePayload.RECENT_FLUID_PLACED, 1000L);
        }

        // Warm up mixed-kind entries (item vs fluid sharing same id pattern)
        s.uiMemory.clearRecentEntries();
        for (int i = 0; i < 5_000; i++) {
            byte kind = (i % 2 == 0)
                    ? S2CRtsStoragePagePayload.RECENT_ITEM_PLACED
                    : S2CRtsStoragePagePayload.RECENT_FLUID_PLACED;
            RtsStorageRecentEntries.recordRecentItem(
                    s, "minecraft:nether_star", kind, 1L);
        }

        // Warm up with null/blank/invalid entries (fast-path null checks)
        s.uiMemory.clearRecentEntries();
        for (int i = 0; i < 100_000; i++) {
            RtsStorageRecentEntries.recordRecentItem(s, (String) null, S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
            RtsStorageRecentEntries.recordRecentItem(s, "", S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
            RtsStorageRecentEntries.recordRecentItem(s, "  ", S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
        }
    }

    @BeforeEach
    void setUp() {
        System.gc();
        session = new RtsStorageSession();
    }

    // ======================================================================
    //  1. Always-new items — no dedupe, pure addFirst + trim
    // ======================================================================

    @Test
    void benchmarkPushAlwaysNew() {
        int count = 100_000;
        String[] ids = generateKeys(count);

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            session.uiMemory.clearRecentEntries();
            for (int i = 0; i < count; i++) {
                RtsStorageRecentEntries.recordRecentItem(
                        session, ids[i], S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 42L);
            }
        }

        long totalNanos = 0;
        for (int iter = 0; iter < ITERATIONS; iter++) {
            session.uiMemory.clearRecentEntries();
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                RtsStorageRecentEntries.recordRecentItem(
                        session, ids[i], S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 42L);
            }
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgTotal = totalNanos / ITERATIONS;
        System.out.println(String.format("[RtsStorageRecentEntries] push always-new (%,d ops): avg %,d ns total, ~%,d ns/op",
                count, avgTotal, avgTotal / count));
    }

    // ======================================================================
    //  2. Always-same item — worst case dedupe + merge every push
    // ======================================================================

    @Test
    void benchmarkPushSameItem() {
        int count = 100_000;

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            session.uiMemory.clearRecentEntries();
            for (int i = 0; i < count; i++) {
                RtsStorageRecentEntries.recordRecentItem(
                        session, "minecraft:diamond", S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
            }
        }

        long totalNanos = 0;
        for (int iter = 0; iter < ITERATIONS; iter++) {
            session.uiMemory.clearRecentEntries();
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                RtsStorageRecentEntries.recordRecentItem(
                        session, "minecraft:diamond", S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
            }
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgTotal = totalNanos / ITERATIONS;
        System.out.printf("[RtsStorageRecentEntries] push same item (%,d ops): avg %,d ns total, ~%,d ns/op%n",
                count, avgTotal, avgTotal / count);
    }

    // ======================================================================
    //  3. Mixed items — realistic pattern: spread across id space
    // ======================================================================

    @Test
    void benchmarkPushMixedItems() {
        int count = 100_000;
        int uniqueIds = 1_000;
        String[] ids = generateKeys(uniqueIds);

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            session.uiMemory.clearRecentEntries();
            for (int i = 0; i < count; i++) {
                RtsStorageRecentEntries.recordRecentItem(
                        session, ids[i % uniqueIds], S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
            }
        }

        long totalNanos = 0;
        for (int iter = 0; iter < ITERATIONS; iter++) {
            session.uiMemory.clearRecentEntries();
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                RtsStorageRecentEntries.recordRecentItem(
                        session, ids[i % uniqueIds], S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
            }
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgTotal = totalNanos / ITERATIONS;
        System.out.printf("[RtsStorageRecentEntries] push mixed (%,d ops, %,d unique): avg %,d ns total, ~%,d ns/op%n",
                count, uniqueIds, avgTotal, avgTotal / count);
    }

    // ======================================================================
    //  4. Fluid entries — different kind, same structure
    // ======================================================================

    @Test
    void benchmarkPushFluidEntries() {
        int count = 50_000;
        String[] ids = generateKeys(500);

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            session.uiMemory.clearRecentEntries();
            for (int i = 0; i < count; i++) {
                RtsStorageRecentEntries.recordRecentItem(
                        session, ids[i % ids.length], S2CRtsStoragePagePayload.RECENT_FLUID_PLACED, 1000L);
            }
        }

        long totalNanos = 0;
        for (int iter = 0; iter < ITERATIONS; iter++) {
            session.uiMemory.clearRecentEntries();
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                RtsStorageRecentEntries.recordRecentItem(
                        session, ids[i % ids.length], S2CRtsStoragePagePayload.RECENT_FLUID_PLACED, 1000L);
            }
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgTotal = totalNanos / ITERATIONS;
        System.out.println(String.format("[RtsStorageRecentEntries] push fluid (%,d ops, %,d unique): avg %,d ns total, ~%,d ns/op",
                count, ids.length, avgTotal, avgTotal / count));
    }

    // ======================================================================
    //  5. Mixed kinds — same item id but alternating item/fluid kind
    //     Tests the `sameRecentKey` branch (kind comparison)
    // ======================================================================

    @Test
    void benchmarkPushMixedKinds() {
        int count = 100_000;
        byte[] kinds = {S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, S2CRtsStoragePagePayload.RECENT_FLUID_PLACED};

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            session.uiMemory.clearRecentEntries();
            for (int i = 0; i < count; i++) {
                RtsStorageRecentEntries.recordRecentItem(
                        session, "minecraft:nether_star", kinds[i % 2], 1L);
            }
        }

        long totalNanos = 0;
        for (int iter = 0; iter < ITERATIONS; iter++) {
            session.uiMemory.clearRecentEntries();
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                RtsStorageRecentEntries.recordRecentItem(
                        session, "minecraft:nether_star", kinds[i % 2], 1L);
            }
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgTotal = totalNanos / ITERATIONS;
        System.out.println(String.format("[RtsStorageRecentEntries] push mixed kinds (%,d ops): avg %,d ns total, ~%,d ns/op",
                count, avgTotal, avgTotal / count));
    }

    // ======================================================================
    //  6. Bulk initial fill — fill from zero to full capacity
    // ======================================================================

    @Test
    void benchmarkBulkFillToCapacity() {
        int FILL_COUNT = 24; // RECENT_ENTRY_LIMIT

        long totalNanos = 0;
        for (int iter = 0; iter < ITERATIONS * 1000; iter++) {
            session.uiMemory.clearRecentEntries();
            long start = System.nanoTime();
            for (int i = 0; i < FILL_COUNT; i++) {
                RtsStorageRecentEntries.recordRecentItem(
                        session, "minecraft:item_" + i, S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
            }
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgNanos = totalNanos / (ITERATIONS * 1000);
        System.out.println(String.format("[RtsStorageRecentEntries] fill to capacity (%,d entries): avg %,d ns/op",
                FILL_COUNT, avgNanos));
    }

    // ======================================================================
    //  7. Null/skip fast-path — null id, blank id, zero amount
    // ======================================================================

    @Test
    void benchmarkNullGuard() {
        int count = 1_000_000;

        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            RtsStorageRecentEntries.recordRecentItem(session, (String) null,
                    S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
            RtsStorageRecentEntries.recordRecentItem(session, "",
                    S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
            RtsStorageRecentEntries.recordRecentItem(session, "  ",
                    S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 0L);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / (count * 3);
        System.out.println(String.format("[RtsStorageRecentEntries] null/blank guards \u00d7 %,d: avg %,d ns/op",
                count * 3, avgNanos));
    }

    // ======================================================================
    //  Helpers
    // ======================================================================

    private static String[] generateKeys(int count) {
        String[] keys = new String[count];
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            keys[i] = "minecraft:item_" + rng.nextInt(Integer.MAX_VALUE);
        }
        return keys;
    }
}
