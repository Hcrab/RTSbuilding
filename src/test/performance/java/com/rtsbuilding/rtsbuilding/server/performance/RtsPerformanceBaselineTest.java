package com.rtsbuilding.rtsbuilding.server.performance;

import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsHandlerCache;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 性能现状的可重复基线。
 *
 * <p>这里故意运行当前生产类的大规模聚合热路径，并把坏数据直接打印出来。
 * 测试只约束工作量和结果正确性，不用不稳定的墙钟时间阻断构建；耗时用于人工观察和后续版本对比。
 */
class RtsPerformanceBaselineTest {

    private static final int LARGE_ENTRY_COUNT = 50_000;
    private static final int MULTIPLAYER_COUNT = 20;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void twentyIndependentPlayerCachesPerformTwoHundredThousandRealSlotReads() {
        int slots = 10_000;
        CountingItemHandler sharedStorage = new CountingItemHandler(slots);

        long start = System.nanoTime();
        for (int player = 0; player < MULTIPLAYER_COUNT; player++) {
            new RtsHandlerCache().update(sharedStorage);
        }
        long elapsedNanos = System.nanoTime() - start;

        assertEquals(200_000, sharedStorage.stackReads);
        System.out.printf(
                "[恐怖基线][存储] 同一 10,000 槽存储 × 20 份玩家缓存 = %,d 次真实槽位读取和快照构建，耗时 %.2f ms%n",
                sharedStorage.stackReads, elapsedNanos / 1_000_000.0D);
    }

    @Test
    void twentyPlayerLargeStorageAggregationExecutesOneMillionRealMapMerges() throws Exception {
        RtsHandlerCache cache = new RtsHandlerCache();
        seedCounts(cache, LARGE_ENTRY_COUNT);

        long start = System.nanoTime();
        long mergedEntries = 0L;
        for (int player = 0; player < MULTIPLAYER_COUNT; player++) {
            Map<String, Long> pageCounts = new HashMap<>(LARGE_ENTRY_COUNT * 2);
            cache.getAvailableItems(pageCounts);
            assertEquals(LARGE_ENTRY_COUNT, pageCounts.size());
            mergedEntries += pageCounts.size();
        }
        long elapsedNanos = System.nanoTime() - start;

        assertEquals(1_000_000L, mergedEntries);
        System.out.printf(
                "[恐怖基线][存储] 20 名玩家 × 50,000 条目 = %,d 次真实 HashMap 合并，本机耗时 %.2f ms%n",
                mergedEntries, elapsedNanos / 1_000_000.0D);
    }

    @Test
    void frequentLargeStorageQueriesExposeTwentyMillionLookupsPerSecondProjection() throws Exception {
        RtsHandlerCache cache = new RtsHandlerCache();
        seedCounts(cache, LARGE_ENTRY_COUNT);

        int lookups = 1_000_000;
        long start = System.nanoTime();
        long checksum = 0L;
        for (int i = 0; i < lookups; i++) {
            checksum += cache.getCount("rtsbuilding:baseline_item_" + (i % LARGE_ENTRY_COUNT));
        }
        long elapsedNanos = System.nanoTime() - start;

        assertTrue(checksum > 0L);
        long projectedPerSecond = (long) LARGE_ENTRY_COUNT * MULTIPLAYER_COUNT * 20L;
        assertEquals(20_000_000L, projectedPerSecond);
        System.out.printf(
                "[恐怖基线][存储] 50,000 条目 × 20 人 × 20 TPS = 每秒理论 %,d 次条目访问；实跑 1,000,000 次耗时 %.2f ms%n",
                projectedPerSecond, elapsedNanos / 1_000_000.0D);
    }

    private static void seedCounts(RtsHandlerCache cache, int count) throws Exception {
        Field field = RtsHandlerCache.class.getDeclaredField("countsByItem");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Long> counts = (Map<String, Long>) field.get(cache);
        for (int i = 0; i < count; i++) {
            counts.put("rtsbuilding:baseline_item_" + i, (long) i + 1L);
        }
    }

    private static final class CountingItemHandler implements RtsItemStorage {
        private final int slots;
        private int stackReads;

        private CountingItemHandler(int slots) {
            this.slots = slots;
        }

        @Override
        public int slotCount() {
            return slots;
        }

        @Override
        public ItemStack stackInSlot(int slot) {
            stackReads++;
            return new ItemStack(Items.STONE, 64);
        }

        @Override
        public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extract(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int slotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }
}
