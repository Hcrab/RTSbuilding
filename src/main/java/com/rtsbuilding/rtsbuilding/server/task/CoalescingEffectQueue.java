package com.rtsbuilding.rtsbuilding.server.task;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将一个 Tick 内同一对象的重复副作用合并成一次提交。
 *
 * <p>它不执行网络或存档操作，只维护确定性脏集合，因此可以脱离游戏运行时测试。</p>
 */
public final class CoalescingEffectQueue<K> {
    public enum Kind { STORAGE_VIEW_DIRTY, WORKFLOW, PERSISTENCE }

    private final Map<K, EnumSet<Kind>> dirty = new LinkedHashMap<>();

    public synchronized void mark(K key, Kind kind) {
        dirty.computeIfAbsent(key, ignored -> EnumSet.noneOf(Kind.class)).add(kind);
    }

    public synchronized List<PendingEffect<K>> drain() {
        List<PendingEffect<K>> result = new ArrayList<>(dirty.size());
        dirty.forEach((key, kinds) -> result.add(new PendingEffect<>(key, EnumSet.copyOf(kinds))));
        dirty.clear();
        return result;
    }

    public synchronized int keyCount() {
        return dirty.size();
    }

    public record PendingEffect<K>(K key, EnumSet<Kind> kinds) {
    }
}
