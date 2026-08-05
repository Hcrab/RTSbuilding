package com.rtsbuilding.rtsbuilding.energy;

import com.rtsbuilding.rtsbuilding.energy.block.entity.ContainerEnergyStorage;
import com.rtsbuilding.rtsbuilding.energy.block.entity.RtsEnergyBankBlockEntity;
import com.rtsbuilding.rtsbuilding.energy.block.entity.RtsThermalGeneratorBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Capability registration for the built-in energy addon.
 * <p>
 * Energy banks expose their buffer as a standard {@code IEnergyStorage} (both
 * directions), and thermal generators expose an extract-only
 * {@code IEnergyStorage} plus a lava {@code IFluidHandler}.
 */
public final class RtsEnergyCapabilities {

    private RtsEnergyCapabilities() {
    }

    /** Registers all block capabilities on the energy mod's event bus. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RtsEnergyCapabilities::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // When the addon is disabled by config, expose no capabilities at all.
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side) -> !com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled() ? null
                        : blockEntity instanceof RtsEnergyBankBlockEntity bank
                                ? new ContainerEnergyStorage(bank.getBuffer(), true, true)
                                : null,
                RtsEnergyBlocks.ENERGY_BANK.get());

        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side) -> !com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled() ? null
                        : blockEntity instanceof RtsThermalGeneratorBlockEntity generator
                                ? new ContainerEnergyStorage(generator.getBuffer(), false, true)
                                : null,
                RtsEnergyBlocks.THERMAL_GENERATOR.get());

        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> !com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled() ? null
                        : blockEntity instanceof RtsThermalGeneratorBlockEntity generator
                                ? generator.getTank()
                                : null,
                RtsEnergyBlocks.THERMAL_GENERATOR.get());
    }
}
