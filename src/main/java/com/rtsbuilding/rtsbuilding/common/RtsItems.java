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
    /** 跨维度储存插件，允许短期唤醒并访问异维度的已连接储存。 */
    public static final RegistryObject<Item> CROSS_DIMENSION_STORAGE_PLUGIN =
            pluginItem("cross_dimension_storage_plugin", true);
    public static final RegistryObject<Item> RANGE_EXTENSION_I = pluginItem("range_extension_i", true);
    public static final RegistryObject<Item> RANGE_EXTENSION_II = pluginItem("range_extension_ii", true);
    public static final RegistryObject<Item> RANGE_EXTENSION_III = pluginItem("range_extension_iii", true);
    public static final RegistryObject<Item> RANGE_EXTENSION_MAX = pluginItem("range_extension_max", true);
    /** 允许非连锁范围采掘石制等级方块。 */
    public static final RegistryObject<Item> HARVEST_TIER_STONE = pluginItem("harvest_tier_stone", true);
    /** 允许非连锁范围采掘铁制等级方块。 */
    public static final RegistryObject<Item> HARVEST_TIER_IRON = pluginItem("harvest_tier_iron", true);
    /** 允许非连锁范围采掘钻石等级方块。 */
    public static final RegistryObject<Item> HARVEST_TIER_DIAMOND = pluginItem("harvest_tier_diamond", true);
    /** 解除范围采掘的插件等级上限，但仍保留真实工具检查。 */
    public static final RegistryObject<Item> HARVEST_TIER_UNLIMITED =
            pluginItem("harvest_tier_unlimited", true);

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
