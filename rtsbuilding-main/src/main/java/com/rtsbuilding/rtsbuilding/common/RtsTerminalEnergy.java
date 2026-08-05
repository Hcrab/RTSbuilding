package com.rtsbuilding.rtsbuilding.common;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Optional bridge to the terminal energy system, provided by the built-in
 * {@code rtsbuilding_technologized} addon.
 * <p>
 * The main mod's {@code rts_terminal} is a plain, durability-free item by
 * default: when no energy provider is installed it can be used unlimited times
 * and shows no energy bar. The energy addon installs a {@link Provider} plus
 * the item energy capability to make the terminal energy-powered again.
 */
public final class RtsTerminalEnergy {

    private static final AtomicReference<Provider> PROVIDER = new AtomicReference<>();

    private RtsTerminalEnergy() {
    }

    /**
     * Energy behavior for the RTS terminal, supplied by the energy addon.
     */
    public interface Provider {

        /** @return {@code true} if the stack holds enough energy to enable RTS mode once. */
        boolean hasEnergy(ItemStack stack);

        /**
         * Atomically checks and deducts the energy needed to enable RTS mode once.
         *
         * @return {@code true} if the energy was available and has been consumed.
         */
        boolean consume(ItemStack stack);

        /** Energy bar color (ARGB), e.g. Mekanism bright green {@code 0x3CFE9A}. */
        int energyBarColor();

        /** Tooltip label color (ARGB). */
        int tooltipLabelColor();

        /** Tooltip value color (ARGB). */
        int tooltipValueColor();
    }

    /** Installs the energy provider (called by the energy addon). */
    public static void install(Provider provider) {
        PROVIDER.set(provider);
    }

    /** @return The installed provider, or {@code null} when no energy addon is present. */
    @Nullable
    public static Provider get() {
        return PROVIDER.get();
    }
}
