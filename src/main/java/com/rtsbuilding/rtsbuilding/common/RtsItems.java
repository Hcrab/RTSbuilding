package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Forge 1.20.1 的 RTSBuilding 物品注册入口。
 *
 * <p>当前只注册生产插件物品。物品本身只负责触发安装动作，实际能力、队伍共享和卸载规则都在
 * {@code server.plugin} 服务层里判定，避免 UI 或物品类各自保存一套规则。
 */
public final class RtsItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RtsbuildingMod.MODID);

    private static final Set<RegistryObject<? extends Item>> CREATIVE_TAB_ITEMS = new LinkedHashSet<>();

    public static final RegistryObject<Item> RTS_CONTROL_CORE = pluginItem("rts_control_core", true);
    public static final RegistryObject<Item> REMOTE_CONTROL_PLUGIN = pluginItem("remote_control_plugin", true);
    public static final RegistryObject<Item> STORAGE_INTEGRATION_PLUGIN = pluginItem("storage_integration_plugin", true);
    public static final RegistryObject<Item> CRAFT_TERMINAL_PLUGIN = pluginItem("craft_terminal_plugin", true);
    public static final RegistryObject<Item> CHAIN_BREAK_PLUGIN = pluginItem("chain_break_plugin", true);
    public static final RegistryObject<Item> AREA_DESTROY_PLUGIN = pluginItem("area_destroy_plugin", true);
    public static final RegistryObject<Item> BLUEPRINT_PLUGIN = pluginItem("blueprint_plugin", true);
    public static final RegistryObject<Item> RANGE_CULLING_PLUGIN = pluginItem("range_culling_plugin", true);
    public static final RegistryObject<Item> FIELD_DEPLOYMENT_PLUGIN = pluginItem("field_deployment_plugin", true);
    public static final RegistryObject<Item> RANGE_EXTENSION_I = pluginItem("range_extension_i", true);
    public static final RegistryObject<Item> RANGE_EXTENSION_II = pluginItem("range_extension_ii", true);
    public static final RegistryObject<Item> RANGE_EXTENSION_III = pluginItem("range_extension_iii", true);
    public static final RegistryObject<Item> RANGE_EXTENSION_MAX = pluginItem("range_extension_max", true);

    private RtsItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static Set<RegistryObject<? extends Item>> getCreativeTabItems() {
        return Collections.unmodifiableSet(CREATIVE_TAB_ITEMS);
    }

    private static RegistryObject<Item> pluginItem(String id, boolean creative) {
        RegistryObject<Item> holder = ITEMS.register(id, () -> new RtsPluginItem(new Item.Properties().stacksTo(64)));
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }
}
