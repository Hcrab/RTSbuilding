package com.rtsbuilding.rtsbuilding.server.benchmark.recententry;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageRecentEntries;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH microbenchmarks for {@link RtsStorageRecentEntries}.
 *
 * <p>Covers same-item dedupe+merge, always-new inserts, mixed items,
 * fluid entries, mixed-kind collisions, bulk fill, and null-guard fast paths.
 * All benchmarks avoid Minecraft's {@code BuiltInRegistries} by using the
 * string-based API.</p>
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RtsStorageRecentEntriesJmhBenchmark {

    // ======================================================================
    //  Always-same item — dedupe + merge + trim per push
    // ======================================================================

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(100_000)
    public void pushSameItem() {
        RtsStorageSession s = new RtsStorageSession();
        for (int i = 0; i < 100_000; i++) {
            RtsStorageRecentEntries.recordRecentItem(
                    s, "minecraft:diamond", S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
        }
    }

    // ======================================================================
    //  Always-new items — no dedupe, pure addFirst + trim
    // ======================================================================

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(100_000)
    public void pushAlwaysNew() {
        RtsStorageSession s = new RtsStorageSession();
        for (int i = 0; i < 100_000; i++) {
            RtsStorageRecentEntries.recordRecentItem(
                    s, "minecraft:item_" + i, S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 42L);
        }
    }

    // ======================================================================
    //  Mixed items — realistic access pattern
    // ======================================================================

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(100_000)
    public void pushMixedItems() {
        RtsStorageSession s = new RtsStorageSession();
        for (int i = 0; i < 100_000; i++) {
            RtsStorageRecentEntries.recordRecentItem(
                    s, "minecraft:item_" + (i % 1000), S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
        }
    }

    // ======================================================================
    //  Fluid entries — different kind classification
    // ======================================================================

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(50_000)
    public void pushFluidEntries() {
        RtsStorageSession s = new RtsStorageSession();
        for (int i = 0; i < 50_000; i++) {
            RtsStorageRecentEntries.recordRecentItem(
                    s, "minecraft:fluid_" + (i % 500), S2CRtsStoragePagePayload.RECENT_FLUID_PLACED, 1000L);
        }
    }

    // ======================================================================
    //  Mixed kinds — same item id, alternating kind (tests kind branch)
    // ======================================================================

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 1)
    @Measurement(iterations = 3)
    @OperationsPerInvocation(100_000)
    public void pushMixedKinds() {
        RtsStorageSession s = new RtsStorageSession();
        byte[] kinds = {S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, S2CRtsStoragePagePayload.RECENT_FLUID_PLACED};
        for (int i = 0; i < 100_000; i++) {
            RtsStorageRecentEntries.recordRecentItem(
                    s, "minecraft:nether_star", kinds[i % 2], 1L);
        }
    }

    // ======================================================================
    //  Bulk fill to capacity — fill deque from empty to full
    // ======================================================================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    public void bulkFillToCapacity(Blackhole bh) {
        RtsStorageSession s = new RtsStorageSession();
        for (int i = 0; i < 24; i++) {
            RtsStorageRecentEntries.recordRecentItem(
                    s, "minecraft:item_" + i, S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
        }
        bh.consume(s.uiMemory.getRecentEntries().size());
    }

    // ======================================================================
    //  Null guards — fast path for null/blank/zero-amount entries
    // ======================================================================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    public void nullGuard(Blackhole bh) {
        RtsStorageSession s = new RtsStorageSession();
        for (int i = 0; i < 100_000; i++) {
            RtsStorageRecentEntries.recordRecentItem(s, (String) null,
                    S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
            RtsStorageRecentEntries.recordRecentItem(s, "",
                    S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
            RtsStorageRecentEntries.recordRecentItem(s, "  ",
                    S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 0L);
        }
        bh.consume(s.uiMemory.getRecentEntries().size());
    }

    // ======================================================================
    //  Bulk clear (session reset) — measure deque clear overhead
    // ======================================================================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    public void clearEntries(Blackhole bh) {
        RtsStorageSession s = new RtsStorageSession();
        // Fill to capacity first
        for (int i = 0; i < 24; i++) {
            s.uiMemory.getRecentEntries().addLast(new com.rtsbuilding.rtsbuilding.server.storage.RecentEntry(
                    "minecraft:item_" + i, 1L, 0L, S2CRtsStoragePagePayload.RECENT_ITEM_PLACED));
        }
        s.uiMemory.clearRecentEntries();
        bh.consume(s);
    }
}
