package com.rtsbuilding.rtsbuilding.server.storage.state;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** 按物品 ID 定位挂起任务的纯 Java 索引；不负责判断库存是否足够。 */
public final class PendingItemIndex<T> {
    private final Map<String, LinkedHashSet<T>> byItemId = new LinkedHashMap<>();

    public void add(String itemId, T value) {
        if (itemId == null || itemId.trim().isEmpty() || value == null) return;
        byItemId.computeIfAbsent(itemId, ignored -> new LinkedHashSet<T>()).add(value);
    }

    public void remove(String itemId, T value) {
        if (itemId == null || value == null) return;
        LinkedHashSet<T> values = byItemId.get(itemId);
        if (values == null) return;
        values.remove(value);
        if (values.isEmpty()) byItemId.remove(itemId);
    }

    public List<T> valuesFor(Collection<String> changedItemIds) {
        if (changedItemIds == null || changedItemIds.isEmpty()) return Collections.emptyList();
        LinkedHashSet<T> matches = new LinkedHashSet<T>();
        for (String itemId : changedItemIds) {
            LinkedHashSet<T> values = byItemId.get(itemId);
            if (values != null) matches.addAll(values);
        }
        return new ArrayList<T>(matches);
    }

    public void clear() {
        byItemId.clear();
    }
}
