package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginItem;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 物品注册器 —— RTSbuilding 的所有物品在此集中注册。
 * <p>
 * 提供 {@link #simpleItem(String, boolean)}、{@link #pluginItem(String, boolean)}、
 * {@link #registerItem(String, java.util.function.Supplier, boolean)} 和
 * {@link #blockItem(String, DeferredHolder, boolean)} 四种工厂方法，
 * 分别用于普通物品、背包插件物品、自定义物品和方块物品。
 */
public final class RtsItems {

    // ============================================================
    //  注册表核心
    // ============================================================

    /** 统一的物品注册表实例 */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, RtsbuildingMod.MODID);

    /** 需要自动注册到创造栏的物品集合（按注册顺序排列） */
    private static final Set<DeferredHolder<Item, ? extends Item>> CREATIVE_TAB_ITEMS = new LinkedHashSet<>();

    // ============================================================
    //  背包插件物品
    // ============================================================

    /** 核心控制芯片 —— 启动 RTS 相机模式的必备物品 */
    public static final DeferredHolder<Item, Item> RTS_CONTROL_CORE = pluginItem("rts_control_core", true);
    /** 远程操作插件 —— 允许远程交互与放置 */
    public static final DeferredHolder<Item, Item> REMOTE_CONTROL_PLUGIN = pluginItem("remote_control_plugin", true);
    /** 存储集成插件 —— 将背包接入远程存储网络 */
    public static final DeferredHolder<Item, Item> STORAGE_INTEGRATION_PLUGIN = pluginItem("storage_integration_plugin", true);
    /** 合成终端插件 —— 远程访问合成台功能 */
    public static final DeferredHolder<Item, Item> CRAFT_TERMINAL_PLUGIN = pluginItem("craft_terminal_plugin", true);
    /** 连锁破坏插件 —— 一键连锁挖掘同种方块 */
    public static final DeferredHolder<Item, Item> CHAIN_BREAK_PLUGIN = pluginItem("chain_break_plugin", true);
    /** 范围破坏插件 —— 一次性破坏区域内的方块 */
    public static final DeferredHolder<Item, Item> AREA_DESTROY_PLUGIN = pluginItem("area_destroy_plugin", true);
    /** 蓝图插件 —— 保存并复现建筑结构 */
    public static final DeferredHolder<Item, Item> BLUEPRINT_PLUGIN = pluginItem("blueprint_plugin", true);
    /** 范围剔除插件 - 在 RTS 内绘制剔除盒，隐藏盒内已有方块并让射线穿透。 */
    public static final DeferredHolder<Item, Item> RANGE_CULLING_PLUGIN = pluginItem("range_culling_plugin", true);
    /** 现场部署插件 —— 快速部署已保存的蓝图 */
    public static final DeferredHolder<Item, Item> FIELD_DEPLOYMENT_PLUGIN = pluginItem("field_deployment_plugin", true);
    /** 范围扩展 I —— 扩大基础操作半径 */
    public static final DeferredHolder<Item, Item> RANGE_EXTENSION_I = pluginItem("range_extension_i", true);
    /** 范围扩展 II —— 进一步扩大操作半径 */
    public static final DeferredHolder<Item, Item> RANGE_EXTENSION_II = pluginItem("range_extension_ii", true);
    /** 范围扩展 III —— 大幅扩大操作半径 */
    public static final DeferredHolder<Item, Item> RANGE_EXTENSION_III = pluginItem("range_extension_iii", true);
    /** 范围扩展 Max —— 极限扩大操作半径 */
    public static final DeferredHolder<Item, Item> RANGE_EXTENSION_MAX = pluginItem("range_extension_max", true);

    // ============================================================
    //  工厂方法
    // ============================================================

    /**
     * 注册一个 {@link RtsPluginItem} 插件物品。
     * 插件物品右击时触发安装逻辑，默认最大堆叠 64 个。
     */
    private static DeferredHolder<Item, Item> pluginItem(String id, boolean creative) {
        DeferredHolder<Item, Item> holder = ITEMS.register(id,
                () -> new RtsPluginItem(properties(id).stacksTo(64)));
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }

    /**
     * 注册一个普通的简单物品（无特殊行为）。
     *
     * @param id       物品的注册名
     * @param creative 是否自动添加到创造栏
     * @return 物品的 {@link DeferredHolder}
     */
    public static DeferredHolder<Item, Item> simpleItem(String id, boolean creative) {
        DeferredHolder<Item, Item> holder = ITEMS.register(id, () -> new Item(properties(id)));
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }

    /**
     * 注册一个带有自定义 {@link Item.Properties} 的简单物品。
     *
     * @param id         物品的注册名
     * @param properties 物品属性（耐久、堆叠数等）
     * @param creative   是否自动添加到创造栏
     * @return 物品的 {@link DeferredHolder}
     */
    public static DeferredHolder<Item, Item> simpleItem(String id, Item.Properties properties, boolean creative) {
        DeferredHolder<Item, Item> holder = ITEMS.register(id, () -> new Item(withId(id, properties)));
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }

    /**
     * 注册任意自定义 {@link Item} 子类的物品。
     *
     * @param id       物品的注册名
     * @param factory  创建物品实例的工厂函数
     * @param creative 是否自动添加到创造栏
     * @return 物品的 {@link DeferredHolder}
     */
    public static DeferredHolder<Item, Item> registerItem(String id, java.util.function.Supplier<? extends Item> factory, boolean creative) {
        DeferredHolder<Item, Item> holder = ITEMS.register(id, factory);
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }

    // ============================================================
    //  注册入口
    // ============================================================

    /**
     * 在模组总线上注册所有物品。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    // ============================================================
    //  工具方法
    // ============================================================

    /**
     * 为已注册的方块注册对应的 {@link BlockItem}。
     *
     * @param id       方块物品的注册名
     * @param block    对应的方块
     * @param creative 是否自动添加到创造栏
     * @return 方块物品的 {@link DeferredHolder}
     */
    public static DeferredHolder<Item, BlockItem> blockItem(String id,
            DeferredHolder<Block, ? extends Block> block, boolean creative) {
        DeferredHolder<Item, BlockItem> holder = ITEMS.register(id,
                () -> new BlockItem(block.get(), properties(id)));
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }

    /**
     * 为已注册的方块注册带有自定义属性的 {@link BlockItem}。
     *
     * @param id         方块物品的注册名
     * @param block      对应的方块
     * @param properties 自定义物品属性
     * @param creative   是否自动添加到创造栏
     * @return 方块物品的 {@link DeferredHolder}
     */
    public static DeferredHolder<Item, BlockItem> blockItem(String id,
            DeferredHolder<Block, ? extends Block> block, Item.Properties properties, boolean creative) {
        DeferredHolder<Item, BlockItem> holder = ITEMS.register(id,
                () -> new BlockItem(block.get(), withId(id, properties)));
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }

    /**
     * 获取所有标记为 {@code creative = true} 的物品集合。
     *
     * @return 不可修改的创造栏物品集合，按注册顺序排列
     */
    public static Set<DeferredHolder<Item, ? extends Item>> getCreativeTabItems() {
        return Collections.unmodifiableSet(CREATIVE_TAB_ITEMS);
    }

    /**
     * 获取所有已注册物品的 {@link DeferredHolder} 列表。
     */
    public static java.util.Collection<DeferredHolder<Item, ? extends Item>> getAllItems() {
        return ITEMS.getEntries();
    }

    /**
     * 26.1 起物品会在构造阶段解析翻译键和模型，因此属性必须先知道最终注册 ID。
     * 把这条新约束收口在注册器内，避免各个物品类自行拼接资源键。
     */
    private static Item.Properties properties(String id) {
        return withId(id, new Item.Properties());
    }

    private static Item.Properties withId(String id, Item.Properties properties) {
        ResourceKey<Item> itemKey = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, id));
        return properties.setId(itemKey);
    }

    private RtsItems() {
    }
}
