package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.server.api.*;

/**
 * Default implementation of {@link RtsAPI} — delegates all calls to the
 * domain service layer.
 *
 * <p>Third-party addons should NOT reference this class directly.
 * <p>Each sub-API implementation lives in its own file under {@code api/impl/}.
 */
public final class RtsAPIImpl implements RtsAPI {

    private static final RtsAPIImpl INSTANCE = new RtsAPIImpl();

    private final RtsStorageQueryAPIImpl storageApi = new RtsStorageQueryAPIImpl();
    private final RtsBlueprintAPIImpl blueprintApi = new RtsBlueprintAPIImpl();
    private final RtsPlacementAPIImpl placementApi = new RtsPlacementAPIImpl();
    private final RtsInteractionAPIImpl interactionApi = new RtsInteractionAPIImpl();
    private final RtsMiningAPIImpl miningApi = new RtsMiningAPIImpl();
    private final RtsTransferAPIImpl transferApi = new RtsTransferAPIImpl();
    private final RtsCraftingAPIImpl craftingApi = new RtsCraftingAPIImpl();
    private final RtsFluidAPIImpl fluidApi = new RtsFluidAPIImpl();
    private final RtsBindingsAPIImpl bindingsApi = new RtsBindingsAPIImpl();
    private final RtsSessionQueryAPIImpl sessionApi = new RtsSessionQueryAPIImpl();

    private RtsAPIImpl() {
    }

    /** Initialises the API and registers it via {@link RtsAPI#setImplementation(RtsAPI)}. */
    public static void init() {
        RtsAPI.setImplementation(INSTANCE);
    }

    @Override
    public RtsStorageQueryAPI storage() { return storageApi; }

    @Override
    public RtsBlueprintAPI blueprint() { return blueprintApi; }

    @Override
    public RtsPlacementAPI placement() { return placementApi; }

    @Override
    public RtsInteractionAPI interaction() { return interactionApi; }

    @Override
    public RtsMiningAPI mining() { return miningApi; }

    @Override
    public RtsTransferAPI transfer() { return transferApi; }

    @Override
    public RtsCraftingAPI crafting() { return craftingApi; }

    @Override
    public RtsFluidAPI fluids() { return fluidApi; }

    @Override
    public RtsBindingsAPI bindings() { return bindingsApi; }

    @Override
    public RtsSessionQueryAPI sessions() { return sessionApi; }
}
