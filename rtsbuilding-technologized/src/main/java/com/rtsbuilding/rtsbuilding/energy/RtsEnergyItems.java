package com.rtsbuilding.rtsbuilding.energy;

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
 * Item registry for the built-in energy addon ({@code rtsbuilding_technologized}).
 */
public final class RtsEnergyItems {

    /** Unified item registry instance for the energy namespace */
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, RtsEnergyMod.MODID);

    private static final Set<DeferredHolder<Item, ? extends Item>> CREATIVE_TAB_ITEMS = new LinkedHashSet<>();

    /** Energy bank block item */
    public static final DeferredHolder<Item, BlockItem> ENERGY_BANK = blockItem(
            "energy_bank", RtsEnergyBlocks.ENERGY_BANK, true);

    /** Thermal generator block item */
    public static final DeferredHolder<Item, BlockItem> THERMAL_GENERATOR = blockItem(
            "thermal_generator", RtsEnergyBlocks.THERMAL_GENERATOR, true);

    public static DeferredHolder<Item, BlockItem> blockItem(String id,
            DeferredHolder<Block, ? extends Block> block, boolean creative) {
        DeferredHolder<Item, BlockItem> holder = ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        if (creative) {
            CREATIVE_TAB_ITEMS.add(holder);
        }
        return holder;
    }

    /** Registers all items on the energy mod's event bus. */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static Set<DeferredHolder<Item, ? extends Item>> getCreativeTabItems() {
        return Collections.unmodifiableSet(CREATIVE_TAB_ITEMS);
    }

    private RtsEnergyItems() {
    }
}
