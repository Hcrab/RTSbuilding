package com.rtsbuilding.rtsbuilding.platform.registry;

import net.minecraft.resources.ResourceLocation;

/**
 * 一个已经写入原版注册表的 RTS 条目。
 *
 * <p>保留 {@link #get()} 是为了让业务层不关心 Fabric 与 NeoForge 的注册句柄差异；本对象
 * 不延迟构造，也不承担生命周期事件。
 */
public final class RtsRegistryEntry<R, T extends R> {
    private final ResourceLocation id;
    private final T value;

    RtsRegistryEntry(ResourceLocation id, T value) {
        this.id = id;
        this.value = value;
    }

    public ResourceLocation id() {
        return this.id;
    }

    public T get() {
        return this.value;
    }
}
