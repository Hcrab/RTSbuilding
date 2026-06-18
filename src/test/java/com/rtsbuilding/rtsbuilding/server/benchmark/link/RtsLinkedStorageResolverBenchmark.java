package com.rtsbuilding.rtsbuilding.server.benchmark.link;

import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.session.LinkedStorageInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.mockito.Mockito.mock;

/**
 * Extreme Performance Benchmarks for {@link RtsLinkedStorageResolver}.
 *
 * <p>Focuses on pure-logic static helper throughput under extreme cardinality.
 * All methods under test are static and stateless.</p>
 */
class RtsLinkedStorageResolverBenchmark {

    private static final int WARMUP = 3;
    private static final int ITERATIONS = 10;

    /**
     * Global JIT warmup — runs once before all tests. Exercises all code paths
     * so the JIT compiler compiles them before measurement begins.
     */
    @BeforeAll
    static void globalWarmUp() {
        // Warm up buildLinkedSummary at small to moderate sizes
        for (int refs : new int[]{0, 1, 10, 100}) {
            RtsStorageSession session = createMockSession(refs);
            for (int i = 0; i < 50; i++) {
                RtsLinkedStorageResolver.buildLinkedSummary(session);
            }
        }

        // Warm up isExtractOnlyLink (100 refs only, enough for JIT)
        RtsStorageSession session = createMockSession(100);
        for (LinkedStorageRef ref : getSessionRefs(session)) {
            RtsLinkedStorageResolver.isExtractOnlyLink(session, ref);
        }

        // Warm up sanitize methods — moderate iterations
        for (int i = 0; i < 100_000; i++) {
            RtsLinkedStorageResolver.sanitizeLinkMode((byte) (i % 3));
            RtsLinkedStorageResolver.sanitizeLinkedStoragePriority((i - 50_000) * 2);
        }
    }

    @BeforeEach
    void setUp() {
        System.gc();
    }

    /**
     * Extracts linkedStorages list from mock session.
     */
    @SuppressWarnings("unchecked")
    private static List<LinkedStorageRef> getSessionRefs(RtsStorageSession session) {
        return session.linkedStorageInfo.getAll();
    }

    // ======================================================================
    //  Section 1: buildLinkedSummary — single-ref, multi-ref, mixed
    // ======================================================================

    @Test
    void benchmarkBuildLinkedSummarySingle() {
        int innerIters = ITERATIONS * 1000;

        for (int w = 0; w < WARMUP; w++) {
            RtsStorageSession session = createMockSession(1);
            for (int i = 0; i < innerIters / 100; i++) {
                RtsLinkedStorageResolver.buildLinkedSummary(session);
            }
        }

        long totalNanos = 0;
        for (int i = 0; i < innerIters; i++) {
            RtsStorageSession session = createMockSession(1);
            long start = System.nanoTime();
            RtsLinkedStorageResolver.buildLinkedSummary(session);
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgNanos = totalNanos / ITERATIONS;
        System.out.println(String.format("[RtsLinkedStorageResolver] buildLinkedSummary(single ref): avg %,d ns/op", avgNanos));
    }

    @Test
    void benchmarkBuildLinkedSummaryTenRefs() {
        int innerIters = ITERATIONS * 100;

        for (int w = 0; w < WARMUP; w++) {
            RtsStorageSession session = createMockSession(10);
            for (int i = 0; i < innerIters / 100; i++) {
                RtsLinkedStorageResolver.buildLinkedSummary(session);
            }
        }

        long totalNanos = 0;
        for (int i = 0; i < innerIters; i++) {
            RtsStorageSession session = createMockSession(10);
            long start = System.nanoTime();
            RtsLinkedStorageResolver.buildLinkedSummary(session);
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgNanos = totalNanos / ITERATIONS;
        System.out.println(String.format("[RtsLinkedStorageResolver] buildLinkedSummary(10 refs): avg %,d ns/op", avgNanos));
    }

    @Test
    void benchmarkBuildLinkedSummaryThousandRefs() {
        for (int w = 0; w < WARMUP; w++) {
            RtsStorageSession session = createMockSession(1000);
            RtsLinkedStorageResolver.buildLinkedSummary(session);
        }

        long totalNanos = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            RtsStorageSession session = createMockSession(1000);
            long start = System.nanoTime();
            RtsLinkedStorageResolver.buildLinkedSummary(session);
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgNanos = totalNanos / ITERATIONS;
        System.out.println(String.format("[RtsLinkedStorageResolver] buildLinkedSummary(1,000 refs): avg %,d ns/op", avgNanos));
    }

    @Test
    void benchmarkBuildLinkedSummaryTenThousandRefs() {
        int REF_COUNT = 10_000;

        for (int w = 0; w < WARMUP; w++) {
            RtsStorageSession session = createMockSession(REF_COUNT);
            RtsLinkedStorageResolver.buildLinkedSummary(session);
        }

        long totalNanos = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            RtsStorageSession session = createMockSession(REF_COUNT);
            long start = System.nanoTime();
            RtsLinkedStorageResolver.buildLinkedSummary(session);
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgNanos = totalNanos / ITERATIONS;
        System.out.println(String.format("[RtsLinkedStorageResolver] buildLinkedSummary(%,d refs): avg %,d ns/op  (%.1f ns/ref)",
                REF_COUNT, avgNanos, (double) avgNanos / REF_COUNT));
    }

    @Test
    void benchmarkBuildLinkedSummaryEmpty() {
        int innerIters = ITERATIONS * 1000;

        for (int w = 0; w < WARMUP; w++) {
            RtsStorageSession session = createMockSession(0);
            for (int i = 0; i < innerIters / 100; i++) {
                RtsLinkedStorageResolver.buildLinkedSummary(session);
            }
        }

        long totalNanos = 0;
        for (int i = 0; i < innerIters; i++) {
            RtsStorageSession session = createMockSession(0);
            long start = System.nanoTime();
            RtsLinkedStorageResolver.buildLinkedSummary(session);
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgNanos = totalNanos / ITERATIONS;
        System.out.println(String.format("[RtsLinkedStorageResolver] buildLinkedSummary(empty): avg %,d ns/op",
                avgNanos));
    }

    @Test
    void benchmarkSanitizeLinkMode() {
        int CALLS = 10_000_000;

        // Warmup (global warmup already did this, but local warmup for safety)
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < CALLS / 100; i++) {
                RtsLinkedStorageResolver.sanitizeLinkMode((byte) 0);
                RtsLinkedStorageResolver.sanitizeLinkMode((byte) 1);
                RtsLinkedStorageResolver.sanitizeLinkMode((byte) 42);
            }
        }

        long start = System.nanoTime();
        for (int i = 0; i < CALLS; i++) {
            RtsLinkedStorageResolver.sanitizeLinkMode((byte) (i % 3));
        }
        long end = System.nanoTime();
        System.out.println(String.format("[RtsLinkedStorageResolver] sanitizeLinkMode(), %,d calls: avg %.1f ns/op  (%,.0f ops/sec)",
                CALLS, (double) (end - start) / CALLS, 1_000_000_000.0 / ((double) (end - start) / CALLS)));
    }

    @Test
    void benchmarkSanitizePriorityBulk() {
        int CALLS = 10_000_000;

        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < CALLS / 100; i++) {
                int v = (i - 5_000_000) * 2;
                RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(v);
            }
        }

