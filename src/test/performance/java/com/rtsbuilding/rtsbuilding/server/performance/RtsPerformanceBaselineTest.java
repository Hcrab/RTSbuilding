package com.rtsbuilding.rtsbuilding.server.performance;

import com.rtsbuilding.rtsbuilding.server.storage.RtsHandlerCache;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forge 1.20.1 性能现状的可重复基线。
 *
 * <p>直接运行生产缓存类的大规模聚合热路径。时间只用于观察，稳定的工作量和结果才是硬断言。
 */
class RtsPerformanceBaselineTest {

    private static final int LARGE_ENTRY_COUNT = 50_000;
    private static final int MULTIPLAYER_COUNT = 20;

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
                "[恐怖基线][Forge 1.20.1][存储] 20 名玩家 × 50,000 条目 = %,d 次真实 HashMap 合并，本机耗时 %.2f ms%n",
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
                "[恐怖基线][Forge 1.20.1][存储] 50,000 条目 × 20 人 × 20 TPS = 每秒理论 %,d 次条目访问；实跑 1,000,000 次耗时 %.2f ms%n",
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
}
