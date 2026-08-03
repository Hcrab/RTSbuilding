package com.rtsbuilding.rtsbuilding.platform;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * RTSBuilding 对内使用的内建注册表契约。
 *
 * <p>该类负责把 1.20.1 的 {@code BuiltInRegistries} 语义投影到 1.19.2 的
 * {@link Registry}，业务层只依赖稳定的查找、判定和遍历入口。它不负责注册新对象，也不改变
 * Minecraft 注册表的生命周期。把版本差异集中在这里，可以避免物品、蓝图、储存和任务代码中
 * 散布 1.19.2 特判。</p>
 *
 * <p>1.19.2 的创造页签还不是注册表对象，因此这里为现有页签建立只读快照，并使用配方目录名
 * 生成稳定 id。这个兼容视图只承载分类和查找，不尝试模拟新版本的动态页签注册行为。</p>
 */
public final class RtsBuiltInRegistries {
    public static final RegistryView<Item> ITEM = RegistryView.of(Registry.ITEM);
    public static final RegistryView<Block> BLOCK = RegistryView.of(Registry.BLOCK);
    public static final RegistryView<Fluid> FLUID = RegistryView.of(Registry.FLUID);
    public static final RegistryView<SoundEvent> SOUND_EVENT = RegistryView.of(Registry.SOUND_EVENT);
    public static final CreativeTabRegistryView CREATIVE_MODE_TAB = new CreativeTabRegistryView();

    private RtsBuiltInRegistries() {
    }

    /**
     * 只读注册表视图，保留搬运基线实际使用的最小操作集合。
     *
     * <p>它不会缓存注册表内容，普通对象查找始终委托给真实的 1.19.2 注册表；这样数据包重载和
     * Forge 生命周期仍由平台负责。{@link #wrapAsHolder(Object)} 仅用于旧版声音播放签名所需的
     * 直接 Holder，不在项目内建立第二份注册信息。</p>
     */
    public static final class RegistryView<T> implements Iterable<T> {
        private final Registry<T> delegate;

        private RegistryView(Registry<T> delegate) {
            this.delegate = delegate;
        }

        private static <T> RegistryView<T> of(Registry<T> delegate) {
            return new RegistryView<>(delegate);
        }

        public ResourceLocation getKey(T value) {
            return delegate.getKey(value);
        }

        public boolean containsKey(ResourceLocation id) {
            return id != null && delegate.containsKey(id);
        }

        public T get(ResourceLocation id) {
            return delegate.get(id);
        }

        public Optional<T> getOptional(ResourceLocation id) {
            return id == null ? Optional.empty() : delegate.getOptional(id);
        }

        public Set<ResourceLocation> keySet() {
            return delegate.keySet();
        }

        public Holder<T> wrapAsHolder(T value) {
            return Holder.direct(value);
        }

        @Override
        public Iterator<T> iterator() {
            return delegate.iterator();
        }
    }

    /**
     * 1.19.2 创造页签的只读注册表式视图。
     *
     * <p>快照按原版 {@link CreativeModeTab#TABS} 顺序建立，以保持 UI 分类顺序。重复 id 使用首个
     * 页签，无法构造资源 id 的页签退回 {@code rtsbuilding:legacy_tab_<数字 id>}，保证玩家安装
     * 非标准旧模组时不会因为一个坏页签名称让整个物品页失效。</p>
     */
    public static final class CreativeTabRegistryView implements Iterable<CreativeModeTab> {
        private Map<ResourceLocation, CreativeModeTab> snapshot() {
            Map<ResourceLocation, CreativeModeTab> tabs = new LinkedHashMap<>();
            Arrays.stream(CreativeModeTab.TABS)
                    .filter(tab -> tab != null)
                    .forEach(tab -> tabs.putIfAbsent(idOf(tab), tab));
            return Collections.unmodifiableMap(tabs);
        }

        public CreativeModeTab get(ResourceLocation id) {
            return id == null ? null : snapshot().get(id);
        }

        public ResourceLocation getKey(CreativeModeTab tab) {
            return tab == null ? null : idOf(tab);
        }

        @Override
        public Iterator<CreativeModeTab> iterator() {
            return snapshot().values().iterator();
        }

        private static ResourceLocation idOf(CreativeModeTab tab) {
            String name = tab.getRecipeFolderName();
            try {
                return new ResourceLocation(name);
            } catch (RuntimeException ignored) {
                return new ResourceLocation("rtsbuilding", "legacy_tab_" + tab.getId());
            }
        }
    }
}
