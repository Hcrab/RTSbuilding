package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 每玩家 LRU 页面缓存，避免纯分页操作时的 O(n log n) 排序+过滤重建。
 *
 * <p>当用户仅切换页面时（搜索/排序/类别未变），
 * 直接返回缓存中的排序结果，无需重新遍历所有处理器槽位。
 * 缓存失效通过 {@code dataVersion} 版本号检测——
 * 当储存数据发生变化时版本号递增，下一次请求触发完整重建。
 *
 * <p>内部使用基于访问顺序的 {@link LinkedHashMap}（accessOrder=true），
 * 最多缓存的玩家数由服务端配置控制。
 * 当缓存满时，最近最少访问的玩家条目被自动淘汰。
 *
 * <p>缓存键（{@link CachedPageKey}）包含搜索词、排序方式、
 * 类别、排序顺序、页面大小等参数，确保只命中相同查询条件的缓存。
 */
public final class RtsPageCache {

    public static final RtsPageCache INSTANCE = new RtsPageCache();

    /** 具有缓存页面数据的最大玩家数。 */
    /** 无锁 LRU：访问顺序迭代让我们可以找到最旧的条目。 */
    private final Map<UUID, CachedPage> cache = new LinkedHashMap<>(
            16, 0.75f, true /* accessOrder */);

    /**
     * 包私有构造函数——外部代码应使用 {@link #INSTANCE}。
     * 同一包中的测试可以创建独立实例。
     */
    public RtsPageCache() {
    }

    /**
     * 决定缓存有效性的键。
     */
    public record CachedPageKey(
            String search, RtsStorageSort sort, String category, boolean ascending,
            int pageSize, boolean pinyinSearchEnabled, boolean includePlayerInventory
    ) {}

    /**
     * 昂贵的排序+过滤+类别构建阶段的缓存结果。
     */
    public record CachedPage(
            CachedPageKey key,
            long dataVersion,
            List<Entry> sortedEntries,
            List<FluidEntry> sortedFluidEntries,
            Map<String, Long> counts,
            Map<String, Long> namespaceTotals,
            List<String> categories
    ) {}

    /**
     * 获取玩家的缓存页面，如果缓存为空或数据版本已过时则返回 null。
     */
    public synchronized CachedPage get(UUID playerUuid) {
        return playerUuid == null ? null : cache.get(playerUuid);
    }

    /**
     * 将页面结果存储在缓存中，如果缓存已满则淘汰最近最少访问的玩家。
     */
    public synchronized void put(UUID playerUuid, CachedPage page) {
        if (playerUuid == null || page == null) {
            return;
        }
        // Evict oldest if at capacity (the eldest entry in access-order map)
        if (cache.size() >= Config.pageCacheMaxPlayers() && !cache.containsKey(playerUuid)) {
            var it = cache.entrySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        cache.put(playerUuid, page);
    }

    /**
     * 移除玩家的缓存页面数据，以便在禁用 RTS 或退出时 GC 可以回收内存。
     */
    public synchronized void remove(UUID playerUuid) {
        if (playerUuid != null) {
            cache.remove(playerUuid);
        }
    }

    /** 移除所有缓存的条目。 */
    public synchronized void clear() {
        cache.clear();
    }

    /** 返回当前缓存的玩家数。 */
    public synchronized int size() {
        return cache.size();
    }
}
