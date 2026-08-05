package com.rtsbuilding.rtsbuilding.energy;

import com.mojang.serialization.Codec;
import com.rtsbuilding.rtsbuilding.common.RtsItems;
import com.rtsbuilding.rtsbuilding.common.RtsTerminalEnergy;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Terminal energy for the {@code rtsbuilding_technologized} addon.
 * <p>
 * The main mod's {@code rts_terminal} is durability-free by default; this class
 * is what makes it energy-powered again: it registers the terminal energy data
 * component and the standard {@code IEnergyStorage} item capability, and installs
 * the {@link RtsTerminalEnergy.Provider} so enabling RTS mode consumes FE.
 * <p>
 * Values follow Mekanism's energy-tablet conventions (1,000,000 FE capacity,
 * 5,000 FE/t charge rate, bright-green bar).
 */
public final class RtsTerminalEnergyImpl {

    /** Maximum storable energy in FE (Mekanism energy-tablet tier: 1,000,000 J) */
    public static final int MAX_ENERGY = 1_000_000;
    /** Energy consumed each time RTS mode is turned on */
    public static final int ENERGY_PER_USE = 500;
    /** Maximum receive rate in FE/t (charging; Mekanism tablet: 5,000 J/t) */
    public static final int MAX_RECEIVE = 5_000;
    /** Maximum extract rate in FE/t */
    public static final int MAX_EXTRACT = 5_000;
    /** Mekanism-style energy bar color (bright green) */
    public static final int ENERGY_BAR_COLOR = 0x3CFE9A;
    /** Tooltip label color — Mekanism EnumColor.BRIGHT_GREEN */
    public static final int TOOLTIP_LABEL_COLOR = 0x55FF55;
    /** Tooltip value color — Mekanism EnumColor.GRAY */
    public static final int TOOLTIP_VALUE_COLOR = 0xAAAAAA;

    /** Data component registry for the energy namespace */
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.DataComponents.createDataComponents(Registries.DATA_COMPONENT_TYPE, RtsEnergyMod.MODID);

    /** Terminal energy component — the FE charge persisted on the item stack */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TERMINAL_ENERGY =
            DATA_COMPONENTS.registerComponentType("terminal_energy",
                    builder -> builder.persistent(Codec.INT));

    private static final RtsTerminalEnergy.Provider PROVIDER = new RtsTerminalEnergy.Provider() {
        @Override
        public boolean hasEnergy(ItemStack stack) {
            IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
            return energy != null && energy.getEnergyStored() >= ENERGY_PER_USE;
        }

        @Override
        public boolean consume(ItemStack stack) {
            IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
            if (energy == null || energy.getEnergyStored() < ENERGY_PER_USE) {
                return false;
            }
            energy.extractEnergy(ENERGY_PER_USE, false);
            return true;
        }

        @Override
        public int energyBarColor() {
            return ENERGY_BAR_COLOR;
        }

        @Override
        public int tooltipLabelColor() {
            return TOOLTIP_LABEL_COLOR;
        }

        @Override
        public int tooltipValueColor() {
            return TOOLTIP_VALUE_COLOR;
        }
    };

    private RtsTerminalEnergyImpl() {
    }

    /** Registers the terminal energy data component and capability on the energy mod's event bus. */
    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
        modEventBus.addListener(RtsTerminalEnergyImpl::registerCapabilities);
    }

    /** Installs the terminal energy provider (only while the addon is enabled). */
    public static void installProvider() {
        RtsTerminalEnergy.install(PROVIDER);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.EnergyStorage.ITEM,
                RtsTerminalEnergyImpl::createEnergyStorage, RtsItems.RTS_TERMINAL.get());
    }

    /**
     * Item energy capability provider — lazily initializes a fresh terminal to
     * full charge and exposes a component-backed energy storage for the stack.
     */
    public static IEnergyStorage createEnergyStorage(ItemStack stack, @Nullable Void context) {
        if (!stack.has(TERMINAL_ENERGY.get())) {
            stack.set(TERMINAL_ENERGY.get(), MAX_ENERGY);
        }
        return new ComponentEnergyStorage(stack, TERMINAL_ENERGY.get(),
                MAX_ENERGY, MAX_RECEIVE, MAX_EXTRACT);
    }
}
