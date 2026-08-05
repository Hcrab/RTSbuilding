package com.rtsbuilding.rtsbuilding.common;

import com.mojang.serialization.Codec;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.item.RtsTerminalItem;
import com.rtsbuilding.rtsbuilding.platform.Platform;
import net.minecraft.core.component.DataComponentType;
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
import java.util.function.Supplier;

/**
 * Item registry — all RTSbuilding items are registered centrally here.
 * <p>
 * Provides two factory methods: {@link #simpleItem(String, boolean)}
 * and {@link #registerItem(String, Supplier, boolean)},
 * for ordinary items and custom items respectively.
 */
public final class RtsItems {

    // ============================================================
    //  Registry core
    // ============================================================

    /** Unified item registry instance */
    public static final DeferredRegister<Item> ITEMS = Platform.itemRegister();

    /** Data component registry — stores per-stack data for RTS items */
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.DataComponents.createDataComponents(Registries.DATA_COMPONENT_TYPE, RtsbuildingMod.MODID);

    /** Terminal UUID component — unique per-stack id recorded when RTS mode is enabled,
     *  used to lock that very terminal against pickup/enable actions while RTS mode is active */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TERMINAL_UUID =
            DATA_COMPONENTS.registerComponentType("terminal_uuid",
                    builder -> builder.persistent(Codec.STRING));

    /** Set of items that need to be automatically registered in the creative tab (ordered by registration order) */
    private static final Set<DeferredHolder<Item, ? extends Item>> CREATIVE_TAB_ITEMS = new LinkedHashSet<>();

    // ============================================================
    //  Terminal items
    // ============================================================

    /** RTS terminal — the handheld management console of the RTS system.
     *  Durability-free by default; the rtsbuilding_technologized addon installs
     *  the energy capability to make it energy-powered. */
    public static final DeferredHolder<Item, Item> RTS_TERMINAL = registerItem(
            "rts_terminal", () -> new RtsTerminalItem(new Item.Properties().stacksTo(1)), true);

    // ============================================================
    //  Factory methods
    // ============================================================

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
