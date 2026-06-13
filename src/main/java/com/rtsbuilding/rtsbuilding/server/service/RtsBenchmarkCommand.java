package com.rtsbuilding.rtsbuilding.server.service;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsAggregateStorage;
import com.rtsbuilding.rtsbuilding.server.storage.RtsHandlerCache;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Server-side performance benchmark command for the RTS storage system.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code /rtsbuilding bench} ??runs all benchmarks with default sizes</li>
 * </ul>
 */
public final class RtsBenchmarkCommand {

    private static final String PREFIX = "§6[RTSBench] §r";
    private static final String PREFIX_HEADER = "§6??§e";

    /** Predefined set of Minecraft items used to simulate varied storage content. */
    private static Item[] itemPool = null;

    private static Item[] getItemPool() {
        if (itemPool == null) {
            itemPool = loadItemPool(300);
        }
        return itemPool;
    }

    /**
     * Loads up to {@code count} unique non-air items from the item registry.
     * Falls back to STONE if the registry doesn't have enough distinct items.
     */
    private static Item[] loadItemPool(int count) {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (items.size() >= count) break;
            if (item == Items.AIR) continue;
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty()) {
                items.add(item);
            }
        }
        while (items.size() < count) {
            items.add(Items.STONE);
        }
        return items.toArray(new Item[0]);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("rtsbuilding")
                        .then(Commands.literal("bench")
                                .requires(src -> src.hasPermission(2)) // OP only
                                .executes(RtsBenchmarkCommand::runAllBenchmarks)
                        )
        );
    }

    // ======================================================================
    //  Benchmark entry point
    // ======================================================================

    private static int runAllBenchmarks(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        long totalStart = System.nanoTime();

        src.sendSuccess(() -> header("RTS 储存系统性能测试 v1.0"), false);
        src.sendSuccess(() -> blank(), false);

        // ── 1. Cache update benchmark ──
        src.sendSuccess(() -> section("缓存更新性能测试"), false);
        src.sendSuccess(() -> info("测试 RtsHandlerCache.update() 在不同槽位数下的增量扫描开销"), false);
        src.sendSuccess(() -> blank(), false);

        int[] cacheSizes = {100, 500, 1000, 5000, 10000, 50000};
        int cacheIterations = 500;
        for (int slots : cacheSizes) {
            runCacheBenchmark(src, slots, cacheIterations);
        }

        // ── 2. Full pipeline benchmark ──
        src.sendSuccess(() -> blank(), false);
        src.sendSuccess(() -> section("全链路插??取出性能测试"), false);
        src.sendSuccess(() -> info("测试 RtsAggregateStorage ??insert() + extract() + 缓存刷新开销"), false);
        src.sendSuccess(() -> blank(), false);

        int[] handlerCounts = {1, 3, 5};     // number of handler priorities
        int[] slotsPerHandler = {100, 1000, 5000};
        int pipelineIterations = 200;
        for (int handlers : handlerCounts) {
            for (int slots : slotsPerHandler) {
                runPipelineBenchmark(src, handlers, slots, pipelineIterations);
            }
        }

        // ── 3. Summary ──
        long totalElapsed = (System.nanoTime() - totalStart) / 1_000_000;
        src.sendSuccess(() -> blank(), false);
        src.sendSuccess(() -> divider(), false);
        src.sendSuccess(() -> info("测试完成！总耗时: " + totalElapsed + "ms"), false);
        src.sendSuccess(() -> info("详细日志已输出到 latest.log"), false);

        RtsbuildingMod.LOGGER.info("[RTSBench] ===== 性能测试完成, 总耗时 {}ms =====", totalElapsed);

        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    // ======================================================================
    //  Cache benchmark
    // ======================================================================

    private static void runCacheBenchmark(CommandSourceStack src, int slots, int iterations) {
        BenchmarkHandler handler = new BenchmarkHandler(slots);
        RtsHandlerCache cache = new RtsHandlerCache();

        // Warmup: populate cache
        for (int i = 0; i < 10; i++) {
            cache.update(handler);
        }

        // Benchmark: measure update() time
        PerformanceTimer timer = PerformanceTimer.benchmark(
                slots + " slots", iterations, () -> cache.update(handler));

        // Report
        double avg = timer.avgMicros() / 1000.0;
        double max = timer.maxMicros() / 1000.0;
        src.sendSuccess(() -> Component.literal(String.format(
                "  §e%-7d slots:  §aavg=%.3fms  §cpeak=%.3fms  §7(n=%d, items=%d, unique=%d)",
                slots, avg, max, timer.samples(), handler.slotCount, handler.uniqueItemCount())), false);

        RtsbuildingMod.LOGGER.info("[RTSBench] cache[slots={},unique={}]: avg={}ms max={}ms samples={}",
                slots, handler.uniqueItemCount(), String.format("%.3f", avg),
                String.format("%.3f", max), timer.samples());
    }

    // ======================================================================
    //  Pipeline benchmark
    // ======================================================================

    private static void runPipelineBenchmark(CommandSourceStack src, int handlerCount,
                                              int slotsPerHandler, int iterations) {
        // Create aggregate storage
        RtsAggregateStorage storage = new RtsAggregateStorage();
        List<BenchmarkHandler> handlers = new ArrayList<>();
        List<RtsHandlerCache> caches = new ArrayList<>();

        for (int p = 0; p < handlerCount; p++) {
            BenchmarkHandler h = new BenchmarkHandler(slotsPerHandler, p * slotsPerHandler);
            RtsHandlerCache cache = new RtsHandlerCache();
            storage.mount(handlerCount - p, h, cache);
            cache.update(h); // initial populate
            handlers.add(h);
            caches.add(cache);
        }

        int totalSlots = handlerCount * slotsPerHandler;

        // ── Insert benchmark ──
        Item insertItem = Items.STONE;
        ItemStack testStack = new ItemStack(insertItem, 1024);

        // Warmup inserts (not measured)
        for (int i = 0; i < 50; i++) {
            storage.insert(testStack.copy(), false);
        }

        PerformanceTimer insertTimer = PerformanceTimer.benchmark(
                "insert", iterations, () -> storage.insert(testStack.copy(), false));

        double insertAvg = insertTimer.avgMicros() / 1000.0;
        double insertMax = insertTimer.maxMicros() / 1000.0;

        // ── Extract benchmark ──
        // Warmup extracts (not measured)
        for (int i = 0; i < 50; i++) {
            storage.extract(insertItem, 64);
        }

        PerformanceTimer extractTimer = PerformanceTimer.benchmark(
                "extract", iterations, () -> storage.extract(insertItem, 64));

        double extractAvg = extractTimer.avgMicros() / 1000.0;
        double extractMax = extractTimer.maxMicros() / 1000.0;

        // ── Cache refresh after insert/extract ──
        PerformanceTimer cacheRefreshTimer = PerformanceTimer.benchmark(
                "cache_refresh", Math.min(iterations, 100),
                () -> {
                    for (int i = 0; i < handlerCount; i++) {
                        caches.get(i).update(handlers.get(i));
                    }
                });

        double refreshAvg = cacheRefreshTimer.avgMicros() / 1000.0;
        double refreshMax = cacheRefreshTimer.maxMicros() / 1000.0;

        // Report
        src.sendSuccess(() -> Component.literal(String.format(
                "  §e%d handlers × %d slots (total=%d):",
                handlerCount, slotsPerHandler, totalSlots)), false);
        src.sendSuccess(() -> Component.literal(String.format(
                "    §a??insert:  §6avg=%.3fms  §cpeak=%.3fms  §7(n=%d)",
                insertAvg, insertMax, insertTimer.samples())), false);
        src.sendSuccess(() -> Component.literal(String.format(
                "    §a??extract: §6avg=%.3fms  §cpeak=%.3fms  §7(n=%d)",
                extractAvg, extractMax, extractTimer.samples())), false);
        src.sendSuccess(() -> Component.literal(String.format(
                "    §a??refresh: §6avg=%.3fms  §cpeak=%.3fms  §7(n=%d)",
                refreshAvg, refreshMax, cacheRefreshTimer.samples())), false);

        RtsbuildingMod.LOGGER.info("[RTSBench] pipeline[h={},s/h={},total={}]: "
                        + "insert(avg={}ms,max={}) extract(avg={}ms,max={}) refresh(avg={}ms,max={})",
                handlerCount, slotsPerHandler, totalSlots,
                String.format("%.3f", insertAvg), String.format("%.3f", insertMax),
                String.format("%.3f", extractAvg), String.format("%.3f", extractMax),
                String.format("%.3f", refreshAvg), String.format("%.3f", refreshMax));
    }

    // ======================================================================
    //  BenchmarkHandler ??simulates AE2's ReportedCountItemHandler
    // ======================================================================

    /**
     * A lightweight benchmark handler that simulates AE2 network storage
     * behavior: many slots, each with a unique or repeated item prototype,
     * implementing {@link ReportedCountItemHandler} to exercise the fast
     * cache path in {@link RtsHandlerCache}.
     */
    static final class BenchmarkHandler implements IItemHandler, ReportedCountItemHandler {

        private final int slotCount;
        private final Item[] items;
        private final long[] counts;

        /**
         * Creates a handler with {@code slotCount} slots, each holding an item
         * from the {@link #getItemPool()} cyclically. Each slot gets a unique
         * position-derived ItemStack with count=1 (simulating AE2's prototype
         * behavior) and a reported count of 21.7M per slot to stress-test
         * the long→int truncation boundary and cache merging logic.
         *
         * @param offset  start index offset for item selection
         */
        BenchmarkHandler(int slotCount, int offset) {
            this.slotCount = slotCount;
            this.items = new Item[slotCount];
            this.counts = new long[slotCount];
            Item[] pool = getItemPool();
            for (int i = 0; i < slotCount; i++) {
                this.items[i] = pool[(offset + i) % pool.length];
                // 21.7M per slot ??near the int boundary
                this.counts[i] = 21_700_000L;
            }
        }

        BenchmarkHandler(int slotCount) {
            this(slotCount, 0);
        }

        @Override
        public int getSlots() {
            return this.slotCount;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= this.slotCount) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(this.items[slot], 1);
            return stack;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            // No-op for benchmarks ??the storage layer is what we're testing
            return stack == null ? ItemStack.EMPTY : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }

        @Override
        public long getReportedCount(int slot) {
            if (slot < 0 || slot >= this.slotCount) return 0L;
            return this.counts[slot];
        }

        /** Returns the number of unique item types across all slots. */
        int uniqueItemCount() {
            return Math.min(this.slotCount, getItemPool().length);
        }
    }

    // ======================================================================
    //  Formatting helpers
    // ======================================================================

    private static Component header(String text) {
        return Component.literal("§6╔══════════════════════════════════════════════╗\n"
                + "§6?? §e" + text + "  §6║\n"
                + "§6╚══════════════════════════════════════════════╝");
    }

    private static Component section(String text) {
        return Component.literal("§6??§e" + text);
    }

    private static Component info(String text) {
        return Component.literal("  §7" + text);
    }

    private static Component blank() {
        return Component.literal("");
    }

    private static Component divider() {
        return Component.literal("§8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
