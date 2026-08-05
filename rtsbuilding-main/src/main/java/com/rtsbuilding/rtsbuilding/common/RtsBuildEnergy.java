package com.rtsbuilding.rtsbuilding.common;

import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Optional bridge to the build-operation energy cost, provided by the built-in
 * {@code rtsbuilding_technologized} addon.
 * <p>
 * The main mod's placement pipeline calls {@link #consumePlacement(ServerPlayer)}
 * after each successfully placed block; when no hook is installed (i.e. the
 * energy addon is not active) nothing is charged. The energy addon installs a
 * {@link Hook} to deduct FE from the player's energy grid.
 */
public final class RtsBuildEnergy {

    private static final AtomicReference<Hook> HOOK = new AtomicReference<>();

    private RtsBuildEnergy() {
    }

    /**
     * Energy cost applied per remotely placed block, supplied by the energy addon.
     */
    @FunctionalInterface
    public interface Hook {

        /**
         * Called after a block was successfully placed. Implementations deduct
         * the configured FE from the player's grid (best-effort).
         */
        void consumePlacement(ServerPlayer player);
    }

    /** Installs the build-energy hook (called by the energy addon). */
    public static void install(Hook hook) {
        HOOK.set(hook);
    }

    /**
     * Charges the player for one placed block. No-op when the energy addon is
     * not active.
     */
    public static void consumePlacement(ServerPlayer player) {
        Hook hook = HOOK.get();
        if (hook != null) {
            hook.consumePlacement(player);
        }
    }
}
