package com.rtsbuilding.rtsbuilding.server.benchmark;

import com.rtsbuilding.rtsbuilding.server.storage.RtsAggregateStorage;
import com.rtsbuilding.rtsbuilding.server.storage.RtsHandlerCache;
import net.minecraftforge.items.IItemHandler;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.mockito.Mockito.*;

/**
 * 极限性能测试 / Extreme Performance Benchmarks for {@link RtsAggregateStorage}.
 *
 * <p>Focuses on mount/unmount overhead, priority sorting, and iteration
 * throughput over many handlers. Does NOT test {@code insert()} or
 * {@code extract()} ??those reach {@code ItemStack.EMPTY} which triggers
 * Minecraft's static initializer.</p>
 *
 * <p>Safe-to-test methods:</p>
 * <ul>
 *   <li>{@link RtsAggregateStorage#mount} ??TreeMap insertion + flat-list rebuild</li>
 *   <li>{@link RtsAggregateStorage#unmount} ??bidirectional scan + removeIf</li>
 *   <li>{@link RtsAggregateStorage#hasItem} ??linear scan over flat list</li>
 *   <li>{@link RtsAggregateStorage#getTotalCount} ??linear scan, sum aggregation</li>
 *   <li>{@link RtsAggregateStorage#getAvailableItems} ??delegation to cache</li>
 *   <li>{@link RtsAggregateStorage#tickUpdate} ??delegation + change tracking</li>
 *   <li>{@link RtsAggregateStorage#drainPendingChanges} ??HashSet drain</li>
 * </ul>
 */
class RtsAggregateStorageBenchmark {

    private static final int WARMUP = 3;
    private static final int ITERATIONS = 10;

    private RtsAggregateStorage storage;

    @BeforeEach
    void setUp() {
        storage = new RtsAggregateStorage();
    }

    // ======================================================================
    //  Section 1: mount ??sequential, random priority, many handlers
    //  Each mount triggers rebuildFlatOrder() which rebuilds the flat list
    //  from the TreeMap. O(n) per mount in the worst case.
    // ======================================================================

    @Test
    void benchmarkMountSequentialSamePriority() {
        int count = 10_000;

        long totalNanos = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            storage = new RtsAggregateStorage();
            long start = System.nanoTime();
            for (int j = 0; j < count; j++) {
                storage.mount(0, mock(IItemHandler.class), mock(RtsHandlerCache.class));
            }
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgTotal = totalNanos / ITERATIONS;
        BenchmarkReporter.record("[RtsAggregateStorage] mount(%,d, same priority=0): avg %,d ns total, ~%,d ns/op",
                count, avgTotal, avgTotal / count);
    }

    @Test
    void benchmarkMountRandomPriorities() {
        int count = 5_000;
        Random rng = new Random(42);

        long totalNanos = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            storage = new RtsAggregateStorage();
            rng.setSeed(42);
            long start = System.nanoTime();
            for (int j = 0; j < count; j++) {
                storage.mount(rng.nextInt(200) - 100,
                        mock(IItemHandler.class), mock(RtsHandlerCache.class));
            }
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        long avgTotal = totalNanos / ITERATIONS;
        BenchmarkReporter.record("[RtsAggregateStorage] mount(%,d, random priorities): avg %,d ns total, ~%,d ns/op",
                count, avgTotal, avgTotal / count);
    }

    // ======================================================================
    //  Section 2: unmount ??sequential unmount of all handlers
    // ======================================================================

    @Test
    void benchmarkUnmountAllSamePriority() {
        int count = 5_000;
        IItemHandler[] handlers = new IItemHandler[count];
        RtsHandlerCache[] caches = new RtsHandlerCache[count];
        for (int i = 0; i < count; i++) {
            handlers[i] = mock(IItemHandler.class);
            caches[i] = mock(RtsHandlerCache.class);
            storage.mount(0, handlers[i], caches[i]);
        }

        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            storage.unmount(handlers[i]);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / count;
        BenchmarkReporter.record("[RtsAggregateStorage] unmount(%,d, same priority): avg %d ns/op, total %,d ns",
                count, avgNanos, end - start);
    }

    @Test
    void benchmarkUnmountDifferentPriorities() {
        int count = 5_000;
        IItemHandler[] handlers = new IItemHandler[count];
        Random rng = new Random(42);
        for (int i = 0; i < count; i++) {
            handlers[i] = mock(IItemHandler.class);
            RtsHandlerCache cache = mock(RtsHandlerCache.class);
            storage.mount(rng.nextInt(200) - 100, handlers[i], cache);
        }

        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            storage.unmount(handlers[i]);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / count;
        BenchmarkReporter.record("[RtsAggregateStorage] unmount(%,d, random priorities): avg %d ns/op, total %,d ns",
                count, avgNanos, end - start);
    }

    // ======================================================================
    //  Section 3: hasItem() ??linear scan over flat list
    // ======================================================================

    @Test
    void benchmarkHasItemLarge() {
        int handlerCount = 10_000;
        for (int i = 0; i < handlerCount; i++) {
            RtsHandlerCache cache = mock(RtsHandlerCache.class);
            when(cache.getCount(anyString())).thenReturn(0L);
            storage.mount(0, mock(IItemHandler.class), cache);
        }

        Item mockItem = mock(Item.class);
        when(mockItem.toString()).thenReturn("minecraft:diamond");

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            storage.hasItem(mockItem);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / ITERATIONS;
        BenchmarkReporter.record("[RtsAggregateStorage] hasItem(%,d handlers): avg %d ns/op",
                handlerCount, avgNanos);
    }

