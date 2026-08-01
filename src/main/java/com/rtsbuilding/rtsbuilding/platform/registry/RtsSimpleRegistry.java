package com.rtsbuilding.rtsbuilding.platform.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric 入口初始化阶段使用的顺序注册器。
 *
 * <p>Fabric 没有 NeoForge 的 DeferredRegister 事件阶段；类被入口按“方块→物品→实体→
 * 创造页”的顺序初始化时，条目会立刻写入原版注册表。该类同时保存稳定顺序，供创造页收集。
 */
public final class RtsSimpleRegistry<R> {
    private final Registry<R> registry;
    private final String namespace;
    private final List<RtsRegistryEntry<R, ? extends R>> entries = new ArrayList<>();

    public RtsSimpleRegistry(Registry<R> registry, String namespace) {
        this.registry = registry;
        this.namespace = namespace;
    }

    public <T extends R> RtsRegistryEntry<R, T> register(String path, Supplier<? extends T> factory) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(this.namespace, path);
        T value = Registry.register(this.registry, id, factory.get());
        RtsRegistryEntry<R, T> entry = new RtsRegistryEntry<>(id, value);
        this.entries.add(entry);
        return entry;
    }

    public Collection<RtsRegistryEntry<R, ? extends R>> getEntries() {
        return List.copyOf(this.entries);
    }
}
