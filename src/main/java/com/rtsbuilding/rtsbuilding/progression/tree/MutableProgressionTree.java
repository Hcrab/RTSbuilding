package com.rtsbuilding.rtsbuilding.progression.tree;

import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * 可变的科技树实现，支持动态注册节???
 * 提供注册表风??API，替代旧版硬编码 {@code buildNodes()} 模式??
 */
public final class MutableProgressionTree implements RtsProgressionTree {

    private final Map<ResourceLocation, RtsProgressionNode> nodes = new LinkedHashMap<>();

    /**
     * 注册一个节点，重复注册会抛出异???
     */
    public void register(RtsProgressionNode node) {
        Objects.requireNonNull(node, "node must not be null");
        if (nodes.containsKey(node.id())) {
            throw new IllegalArgumentException("Duplicate progression node: " + node.id());
        }
        nodes.put(node.id(), node);
    }

    /**
     * 批量注册节点??
     */
    public void registerAll(Collection<RtsProgressionNode> nodes) {
        for (RtsProgressionNode node : nodes) {
            register(node);
        }
    }

    /**
     * 注册或替换（覆盖模式），用于热加载场???
     */
    public void registerOrReplace(RtsProgressionNode node) {
        Objects.requireNonNull(node, "node must not be null");
        nodes.put(node.id(), node);
    }

    @Override
    public RtsProgressionNode get(ResourceLocation id) {
        return nodes.get(id);
    }

    @Override
    public Collection<RtsProgressionNode> all() {
        return nodes.values();
    }

    @Override
    public boolean contains(ResourceLocation id) {
        return nodes.containsKey(id);
    }

    /**
     * 返回不可变节点映射视???
     */
    public Map<ResourceLocation, RtsProgressionNode> nodesView() {
        return Collections.unmodifiableMap(nodes);
    }

    /**
     * 返回已注册的节点数量??
     */
    public int size() {
        return nodes.size();
    }

    /**
     * 清空所有节???
     */
    public void clear() {
        nodes.clear();
    }
}