    @Test
    void benchmarkHasItemSmall() {
        int handlerCount = 10;
        for (int i = 0; i < handlerCount; i++) {
            RtsHandlerCache cache = mock(RtsHandlerCache.class);
            when(cache.getCount(anyString())).thenReturn(0L);
            storage.mount(0, mock(IItemHandler.class), cache);
        }

        Item mockItem = mock(Item.class);
        when(mockItem.toString()).thenReturn("minecraft:diamond");

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS * 1000; i++) {
            storage.hasItem(mockItem);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / (ITERATIONS * 1000);
        BenchmarkReporter.record("[RtsAggregateStorage] hasItem(%,d handlers): avg %d ns/op",
                handlerCount, avgNanos);
    }

    // ======================================================================
    //  Section 4: getTotalCount() ??linear scan with sum aggregation
    // ======================================================================

    @Test
    void benchmarkGetTotalCountLarge() {
        int handlerCount = 10_000;
        for (int i = 0; i < handlerCount; i++) {
            RtsHandlerCache cache = mock(RtsHandlerCache.class);
            when(cache.getCount(anyString())).thenReturn(42L);
            storage.mount(0, mock(IItemHandler.class), cache);
        }

        Item mockItem = mock(Item.class);
        when(mockItem.toString()).thenReturn("minecraft:diamond");

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            storage.getTotalCount(mockItem);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / ITERATIONS;
        BenchmarkReporter.record("[RtsAggregateStorage] getTotalCount(%,d handlers): avg %d ns/op",
                handlerCount, avgNanos);
    }

    // ======================================================================
    //  Section 5: getAvailableItems() ??delegation to all caches
    // ======================================================================

    @Test
    void benchmarkGetAvailableItemsLarge() {
        int handlerCount = 1_000;
        for (int i = 0; i < handlerCount; i++) {
            IItemHandler handler = mock(IItemHandler.class);
            RtsHandlerCache cache = mock(RtsHandlerCache.class);
            final int idx = i;
            doAnswer(inv -> {
                Map<String, Long> out = inv.getArgument(0);
                out.put("minecraft:item_" + (idx % 100), 64L);
                return null;
            }).when(cache).getAvailableItems(any());
            storage.mount(0, handler, cache);
        }

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            Map<String, Long> out = new HashMap<>();
            storage.getAvailableItems(out);
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / ITERATIONS;
        BenchmarkReporter.record("[RtsAggregateStorage] getAvailableItems(%,d handlers): avg %d ns/op",
                handlerCount, avgNanos);
    }

    // ======================================================================
    //  Section 6: tickUpdate() ??delegation to all caches + change tracking
    // ======================================================================

    @Test
    void benchmarkTickUpdateLarge() {
        int handlerCount = 1_000;
        for (int i = 0; i < handlerCount; i++) {
            IItemHandler handler = mock(IItemHandler.class);
            RtsHandlerCache cache = mock(RtsHandlerCache.class);
            Set<String> changes = new HashSet<>();
            changes.add("minecraft:item_" + i);
            when(cache.update(handler)).thenReturn(changes);
            storage.mount(0, handler, cache);
        }

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            storage.tickUpdate();
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / ITERATIONS;
        BenchmarkReporter.record("[RtsAggregateStorage] tickUpdate(%,d handlers): avg %,d ns/op",
                handlerCount, avgNanos);
    }

    @Test
    void benchmarkTickUpdateEmpty() {
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS * 1000; i++) {
            storage.tickUpdate();
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / (ITERATIONS * 1000);
        BenchmarkReporter.record("[RtsAggregateStorage] tickUpdate(empty): avg %d ns/op", avgNanos);
    }

    // ======================================================================
    //  Section 7: drainPendingChanges() ??HashSet clear
    // ======================================================================

    @Test
    void benchmarkDrainPendingChanges() {
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS * 1000; i++) {
            storage.drainPendingChanges();
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / (ITERATIONS * 1000);
        BenchmarkReporter.record("[RtsAggregateStorage] drainPendingChanges(empty): avg %d ns/op", avgNanos);
    }

    @Test
    void benchmarkDrainPendingChangesLarge() {
        // Access drainPendingChanges indirectly by simulating insert/extract
        // tracking: we can't call insert() here, so this benchmark covers
        // the empty path only. Full pending-changes bench would need a
        // bootstrapped environment.
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS * 1000; i++) {
            storage.drainPendingChanges();
        }
        long end = System.nanoTime();
        BenchmarkReporter.record("[RtsAggregateStorage] drainPendingChanges \u00d7 %,d: avg %d ns/op",
                ITERATIONS * 1000, (end - start) / (ITERATIONS * 1000));
    }

    // ======================================================================
    //  Section 8: isEmpty() ??trivial read
    // ======================================================================

    @Test
    void benchmarkIsEmpty() {
        long start = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            storage.isEmpty();
        }
        long end = System.nanoTime();
        long avgNanos = (end - start) / 1_000_000;
        BenchmarkReporter.record("[RtsAggregateStorage] isEmpty() \u00d7 1M: avg %d ns/op", avgNanos);
    }
}
