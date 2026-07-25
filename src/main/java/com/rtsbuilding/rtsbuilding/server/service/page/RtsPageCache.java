package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 每玩家 LRU 页面缓存，避免纯分页操作重复执行排序与过滤。
 *
 * <p>缓存只保存昂贵的页面中间结果；储存数据仍由会话和处理器拥有。
 * 查询条件或数据版本变化时，调用方会重新构建页面。最大玩家数由服务端配置控制。</p>
 */
public final class RtsPageCache {

    public static final RtsPageCache INSTANCE = new RtsPageCache();

    private final Map<UUID, CachedPage> cache =
            new LinkedHashMap<>(16, 0.75F, true);

    /**
     * 公共构造函数仅供独立测试创建隔离缓存；生产代码使用 {@link #INSTANCE}。
     */
    public RtsPageCache() {
    }

    /** 决定页面缓存是否可复用的查询键。 */
    public record CachedPageKey(
            String search,
            RtsStorageSort sort,
            String category,
            boolean ascending,
            int pageSize,
            boolean pinyinSearchEnabled,
            boolean includePlayerInventory) {
    }

    /** 排序、过滤和类别计算完成后的缓存结果。 */
    public record CachedPage(
            CachedPageKey key,
            long dataVersion,
            List<Entry> sortedEntries,
            List<FluidEntry> sortedFluidEntries,
            Map<String, Long> counts,
            Map<String, Long> namespaceTotals,
            List<String> categories) {
    }

    public synchronized CachedPage get(UUID playerUuid) {
        return playerUuid == null ? null : this.cache.get(playerUuid);
    }

    public synchronized void put(UUID playerUuid, CachedPage page) {
        if (playerUuid == null || page == null) {
            return;
        }
        if (this.cache.size() >= Config.pageCacheMaxPlayers()
                && !this.cache.containsKey(playerUuid)) {
            var iterator = this.cache.entrySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        this.cache.put(playerUuid, page);
    }

    public synchronized void remove(UUID playerUuid) {
        if (playerUuid != null) {
            this.cache.remove(playerUuid);
        }
    }

    public synchronized void clear() {
        this.cache.clear();
    }

    public synchronized int size() {
        return this.cache.size();
    }
}
