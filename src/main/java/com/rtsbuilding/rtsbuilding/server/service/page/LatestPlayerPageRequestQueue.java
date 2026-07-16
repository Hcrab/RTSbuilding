package com.rtsbuilding.rtsbuilding.server.service.page;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 按玩家合并页面请求的纯队列。
 *
 * <p>同一服务器 tick 内，同一玩家只保留最后一次请求，因此搜索、翻页和首次打开都不会
 * 被静默丢弃，同时昂贵的储存页构建最多执行一次。队列不理解 Minecraft 对象，实际执行
 * 与玩家生命周期清理由服务端适配层负责。
 */
public final class LatestPlayerPageRequestQueue<K, V> {
    private final Map<K, V> pending = new LinkedHashMap<>();

    public synchronized void offer(K playerKey, V request) {
        this.pending.put(playerKey, request);
    }

    public synchronized void remove(K playerKey) {
        this.pending.remove(playerKey);
    }

    public synchronized void clear() {
        this.pending.clear();
    }

    public void drain(Consumer<V> consumer) {
        Map<K, V> snapshot;
        synchronized (this) {
            if (this.pending.isEmpty()) {
                return;
            }
            snapshot = new LinkedHashMap<>(this.pending);
            this.pending.clear();
        }
        snapshot.values().forEach(consumer);
    }

    int size() {
        synchronized (this) {
            return this.pending.size();
        }
    }
}
