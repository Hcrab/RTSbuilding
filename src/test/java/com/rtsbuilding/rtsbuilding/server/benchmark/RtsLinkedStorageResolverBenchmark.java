package com.rtsbuilding.rtsbuilding.server.benchmark;

import com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.mockito.Mockito.*;

/**
 * 极限性能测试 / Extreme Performance Benchmarks for {@link RtsLinkedStorageResolver}.
 *
 * <p>Focuses on pure-logic static helper throughput under extreme cardinality:
 * <ul>
 *   <li>{@link RtsLinkedStorageResolver#buildLinkedSummary} — linear scan over refs</li>
 *   <li>{@link RtsLinkedStorageResolver#sanitizeLinkMode} — tiny branch</li>
 *   <li>{@link RtsLinkedStorageResolver#sanitizeLinkedStoragePriority} — clamp only</li>
 *   <li>{@link RtsLinkedStorageResolver#isExtractOnlyLink} — map lookup + branch</li>
 * </ul>
 */
class RtsLinkedStorageResolverBenchmark {

    private static final int WARMUP = 3;
    private static final int ITERATIONS = 10;

    // ======================================================================
    //  Section 1: buildLinkedSummary — single-ref, multi-ref, mixed
    //  Called every time the UI renders the storage tab summary.
    // ======================================================================

