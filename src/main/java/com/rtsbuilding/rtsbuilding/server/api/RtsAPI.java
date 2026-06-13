package com.rtsbuilding.rtsbuilding.server.api;

/**
 * Main API entry point for the RTS Building module.
 *
 * <p>Third-party addons should access all RTS functionality through this interface.
 * Obtain the global singleton via {@link #get()}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Obtain API instance
 * RtsAPI api = RtsAPI.get();
 *
 * // Query the count of a specific item in a player's storage
 * long count = api.storage().countItems(player, Items.DIAMOND);
 * }</pre>
 *
 * <p>All methods are thread-safe. Default values are returned when no player session is active.
 */
public interface RtsAPI {

    /**
     * Returns the global {@link RtsAPI} instance.
     * Always available after mod initialisation is complete.
     */
    static RtsAPI get() {
        return Holder.INSTANCE;
    }

    // ======================================================================
    // Sub-APIs
    // ======================================================================

    /** Storage queries: count, extract, and return items/fluids */
    RtsStorageQueryAPI storage();

    /** Blueprint material queries and extraction */
    RtsBlueprintAPI blueprint();

    /** Remote block placement */
    RtsPlacementAPI placement();

    /** Remote interaction (right-click containers/entities, etc.) */
    RtsInteractionAPI interaction();

    /** Remote mining and vein mining */
    RtsMiningAPI mining();

    /** Item transfer between linked storage and player inventory */
    RtsTransferAPI transfer();

    /** Crafting terminal operations */
    RtsCraftingAPI crafting();

    /** Fluid operations */
    RtsFluidAPI fluids();

    /** Storage binding management */
    RtsBindingsAPI bindings();

    /** Session queries */
    RtsSessionQueryAPI sessions();

    /**
     * Sets the internal implementation. Called by the RTS core during mod initialisation.
     *
     * @throws IllegalStateException if the implementation has already been set
     */
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
