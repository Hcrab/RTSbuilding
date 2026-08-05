package com.rtsbuilding.rtsbuilding.energy;

import com.rtsbuilding.rtsbuilding.energy.block.entity.RtsEnergyBankBlockEntity;
import com.rtsbuilding.rtsbuilding.energy.block.entity.RtsThermalGeneratorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block entity registry for the built-in energy addon ({@code rtsbuilding_technologized}).
 */
public final class RtsEnergyBlockEntities {

    /** Unified block entity registry instance for the energy namespace */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RtsEnergyMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RtsEnergyBankBlockEntity>> ENERGY_BANK =
            BLOCK_ENTITY_TYPES.register("energy_bank", () ->
                    BlockEntityType.Builder.of(RtsEnergyBankBlockEntity::new, RtsEnergyBlocks.ENERGY_BANK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RtsThermalGeneratorBlockEntity>> THERMAL_GENERATOR =
            BLOCK_ENTITY_TYPES.register("thermal_generator", () ->
                    BlockEntityType.Builder.of(RtsThermalGeneratorBlockEntity::new, RtsEnergyBlocks.THERMAL_GENERATOR.get()).build(null));

    /** Registers all block entity types on the energy mod's event bus. */
    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }

    private RtsEnergyBlockEntities() {
    }
}
