package com.rtsbuilding.rtsbuilding.energy;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative tab for the built-in energy addon.
 */
public final class RtsEnergyCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RtsEnergyMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ENERGY_TAB = CREATIVE_TABS.register(
            "energy",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.rtsbuilding_technologized"))
                    .icon(() -> new ItemStack(RtsEnergyItems.ENERGY_BANK.get()))
                    .displayItems((parameters, output) -> {
                        for (var holder : RtsEnergyItems.getCreativeTabItems()) {
                            output.accept(holder.get());
                        }
                        for (var holder : RtsEnergyBlocks.getCreativeTabBlocks()) {
                            output.accept(holder.get());
                        }
                    })
                    .build());

    /** Registers all creative tabs on the energy mod's event bus. */
    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }

    private RtsEnergyCreativeTabs() {
    }
}
