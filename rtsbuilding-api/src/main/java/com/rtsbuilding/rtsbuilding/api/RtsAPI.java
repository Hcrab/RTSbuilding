package com.rtsbuilding.rtsbuilding.api;

import com.rtsbuilding.rtsbuilding.api.energy.RtsEnergyAPI;
import org.jetbrains.annotations.ApiStatus;

/**
 * Main API entry point for the RTS Building mod.
 *
 * <p>Third-party addon mods should access all RTS functionality through this interface.
 * Obtain the global singleton via {@link #get()}.
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Get the API instance
 * RtsAPI api = RtsAPI.get();
 *
 * // Query the amount of a specific item in player storage
 * long count = api.storage().countItems(player, Items.DIAMOND);
 * }</pre>
 *
 * <p>All methods are thread-safe. Returns default values when no player session is active.
 */
public interface RtsAPI {

    /**
     * Returns the global {@link RtsAPI} instance.
     * Always available after mod initialization.
     */
    static RtsAPI get() {
        return Holder.INSTANCE;
    }

    // ======================================================================
    //  Sub-APIs
    // ======================================================================

    /** Storage query: count, extract, and return items/fluids */
    RtsStorageQueryAPI storage();

    /** Blueprint material query and extraction */
    RtsBlueprintAPI blueprint();

    /** Remote block placement */
    RtsPlacementAPI placement();

    /** Remote interaction (right-click containers/entities, etc.) */
    RtsInteractionAPI interaction();

    /** Remote mining and ultimine */
    RtsMiningAPI mining();

    /** Item transfer between linked storage and player inventory */
    RtsTransferAPI transfer();

    /** Crafting terminal operations */
    RtsCraftingAPI crafting();

    /** Fluid operations */
    RtsFluidAPI fluids();

    /** Storage binding management */
    RtsBindingsAPI bindings();

    /** Session query */
    RtsSessionQueryAPI sessions();

    /** Energy grid access (per-player energy storage/generation) */
    RtsEnergyAPI energy();

    /**
     * Set the internal implementation. Called by the RTS core during mod initialization.
     * <p>
     * This is an internal API — addon mods should not call this directly.
     *
     * @throws IllegalStateException if the implementation has already been set
     */
    @ApiStatus.Internal
    static void setImplementation(RtsAPI implementation) {
        if (Holder.INSTANCE != null && implementation != null) {
            throw new IllegalStateException("RtsAPI implementation already set");
        }
        Holder.INSTANCE = implementation;
    }

    final class Holder {
        private Holder() {
        }

        static RtsAPI INSTANCE;
    }
}
