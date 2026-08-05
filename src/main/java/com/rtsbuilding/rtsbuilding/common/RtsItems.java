package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginItem;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import cpw.mods.fml.common.registry.GameRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 物品注册器。实例在类初始化时构造，但只在 1.12.2 的 {@link RegistryEvent.Register} 阶段提交。
 * {@link Handle} 保留业务代码熟悉的 {@code get()} 边界，同时不伪装成新版本 DeferredRegister。
 */
public final class RtsItems {
    private static final List<Handle<? extends Item>> ALL_ITEMS = new ArrayList<>();
    private static final Set<Handle<? extends Item>> CREATIVE_TAB_ITEMS = new LinkedHashSet<>();

    public static final Handle<Item> RTS_CONTROL_CORE = pluginItem("rts_control_core", true);
    public static final Handle<Item> REMOTE_CONTROL_PLUGIN = pluginItem("remote_control_plugin", true);
    public static final Handle<Item> STORAGE_INTEGRATION_PLUGIN = pluginItem("storage_integration_plugin", true);
    public static final Handle<Item> CRAFT_TERMINAL_PLUGIN = pluginItem("craft_terminal_plugin", true);
    public static final Handle<Item> CHAIN_BREAK_PLUGIN = pluginItem("chain_break_plugin", true);
    public static final Handle<Item> AREA_DESTROY_PLUGIN = pluginItem("area_destroy_plugin", true);
    public static final Handle<Item> BLUEPRINT_PLUGIN = pluginItem("blueprint_plugin", true);
    public static final Handle<Item> RANGE_CULLING_PLUGIN = pluginItem("range_culling_plugin", true);
    public static final Handle<Item> FIELD_DEPLOYMENT_PLUGIN = pluginItem("field_deployment_plugin", true);
    public static final Handle<Item> RANGE_EXTENSION_I = pluginItem("range_extension_i", true);
    public static final Handle<Item> RANGE_EXTENSION_II = pluginItem("range_extension_ii", true);
    public static final Handle<Item> RANGE_EXTENSION_III = pluginItem("range_extension_iii", true);
    public static final Handle<Item> RANGE_EXTENSION_MAX = pluginItem("range_extension_max", true);
    public static final Handle<Item> HARVEST_TIER_STONE = pluginItem("harvest_tier_stone", true);
    public static final Handle<Item> HARVEST_TIER_IRON = pluginItem("harvest_tier_iron", true);
    public static final Handle<Item> HARVEST_TIER_DIAMOND = pluginItem("harvest_tier_diamond", true);
    public static final Handle<Item> HARVEST_TIER_UNLIMITED = pluginItem("harvest_tier_unlimited", true);

    private static Handle<Item> pluginItem(String id, boolean creative) {
        return registerItem(id, RtsPluginItem::new, creative);
    }

    public static Handle<Item> simpleItem(String id, boolean creative) {
        return registerItem(id, Item::new, creative);
    }

    public static <T extends Item> Handle<T> registerItem(String id, Supplier<? extends T> factory,
            boolean creative) {
        T item = factory.get();
        configureItem(item, id, creative);
        Handle<T> handle = new Handle<>(id, item);
        ALL_ITEMS.add(handle);
        if (creative) CREATIVE_TAB_ITEMS.add(handle);
        return handle;
    }

    public static Handle<ItemBlock> blockItem(String id, RtsBlocks.Handle<? extends Block> block,
            boolean creative) {
        ItemBlock item = new ItemBlock(block.get());
        configureItem(item, id, creative);
        Handle<ItemBlock> handle = new Handle<>(id, item);
        ALL_ITEMS.add(handle);
        if (creative) CREATIVE_TAB_ITEMS.add(handle);
        return handle;
    }

    private static void configureItem(Item item, String id, boolean creative) {
        item.setUnlocalizedName(RtsbuildingMod.MODID + "." + id);
        // 1.7.10 不会从注册名自动推导材质域；缺少这一行时会去 minecraft 域查找，
        // 最终把所有插件物品渲染成紫黑缺失材质。
        item.setTextureName(RtsbuildingMod.MODID + ":" + id);
        item.setMaxStackSize(64);
        if (creative) item.setCreativeTab(RtsCreativeTabs.RTSBUILDING_TAB);
    }

    public static Set<Handle<? extends Item>> getCreativeTabItems() {
        return Collections.unmodifiableSet(CREATIVE_TAB_ITEMS);
    }

    public static Collection<Handle<? extends Item>> getAllItems() {
        return Collections.unmodifiableList(ALL_ITEMS);
    }

    public static synchronized void register() {
        if (registered) return;
        for (Handle<? extends Item> handle : ALL_ITEMS) {
            GameRegistry.registerItem(handle.get(), handle.id());
        }
        registered = true;
    }

    private static boolean registered;

    public static final class Handle<T> {
        private final String id;
        private final T value;

        private Handle(String id, T value) {
            this.id = id;
            this.value = value;
        }

        public String id() { return id; }
        public T get() { return value; }
    }

    private RtsItems() {
    }
}
