package com.rtsbuilding.rtsbuilding.uicore.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 形成一次不可变 UI 贡献快照的确定性注册表。
 *
 * <p>排序优先遵守 before/after 拓扑关系，其次按 group、weight 和注册顺序稳定
 * 排列。生成快照后禁止继续注册，避免渲染期间列表突变。</p>
 */
public final class UiOrderedRegistry<T> {
    private final Map<String, Node<T>> nodes = new LinkedHashMap<String, Node<T>>();
    private long nextSequence;
    private List<UiRegistration<T>> snapshot;

    public void register(UiRegistration<T> registration) {
        if (snapshot != null) {
            throw new IllegalStateException("registry is frozen after snapshot");
        }
        if (registration == null) {
            throw new IllegalArgumentException("registration must not be null");
        }
        if (nodes.containsKey(registration.getId())) {
            throw new IllegalArgumentException("duplicate UI contribution id: " + registration.getId());
        }
        nodes.put(registration.getId(), new Node<T>(registration, nextSequence++));
    }

    public List<UiRegistration<T>> snapshot() {
        if (snapshot == null) {
            snapshot = Collections.unmodifiableList(sort());
        }
        return snapshot;
    }

    public int size() {
        return nodes.size();
    }

    public boolean isFrozen() {
        return snapshot != null;
    }

    private List<UiRegistration<T>> sort() {
        final Map<String, Integer> indegree = new LinkedHashMap<String, Integer>();
        final Map<String, Set<String>> outgoing = new LinkedHashMap<String, Set<String>>();
        for (String id : nodes.keySet()) {
            indegree.put(id, 0);
            outgoing.put(id, new LinkedHashSet<String>());
        }
        for (Node<T> node : nodes.values()) {
            for (String target : node.registration.getBefore()) {
                addEdge(node.registration.getId(), target, indegree, outgoing);
            }
            for (String target : node.registration.getAfter()) {
                addEdge(target, node.registration.getId(), indegree, outgoing);
            }
        }

        PriorityQueue<Node<T>> ready = new PriorityQueue<Node<T>>(Math.max(1, nodes.size()), nodeComparator());
        for (Node<T> node : nodes.values()) {
            if (indegree.get(node.registration.getId()) == 0) {
                ready.add(node);
            }
        }
        List<UiRegistration<T>> ordered = new ArrayList<UiRegistration<T>>(nodes.size());
        while (!ready.isEmpty()) {
            Node<T> node = ready.remove();
            ordered.add(node.registration);
            for (String target : outgoing.get(node.registration.getId())) {
                int remaining = indegree.get(target) - 1;
                indegree.put(target, remaining);
                if (remaining == 0) ready.add(nodes.get(target));
            }
        }
        if (ordered.size() != nodes.size()) {
            List<String> cycle = new ArrayList<String>();
            for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
                if (entry.getValue() > 0) cycle.add(entry.getKey());
            }
            throw new IllegalStateException("UI contribution ordering cycle: " + cycle);
        }
        return ordered;
    }

    private void addEdge(String from, String to, Map<String, Integer> indegree,
                         Map<String, Set<String>> outgoing) {
        // 可选附属不存在时，before/after 引用自然失效，不让基础模组启动失败。
        if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
            return;
        }
        if (outgoing.get(from).add(to)) {
            indegree.put(to, indegree.get(to) + 1);
        }
    }

    private Comparator<Node<T>> nodeComparator() {
        return new Comparator<Node<T>>() {
            @Override
            public int compare(Node<T> left, Node<T> right) {
                int group = left.registration.getGroup().compareTo(right.registration.getGroup());
                if (group != 0) return group;
                int weight = Integer.compare(left.registration.getWeight(), right.registration.getWeight());
                if (weight != 0) return weight;
                return Long.compare(left.sequence, right.sequence);
            }
        };
    }

    private static final class Node<T> {
        private final UiRegistration<T> registration;
        private final long sequence;

        private Node(UiRegistration<T> registration, long sequence) {
            this.registration = registration;
            this.sequence = sequence;
        }
    }
}