        long start = System.nanoTime();
        for (int i = 0; i < CALLS; i++) {
            int v = (i - 5_000_000) * 2;
            RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(v);
        }
        long end = System.nanoTime();
        System.out.println(String.format("[RtsLinkedStorageResolver] sanitizeLinkedStoragePriority(), %,d calls: avg %.1f ns/op  (%,.0f ops/sec)",
                CALLS, (double) (end - start) / CALLS, 1_000_000_000.0 / ((double) (end - start) / CALLS)));
    }

    @Test
    void benchmarkSanitizePriorityEdgeCases() {
        int CALLS = 1_000_000;
        int[] values = {-100_000, -9999, 0, 500, 9999, 100_000};

        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < CALLS / 100; i++) {
                RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(values[i % values.length]);
            }
        }

        long start = System.nanoTime();
        for (int i = 0; i < CALLS; i++) {
            RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(values[i % values.length]);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / CALLS;
        System.out.println(String.format("[RtsLinkedStorageResolver] sanitizeLinkedStoragePriority (edge cases, %,d calls): avg %,d ns/op",
                CALLS, avgNanos));
    }

    @Test
    void benchmarkIsExtractOnlyLinkBulk() {
        int REF_COUNT = 10_000;
        RtsStorageSession session = createMockSession(REF_COUNT);
        List<LinkedStorageRef> refs = getSessionRefs(session);

        for (int w = 0; w < WARMUP; w++) {
            for (LinkedStorageRef ref : refs) {
                RtsLinkedStorageResolver.isExtractOnlyLink(session, ref);
            }
        }

        long totalNanos = 0;
        long totalCalls = 0;
        for (int i = 0; i < ITERATIONS * 10; i++) {
            long start = System.nanoTime();
            for (LinkedStorageRef ref : refs) {
                RtsLinkedStorageResolver.isExtractOnlyLink(session, ref);
            }
            long end = System.nanoTime();
            totalNanos += (end - start);
            totalCalls += refs.size();
        }
        long avgNanos = totalNanos / totalCalls;
        System.out.println(String.format("[RtsLinkedStorageResolver] isExtractOnlyLink(%,d refs, repeated): avg %,d ns/op  (%,.0f ops/sec)",
                totalCalls, avgNanos, 1_000_000_000.0 / avgNanos));
    }

    @Test
    void benchmarkIsExtractOnlyLinkNull() {
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < 10_000; i++) {
                RtsLinkedStorageResolver.isExtractOnlyLink(null, null);
            }
        }

        long start = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            RtsLinkedStorageResolver.isExtractOnlyLink(null, null);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / 1_000_000;
        System.out.println(String.format("[RtsLinkedStorageResolver] isExtractOnlyLink(null,null) \u00d7 1M: avg %,d ns/op", avgNanos));
    }

    // ======================================================================
    //  Helpers
    // ======================================================================

    @SuppressWarnings("unchecked")
    private static RtsStorageSession createMockSession(int refCount) {
        RtsStorageSession session = mock(RtsStorageSession.class);
        try {
            Field infoField = RtsStorageSession.class.getDeclaredField("linkedStorageInfo");
            infoField.setAccessible(true);
            LinkedStorageInfo info = new LinkedStorageInfo();
            infoField.set(session, info);

            Random rng = ThreadLocalRandom.current();
            for (int i = 0; i < refCount; i++) {
                LinkedStorageRef ref = createRef();
                info.add(ref, rng.nextBoolean() ? (byte) 0 : (byte) 1, 0);
                info.setName(ref, "Chest_" + rng.nextInt(10000));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mock session", e);
        }
        return session;
    }

    @SuppressWarnings("unchecked")
    private static LinkedStorageRef createRef() {
        ResourceKey<Level> dim = (ResourceKey<Level>) mock(ResourceKey.class);
        return new LinkedStorageRef(dim, BlockPos.ZERO);
    }
}
