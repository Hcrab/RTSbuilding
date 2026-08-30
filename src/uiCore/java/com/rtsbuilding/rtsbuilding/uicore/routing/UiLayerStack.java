package com.rtsbuilding.rtsbuilding.uicore.routing;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 维护唯一 ID、确定性 z 顺序、可见性和模态边界的纯逻辑层栈。
 *
 * <p>它不调用渲染或业务方法。平台层在布局改变时更新 bounds，在输入到来时
 * 查询唯一最上层所有者，因此不会产生第二条并行事件管线。</p>
 */
public final class UiLayerStack<T> {
    private final Map<String, Entry<T>> entries = new LinkedHashMap<String, Entry<T>>();
    private long nextOrder;

    public void register(String id, T owner, UiRect bounds, boolean modal, boolean visible) {
        validate(id, owner, bounds);
        if (entries.containsKey(id)) {
            throw new IllegalArgumentException("duplicate UI layer id: " + id);
        }
        entries.put(id, new Entry<T>(id, owner, bounds, modal, visible, nextOrder++));
    }

    public T unregister(String id) {
        Entry<T> removed = entries.remove(id);
        return removed == null ? null : removed.owner;
    }

    public void updateBounds(String id, UiRect bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        require(id).bounds = bounds;
    }

    public void setVisible(String id, boolean visible) {
        require(id).visible = visible;
    }

    public void setModal(String id, boolean modal) {
        require(id).modal = modal;
    }

    public void bringToFront(String id) {
        require(id).order = nextOrder++;
    }

    public T topmostAt(double x, double y) {
        Entry<T> modal = topmostModalEntry();
        if (modal != null) {
            return modal.bounds.contains(x, y) ? modal.owner : null;
        }
        List<Entry<T>> ordered = orderedEntries(false);
        for (int i = ordered.size() - 1; i >= 0; i--) {
            Entry<T> entry = ordered.get(i);
            if (entry.visible && entry.bounds.contains(x, y)) {
                return entry.owner;
            }
        }
        return null;
    }

    public T topmostModal() {
        Entry<T> entry = topmostModalEntry();
        return entry == null ? null : entry.owner;
    }

    public List<T> ownersBackToFront() {
        List<Entry<T>> ordered = orderedEntries(true);
        List<T> result = new ArrayList<T>(ordered.size());
        for (Entry<T> entry : ordered) {
            if (entry.visible) {
                result.add(entry.owner);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public int size() {
        return entries.size();
    }

    private Entry<T> topmostModalEntry() {
        Entry<T> top = null;
        for (Entry<T> entry : entries.values()) {
            if (entry.visible && entry.modal && (top == null || entry.order > top.order)) {
                top = entry;
            }
        }
        return top;
    }

    private List<Entry<T>> orderedEntries(boolean includeHidden) {
        List<Entry<T>> ordered = new ArrayList<Entry<T>>();
        for (Entry<T> entry : entries.values()) {
            if (includeHidden || entry.visible) {
                ordered.add(entry);
            }
        }
        Collections.sort(ordered, new Comparator<Entry<T>>() {
            @Override
            public int compare(Entry<T> left, Entry<T> right) {
                return left.order < right.order ? -1 : (left.order == right.order ? 0 : 1);
            }
        });
        return ordered;
    }

    private Entry<T> require(String id) {
        Entry<T> entry = entries.get(id);
        if (entry == null) {
            throw new IllegalArgumentException("unknown UI layer id: " + id);
        }
        return entry;
    }

    private static <T> void validate(String id, T owner, UiRect bounds) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (owner == null || bounds == null) {
            throw new IllegalArgumentException("owner and bounds must not be null");
        }
    }

    private static final class Entry<T> {
        private final String id;
        private final T owner;
        private UiRect bounds;
        private boolean modal;
        private boolean visible;
        private long order;

        private Entry(String id, T owner, UiRect bounds, boolean modal, boolean visible, long order) {
            this.id = id;
            this.owner = owner;
            this.bounds = bounds;
            this.modal = modal;
            this.visible = visible;
            this.order = order;
        }
    }
}
