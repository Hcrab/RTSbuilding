package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.platform.Platform;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Block registry — all RTSbuilding blocks are registered centrally here.
 * <p>
 * Uses {@link DeferredRegister} for lazy registration, ensuring it is completed at the correct registration phase.
 * Provides two factory methods: {@link #simpleBlock(String, BlockBehaviour.Properties, boolean)} for
 * ordinary blocks and {@link #registerBlock(String, java.util.function.Supplier, boolean)} for custom block subclasses.
 * Use {@link #getCreativeTabBlocks()} to retrieve the list of blocks to be added to the creative tab.
 */
public final class RtsBlocks {

    /** Unified block registry instance */
    public static final DeferredRegister<Block> BLOCKS = Platform.blockRegister();

    /** Set of blocks that need to be automatically registered in the creative tab (ordered by registration order) */
    private static final Set<DeferredHolder<Block, ? extends Block>> CREATIVE_TAB_BLOCKS = new LinkedHashSet<>();

    // ============================================================
    //  Block definitions
    // ============================================================

    // Example block registration (uncomment to use)
    // public static final DeferredHolder<Block, Block> EXAMPLE_BLOCK = simpleBlock("example_block",
    //         BlockBehaviour.Properties.of().strength(2.0f).requiresCorrectToolForDrops(),
    //         true);

    // ============================================================
    //  Factory methods
    // ============================================================

    /**
     * Register a simple ordinary block.
     *
     * @param id         The registry name of the block
     * @param properties Block properties (hardness, sound, etc.)
     * @param creative   Whether to automatically add to the creative tab
     * @return The block's {@link DeferredHolder}
     */
    public static DeferredHolder<Block, Block> simpleBlock(String id, BlockBehaviour.Properties properties, boolean creative) {
        DeferredHolder<Block, Block> holder = BLOCKS.register(id, () -> new Block(properties));
        if (creative) {
            CREATIVE_TAB_BLOCKS.add(holder);
        }
        return holder;
    }

    /**
     * Register a block of any custom {@link Block} subclass.
     *
     * @param id       The registry name of the block
     * @param factory  Factory function for creating the block instance
     * @param creative Whether to automatically add to the creative tab
     * @return The block's {@link DeferredHolder}
     */
    public static <T extends Block> DeferredHolder<Block, T> registerBlock(String id,
            java.util.function.Supplier<? extends T> factory, boolean creative) {
        DeferredHolder<Block, T> holder = BLOCKS.register(id, factory);
        if (creative) {
            CREATIVE_TAB_BLOCKS.add(holder);
        }
        return holder;
    }

    // ============================================================
    //  Registration entry point
    // ============================================================

    /**
     * Register all blocks on the mod event bus.
     * Should be called in the constructor of {@link RtsbuildingMod}.
     */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    // ============================================================
    //  Utility methods
    // ============================================================

    /**
     * Get all blocks marked with {@code creative = true}.
     *
     * @return An unmodifiable set of creative tab blocks, ordered by registration order
     */
    public static Set<DeferredHolder<Block, ? extends Block>> getCreativeTabBlocks() {
        return Collections.unmodifiableSet(CREATIVE_TAB_BLOCKS);
    }

    /**
     * Get the list of all registered block {@link DeferredHolder}s.
     */
    public static java.util.Collection<DeferredHolder<Block, ? extends Block>> getAllBlocks() {
        return BLOCKS.getEntries();
    }

    private RtsBlocks() {
    }
}
