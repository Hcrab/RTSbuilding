package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.platform.Platform;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative mode tab registry — all RTSbuilding creative tabs are registered centrally here.
 * <p>
 * Currently contains one main tab that automatically collects all items and blocks
 * marked as creative and displays them within it.
 */
public final class RtsCreativeTabs {

    /** Unified creative tab registry instance */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = Platform.creativeTabRegister();

    /** RTSbuilding main tab — contains all mod items and blocks */
    @SuppressWarnings("unused")
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RTSBUILDING_TAB = CREATIVE_TABS.register(
            "rtsbuilding",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.rtsbuilding"))
                    .icon(() -> new ItemStack(RtsItems.RTS_CONTROL_CORE.get()))
                    .displayItems((parameters, output) -> {
                        for (var holder : RtsItems.getCreativeTabItems()) {
                            output.accept(holder.get());
                        }
                        for (var holder : RtsBlocks.getCreativeTabBlocks()) {
                            output.accept(holder.get());
                        }
                    })
                    .build());

    // ============================================================
    //  Registration entry point
    // ============================================================

    /**
     * Register all creative tabs on the mod event bus.
     *
     * @param modEventBus The mod event bus
     */
    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }

    private RtsCreativeTabs() {
    }
}
