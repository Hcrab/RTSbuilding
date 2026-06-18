package com.rtsbuilding.rtsbuilding.server.storage.session;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * Cached BD (Better Description) network state scoped to a single
 * RtsStorageSession.
 *
 * <p>Extracted from RtsStorageSession to group the five BD network
 * cache fields into a single value object. Owned and mutated exclusively by
 * {@link com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService}
 * and session lifecycle hooks.
 */
public final class BdCacheState {

    /** BD network item handler ({@link IItemHandler}), null = not cached. */
    @Nullable
    public IItemHandler handler;

    /** BD network fluid handler ({@link IFluidHandler}), null = not cached. */
    @Nullable
    public IFluidHandler fluidHandler;

    /** BD network display name. */
    @Nullable
    public String name;

    /** Stale flag for item handler. Set to {@code true} before resolution to
     *  force a refresh. */
    public boolean handlerStale;

    /** Stale flag for fluid handler. Set to {@code true} before resolution to
     *  force a refresh. */
    public boolean fluidHandlerStale;

    /**
     * Nulls out all references so the GC can reclaim the previously held
     * handler objects immediately.
     */
    public void release() {
        this.handler = null;
        this.fluidHandler = null;
        this.name = null;
    }
}
