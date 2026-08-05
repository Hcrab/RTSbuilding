package com.rtsbuilding.rtsbuilding.platform.registry;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * 把 1.7.10 的字符串注册表包装成后续版本常用的 ResourceLocation 视图。
 *
 * <p>此类不拥有注册生命周期，也不允许业务层注册新对象；它只提供稳定的查询、反查和遍历
 * 语义。这样蓝图、储存、合成与 UI 代码都不必知道 GTNH 仍使用字符串键。</p>
 */
public final class RtsRegistry<T> implements Iterable<T> {
    private final FMLControlledNamespacedRegistry<T> delegate;

    RtsRegistry(FMLControlledNamespacedRegistry<T> delegate) {
        this.delegate = delegate;
    }

    public T getValue(ResourceLocation id) {
        return id == null ? null : delegate.getObject(id.toString());
    }

    public T getObject(ResourceLocation id) {
        return getValue(id);
    }

    public ResourceLocation getKey(T value) {
        if (value == null) return null;
        String name = delegate.getNameForObject(value);
        return name == null ? null : new ResourceLocation(name);
    }

    public ResourceLocation getNameForObject(T value) {
        return getKey(value);
    }

    public boolean containsKey(ResourceLocation id) {
        return id != null && delegate.containsKey(id.toString());
    }

    public Collection<T> getValuesCollection() {
        ArrayList<T> values = new ArrayList<T>();
        for (T value : this) {
            if (value != null) values.add(value);
        }
        return values;
    }

    @Override
    public Iterator<T> iterator() {
        return delegate.iterator();
    }
}
