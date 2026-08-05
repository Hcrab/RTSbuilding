package com.rtsbuilding.rtsbuilding.energy;

import com.rtsbuilding.rtsbuilding.energy.block.RtsEnergyBankBlock;
import com.rtsbuilding.rtsbuilding.energy.block.RtsThermalGeneratorBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Block registry for the built-in energy addon ({@code rtsbuilding_technologized}).
 */
public final class RtsEnergyBlocks {

    /** Unified block registry instance for the energy namespace */
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, RtsEnergyMod.MODID);

    private static final Set<DeferredHolder<Block, ? extends Block>> CREATIVE_TAB_BLOCKS = new LinkedHashSet<>();

    /** Energy bank — block-level FE storage buffer (part of the player's energy grid). */
    public static final DeferredHolder<Block, RtsEnergyBankBlock> ENERGY_BANK = registerBlock(
            "energy_bank",
            () -> new RtsEnergyBankBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()),
            true);

    /** Thermal generator — burns lava to produce FE for the owner's grid. */
    public static final DeferredHolder<Block, RtsThermalGeneratorBlock> THERMAL_GENERATOR = registerBlock(
            "thermal_generator",
            () -> new RtsThermalGeneratorBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RtsThermalGeneratorBlock.LIT) ? 14 : 0)
                    .requiresCorrectToolForDrops()),
            true);

    public static <T extends Block> DeferredHolder<Block, T> registerBlock(String id,
            java.util.function.Supplier<? extends T> factory, boolean creative) {
        DeferredHolder<Block, T> holder = BLOCKS.register(id, factory);
        if (creative) {
            CREATIVE_TAB_BLOCKS.add(holder);
        }
        return holder;
    }

    /** Registers all blocks on the energy mod's event bus. */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    public static Set<DeferredHolder<Block, ? extends Block>> getCreativeTabBlocks() {
        return Collections.unmodifiableSet(CREATIVE_TAB_BLOCKS);
    }

    private RtsEnergyBlocks() {
    }
}