    @Test
    void benchmarkBuildLinkedSummarySingle() {
        long totalNanos = 0;
        for (int i = 0; i < ITERATIONS * 1000; i++) {
            RtsStorageSession session = createMockSession(1);
            long start = System.nanoTime();
            RtsLinkedStorageResolver.buildLinkedSummary(session);
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgNanos = totalNanos / (ITERATIONS * 1000);
        BenchmarkReporter.record("[RtsLinkedStorageResolver] buildLinkedSummary(single ref): avg %d ns/op", avgNanos);
    }

    @Test
    void benchmarkBuildLinkedSummaryTenRefs() {
        long totalNanos = 0;
        for (int i = 0; i < ITERATIONS * 100; i++) {
            RtsStorageSession session = createMockSession(10);
            long start = System.nanoTime();
            RtsLinkedStorageResolver.buildLinkedSummary(session);
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgNanos = totalNanos / (ITERATIONS * 100);
        BenchmarkReporter.record("[RtsLinkedStorageResolver] buildLinkedSummary(10 refs): avg %d ns/op", avgNanos);
    }

    @Test
    void benchmarkBuildLinkedSummaryThousandRefs() {
        long totalNanos = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            RtsStorageSession session = createMockSession(1000);
            long start = System.nanoTime();
            RtsLinkedStorageResolver.buildLinkedSummary(session);
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgNanos = totalNanos / ITERATIONS;
        BenchmarkReporter.record("[RtsLinkedStorageResolver] buildLinkedSummary(1,000 refs): avg %,d ns/op", avgNanos);
    }

    @Test
    void benchmarkBuildLinkedSummaryTenThousandRefs() {
        int refCount = 10_000;
        long totalNanos = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            RtsStorageSession session = createMockSession(refCount);
            long start = System.nanoTime();
            RtsLinkedStorageResolver.buildLinkedSummary(session);
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgNanos = totalNanos / ITERATIONS;
        double nanosPerRef = (double) avgNanos / refCount;
        BenchmarkReporter.record("[RtsLinkedStorageResolver] buildLinkedSummary(%,d refs): avg %,d ns/op  (%.1f ns/ref)",
                refCount, avgNanos, nanosPerRef);
    }

    // ======================================================================
    //  Section 2: buildLinkedSummary — empty session / null session
    // ======================================================================

    @Test
    void benchmarkBuildLinkedSummaryEmptySession() {
        long totalNanos = 0;
        for (int i = 0; i < ITERATIONS * 1000; i++) {
            RtsStorageSession session = createMockSession(0);
            long start = System.nanoTime();
            RtsLinkedStorageResolver.buildLinkedSummary(session);
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        BenchmarkReporter.record("[RtsLinkedStorageResolver] buildLinkedSummary(empty): avg %d ns/op",
                totalNanos / (ITERATIONS * 1000));
    }

    // ======================================================================
    //  Section 3: sanitizeLinkMode — 3-way branch, billions of calls/sec
    // ======================================================================

    @Test
    void benchmarkSanitizeLinkModeBulk() {
        int calls = 10_000_000;

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < calls / 100; i++) {
                RtsLinkedStorageResolver.sanitizeLinkMode((byte) 0);
                RtsLinkedStorageResolver.sanitizeLinkMode((byte) 1);
                RtsLinkedStorageResolver.sanitizeLinkMode((byte) 42);
            }
        }

        long start = System.nanoTime();
        for (int i = 0; i < calls; i++) {
            // Mix of all three outcomes
            RtsLinkedStorageResolver.sanitizeLinkMode((byte) (i % 3));
        }
        long end = System.nanoTime();
        double avgNanos = (double) (end - start) / calls;

        BenchmarkReporter.record("[RtsLinkedStorageResolver] sanitizeLinkMode(), %,d calls: avg %.1f ns/op  (%,.0f ops/sec)",
                calls, avgNanos, 1_000_000_000.0 / avgNanos);
    }

    // ======================================================================
    //  Section 4: sanitizeLinkedStoragePriority — Mth.clamp, billions/sec
    // ======================================================================

    @Test
    void benchmarkSanitizePriorityBulk() {
        int calls = 10_000_000;

        long start = System.nanoTime();
        for (int i = 0; i < calls; i++) {
            int v = (i - 5_000_000) * 2; // range: -10M to 10M
            RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(v);
        }
        long end = System.nanoTime();
        double avgNanos = (double) (end - start) / calls;

        BenchmarkReporter.record("[RtsLinkedStorageResolver] sanitizeLinkedStoragePriority(), %,d calls: avg %.1f ns/op  (%,.0f ops/sec)",
                calls, avgNanos, 1_000_000_000.0 / avgNanos);
    }

    @Test
    void benchmarkSanitizePriorityEdgeCases() {
        int calls = 1_000_000;
        int[] values = {-100_000, -9999, 0, 500, 9999, 100_000};

        long start = System.nanoTime();
        for (int i = 0; i < calls; i++) {
            RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(values[i % values.length]);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / calls;
        BenchmarkReporter.record("[RtsLinkedStorageResolver] sanitizeLinkedStoragePriority (edge cases, %d calls): avg %d ns/op",
                calls, avgNanos);
    }

    // ======================================================================
    //  Section 5: isExtractOnlyLink — map lookup + sanitize branch
    // ======================================================================

    @Test
    void benchmarkIsExtractOnlyLinkBulk() {
        int refCount = 10_000;
        RtsStorageSession session = createMockSession(refCount);
        List<LinkedStorageRef> refs = session.linkedStorages;

        // Warmup
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
        BenchmarkReporter.record("[RtsLinkedStorageResolver] isExtractOnlyLink(%,d refs, repeated): avg %d ns/op  (%,.0f ops/sec)",
                refCount, avgNanos, 1_000_000_000.0 / avgNanos);
    }

    @Test
    void benchmarkIsExtractOnlyLinkNullSession() {
        long start = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            RtsLinkedStorageResolver.isExtractOnlyLink(null, null);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / 1_000_000;
        BenchmarkReporter.record("[RtsLinkedStorageResolver] isExtractOnlyLink(null,null) \u00d7 1M: avg %d ns/op", avgNanos);
    }

    // ======================================================================
    //  Helpers — mirrors the pattern from RtsLinkedStorageResolverTest
    // ======================================================================

    @SuppressWarnings("unchecked")
    private static RtsStorageSession createMockSession(int refCount) {
        RtsStorageSession session = mock(RtsStorageSession.class);
        try {
            Field storagesField = RtsStorageSession.class.getDeclaredField("linkedStorages");
            storagesField.setAccessible(true);
            List<LinkedStorageRef> refs = new ArrayList<>(refCount);
            for (int i = 0; i < refCount; i++) {
                refs.add(createRef());
            }
            storagesField.set(session, refs);

            // Only populate names/modes if there are refs
            if (refCount > 0) {
                Field namesField = RtsStorageSession.class.getDeclaredField("linkedNames");
                namesField.setAccessible(true);
                Map<LinkedStorageRef, String> names = new HashMap<>();
                Map<LinkedStorageRef, Byte> modes = new HashMap<>();
                Random rng = ThreadLocalRandom.current();
                for (LinkedStorageRef ref : refs) {
                    names.put(ref, "Chest_" + rng.nextInt(10000));
                    modes.put(ref, rng.nextBoolean() ? (byte) 0 : (byte) 1);
                }
                namesField.set(session, names);

                Field modesField = RtsStorageSession.class.getDeclaredField("linkedModes");
                modesField.setAccessible(true);
                modesField.set(session, modes);
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
