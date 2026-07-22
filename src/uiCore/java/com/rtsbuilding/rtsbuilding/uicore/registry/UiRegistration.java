package com.rtsbuilding.rtsbuilding.uicore.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一项 UI 贡献的稳定排序元数据和平台无关载荷。 */
public final class UiRegistration<T> {
    private final String id;
    private final String group;
    private final int weight;
    private final List<String> before;
    private final List<String> after;
    private final T value;

    public UiRegistration(String id, String group, int weight,
                          List<String> before, List<String> after, T value) {
        this.id = requireId(id, "id");
        this.group = requireId(group, "group");
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        this.weight = weight;
        this.before = copyIds(before, "before");
        this.after = copyIds(after, "after");
        this.value = value;
    }

    public String getId() {
        return id;
    }

    public String getGroup() {
        return group;
    }

    public int getWeight() {
        return weight;
    }

    public List<String> getBefore() {
        return before;
    }

    public List<String> getAfter() {
        return after;
    }

    public T getValue() {
        return value;
    }

    private static List<String> copyIds(List<String> source, String name) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> copy = new ArrayList<String>(source.size());
        for (String id : source) {
            copy.add(requireId(id, name));
        }
        return Collections.unmodifiableList(copy);
    }

    private static String requireId(String id, String name) {
        if (id == null || !id.matches("[a-z0-9_.:-]+")) {
            throw new IllegalArgumentException(name + " must be a stable lowercase id: " + id);
        }
        return id;
    }
}
