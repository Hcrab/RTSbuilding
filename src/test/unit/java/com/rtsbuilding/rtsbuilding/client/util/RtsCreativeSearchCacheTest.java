package com.rtsbuilding.rtsbuilding.client.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCreativeSearchCacheTest {
    @Test
    void repeatedRenderAndClickQueriesReuseFilteredCreativeResults() {
        List<FakeCreativeEntry> entries = fakeEntries(50_000);
        RtsCreativeSearchCache<FakeCreativeEntry> cache = new RtsCreativeSearchCache<>(FakeCreativeEntry::index);

        List<FakeCreativeEntry> first = cache.filter(entries, 1L, "all", "target block");
        int firstScanCount = cache.lastScanCountForDiagnostics();

        List<FakeCreativeEntry> second = cache.filter(entries, 1L, "all", "target block");
        int secondScanCount = cache.lastScanCountForDiagnostics();

        assertEquals(1, first.size());
        assertEquals(entries.size(), firstScanCount, "第一次过滤必须完整扫描同一批创造物品，数据才有测量意义");
        assertSame(first, second, "同一过滤条件应复用结果，避免渲染、tooltip 和点击各扫一次完整创造栏");
        assertEquals(0, secondScanCount, "缓存命中时不应再次扫描大整合包的完整创造物品表");
    }

    @Test
    void cacheReducesMeasuredScanWorkAcrossRepeatedUiFrames() {
        List<FakeCreativeEntry> entries = fakeEntries(50_000);
        RtsCreativeSearchCache<FakeCreativeEntry> cache = new RtsCreativeSearchCache<>(FakeCreativeEntry::index);
        int repeatedFrames = 12;

        long uncachedStyleScans = 0L;
        for (int frame = 0; frame < repeatedFrames; frame++) {
            cache.filter(entries, frame, "all", "target block");
            uncachedStyleScans += cache.lastScanCountForDiagnostics();
        }

        cache.invalidate();
        long cachedScans = 0L;
        for (int frame = 0; frame < repeatedFrames; frame++) {
            cache.filter(entries, 100L, "all", "target block");
            cachedScans += cache.lastScanCountForDiagnostics();
        }

        assertEquals((long) entries.size() * repeatedFrames, uncachedStyleScans,
                "旧式每帧重算会把完整创造物品表重复扫很多次");
        assertEquals(entries.size(), cachedScans,
                "缓存路径只应在第一次查询时扫描，后续同条件 UI 帧直接复用结果");
        assertTrue(uncachedStyleScans >= cachedScans * 10,
                "测试数据应能量化优化幅度，避免回归成每帧重复扫描");
    }

    @Test
    void pinyinSearchOnlyRunsForChineseLabelsThatNeedIt() {
        List<FakeCreativeEntry> entries = List.of(
                FakeCreativeEntry.create("minecraft:dirt", "Dirt", "minecraft", "dirt"),
                FakeCreativeEntry.create("minecraft:oak_planks", "橡木木板", "minecraft", "oak_planks")
        );
        RtsCreativeSearchCache<FakeCreativeEntry> cache = new RtsCreativeSearchCache<>(FakeCreativeEntry::index);

        List<FakeCreativeEntry> result = cache.filter(entries, 1L, "all", "xiangmu");

        assertEquals(1, result.size());
        assertEquals("minecraft:oak_planks", result.get(0).index().itemId());
        assertEquals(1, cache.lastPinyinCheckCountForDiagnostics(),
                "英文条目不应进入拼音匹配，中文条目才需要额外成本");
    }

    private static List<FakeCreativeEntry> fakeEntries(int count) {
        List<FakeCreativeEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = "testmod:item_" + i;
            String label = i == count - 1 ? "Target Block" : "Decorative Block " + i;
            entries.add(FakeCreativeEntry.create(id, label, "testmod", "item_" + i));
        }
        return entries;
    }

    private record FakeCreativeEntry(RtsCreativeSearchCache.IndexedEntry index) {
        static FakeCreativeEntry create(String itemId, String label, String mod, String name) {
            return new FakeCreativeEntry(RtsCreativeSearchCache.index("all", itemId, label, mod, name));
        }
    }
}
