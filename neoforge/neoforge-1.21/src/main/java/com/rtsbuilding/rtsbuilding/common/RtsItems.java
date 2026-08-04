package com.rtsbuilding.rtsbuilding.common;

import com.mojang.serialization.Codec;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.item.RtsTerminalItem;
import com.rtsbuilding.rtsbuilding.platform.Platform;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Item registry — all RTSbuilding items are registered centrally here.
 * <p>
 * Provides four factory methods: {@link #simpleItem(String, boolean)},
 * {@link #pluginItem(String, boolean)}, {@link #registerItem(String, Supplier, boolean)},
 * and {@link #blockItem(String, DeferredHolder, boolean)},
 * for ordinary items, inventory plugin items, custom items, and block items respectively.
 */
public final class RtsItems {

    // ============================================================
    //  Registry core
    // ============================================================

    /** Unified item registry instance */
    public static final DeferredRegister<Item> ITEMS = Platform.itemRegister();

    /** Data component registry — stores per-stack energy (FE) for the RTS terminal */
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.DataComponents.createDataComponents(Registries.DATA_COMPONENT_TYPE, RtsbuildingMod.MODID);

    /** Terminal energy component — the FE charge persisted on the item stack */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TERMINAL_ENERGY =
            DATA_COMPONENTS.registerComponentType("terminal_energy",
                    builder -> builder.persistent(Codec.INT));

    /** Terminal UUID component — unique per-stack id recorded when RTS mode is enabled,
     *  used to lock that very terminal against pickup/enable actions while RTS mode is active */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TERMINAL_UUID =
            DATA_COMPONENTS.registerComponentType("terminal_uuid",
                    builder -> builder.persistent(Codec.STRING));

    /** Set of items that need to be automatically registered in the creative tab (ordered by registration order) */
    private static final Set<DeferredHolder<Item, ? extends Item>> CREATIVE_TAB_ITEMS = new LinkedHashSet<>();

    // ============================================================
    //  Inventory plugin items
    // ============================================================

    /** Core control chip — essential item for activating RTS camera mode */
    public static final DeferredHolder<Item, Item> RTS_CONTROL_CORE = pluginItem("rts_control_core", true);
    /** Remote control plugin — enables remote interaction and placement */
    public static final DeferredHolder<Item, Item> REMOTE_CONTROL_PLUGIN = pluginItem("remote_control_plugin", true);
    /** Storage integration plugin — connects inventory to the remote storage network */
    public static final DeferredHolder<Item, Item> STORAGE_INTEGRATION_PLUGIN = pluginItem("storage_integration_plugin", true);
    /** Craft terminal plugin — remote access to crafting table functionality */
    public static final DeferredHolder<Item, Item> CRAFT_TERMINAL_PLUGIN = pluginItem("craft_terminal_plugin", true);
    /** Chain break plugin — one-click chain mining of the same type of block */
    public static final DeferredHolder<Item, Item> CHAIN_BREAK_PLUGIN = pluginItem("chain_break_plugin", true);
    /** Area destroy plugin — destroys blocks within a region at once */
    public static final DeferredHolder<Item, Item> AREA_DESTROY_PLUGIN = pluginItem("area_destroy_plugin", true);
    /** Blueprint plugin — save and reproduce building structures */
    public static final DeferredHolder<Item, Item> BLUEPRINT_PLUGIN = pluginItem("blueprint_plugin", true);
    /** Field deployment plugin — quickly deploy saved blueprints */
    public static final DeferredHolder<Item, Item> FIELD_DEPLOYMENT_PLUGIN = pluginItem("field_deployment_plugin", true);
    /** Range extension I — expands the base action radius */
    public static final DeferredHolder<Item, Item> RANGE_EXTENSION_I = pluginItem("range_extension_i", true);
    /** Range extension II — further expands the action radius */
    public static final DeferredHolder<Item, Item> RANGE_EXTENSION_II = pluginItem("range_extension_ii", true);
    /** Range extension III — significantly expands the action radius */
    public static final DeferredHolder<Item, Item> RANGE_EXTENSION_III = pluginItem("range_extension_iii", true);
    /** Range extension Max — maximizes the action radius */
    public static final DeferredHolder<Item, Item> RANGE_EXTENSION_MAX = pluginItem("range_extension_max", true);

    // ============================================================
    //  Terminal items
    // ============================================================

    /** RTS terminal — the handheld management console of the RTS system (non-stackable, energy-powered) */
    public static final DeferredHolder<Item, Item> RTS_TERMINAL = registerItem(
            "rts_terminal", () -> new RtsTerminalItem(new Item.Properties().stacksTo(1)), true);

    // ============================================================
    //  Factory methods
    // ============================================================

    /**
     * Register an {@link RtsPluginItem} plugin item.
     * Plugin items trigger installation logic on right-click, with a default max stack size of 64.
     */
    private static DeferredHolder<Item, Item> pluginItem(String id, boolean creative) {
        DeferredHolder<Item, Item> holder = ITEMS.register(id, () -> new RtsPluginItem(new Item.Properties().stacksTo(64)));
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }

    /**
     * Register a simple ordinary item (no special behavior).
     *
     * @param id       The registry name of the item
     * @param creative Whether to automatically add to the creative tab
     * @return The item's {@link DeferredHolder}
     */
    public static DeferredHolder<Item, Item> simpleItem(String id, boolean creative) {
        DeferredHolder<Item, Item> holder = ITEMS.register(id, () -> new Item(new Item.Properties()));
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }

    /**
     * Register a simple item with custom {@link Item.Properties}.
     *
     * @param id         The registry name of the item
     * @param properties Item properties (durability, stack size, etc.)
     * @param creative   Whether to automatically add to the creative tab
     * @return The item's {@link DeferredHolder}
     */
    public static DeferredHolder<Item, Item> simpleItem(String id, Item.Properties properties, boolean creative) {
        DeferredHolder<Item, Item> holder = ITEMS.register(id, () -> new Item(properties));
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }

    /**
     * Register an item of any custom {@link Item} subclass.
     *
     * @param id       The registry name of the item
     * @param factory  Factory function for creating the item instance
     * @param creative Whether to automatically add to the creative tab
     * @return The item's {@link DeferredHolder}
     */
    public static DeferredHolder<Item, Item> registerItem(String id, java.util.function.Supplier<? extends Item> factory, boolean creative) {
        DeferredHolder<Item, Item> holder = ITEMS.register(id, factory);
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }

    // ============================================================
    //  Registration entry point
    // ============================================================

    /**
     * Register all items on the mod event bus.
     *
     * @param modEventBus The mod event bus
     */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        modEventBus.addListener(RtsItems::registerCapabilities);
    }

    /**
     * Register item capabilities — exposes the native FE energy capability
     * for the RTS terminal so any charger mod can recharge it.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.EnergyStorage.ITEM,
                RtsTerminalItem::createEnergyStorage, RTS_TERMINAL.get());
    }

    // ============================================================
    //  Utility methods
    // ============================================================

    /**
     * Register a {@link BlockItem} for an already registered block.
     *
     * @param id       The registry name of the block item
     * @param block    The corresponding block
     * @param creative Whether to automatically add to the creative tab
     * @return The block item's {@link DeferredHolder}
     */
    public static DeferredHolder<Item, BlockItem> blockItem(String id,
            DeferredHolder<Block, ? extends Block> block, boolean creative) {
        DeferredHolder<Item, BlockItem> holder = ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }

    /**
     * Register a {@link BlockItem} with custom properties for an already registered block.
     *
     * @param id         The registry name of the block item
     * @param block      The corresponding block
     * @param properties Custom item properties
     * @param creative   Whether to automatically add to the creative tab
     * @return The block item's {@link DeferredHolder}
     */
    public static DeferredHolder<Item, BlockItem> blockItem(String id,
            DeferredHolder<Block, ? extends Block> block, Item.Properties properties, boolean creative) {
        DeferredHolder<Item, BlockItem> holder = ITEMS.register(id, () -> new BlockItem(block.get(), properties));
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }

    /**
     * Get all items marked with {@code creative = true}.
     *
     * @return An unmodifiable set of creative tab items, ordered by registration order
     */
    public static Set<DeferredHolder<Item, ? extends Item>> getCreativeTabItems() {
        return Collections.unmodifiableSet(CREATIVE_TAB_ITEMS);
    }

    /**
     * Get the list of all registered item {@link DeferredHolder}s.
     */
    public static java.util.Collection<DeferredHolder<Item, ? extends Item>> getAllItems() {
        return ITEMS.getEntries();
    }

    private RtsItems() {
    }
}
