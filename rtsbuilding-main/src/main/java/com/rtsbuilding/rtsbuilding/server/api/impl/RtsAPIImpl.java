package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.*;
import com.rtsbuilding.rtsbuilding.api.energy.RtsEnergyAPI;
import org.jetbrains.annotations.ApiStatus;

/**
 * Default implementation of {@link RtsAPI} — delegates all calls to the domain service layer.
 *
 * <p>Third-party addon mods should not reference this class directly.
 * <p>Each sub-API implementation resides in its own file under the {@code api/impl/} directory.
 * <p>The energy sub-API is provided by the built-in {@code rtsbuilding_technologized} addon and
 * injected via {@link #setEnergyApi(RtsEnergyAPI)}; before that it is {@code null}.
 */
@ApiStatus.Internal
public final class RtsAPIImpl implements RtsAPI {

    private static final RtsAPIImpl INSTANCE = new RtsAPIImpl();

    private final RtsStorageQueryAPIImpl storageApi = new RtsStorageQueryAPIImpl();
    private final RtsBlueprintAPIImpl blueprintApi = new RtsBlueprintAPIImpl();
    private final RtsPlacementAPIImpl placementApi = new RtsPlacementAPIImpl();
    private final RtsInteractionAPIImpl interactionApi = new RtsInteractionAPIImpl();
    private final RtsMiningAPIImpl miningApi = new RtsMiningAPIImpl();
    private final RtsTransferAPIImpl transferApi = new RtsTransferAPIImpl();
    private final RtsFluidAPIImpl fluidApi = new RtsFluidAPIImpl();
    private final RtsBindingsAPIImpl bindingsApi = new RtsBindingsAPIImpl();
    private final RtsSessionQueryAPIImpl sessionApi = new RtsSessionQueryAPIImpl();

    @ApiStatus.Internal
    private volatile RtsEnergyAPI energyApi;

    private RtsAPIImpl() {
    }

    /** Initialize the API and register it via {@link RtsAPI#setImplementation(RtsAPI)}. */
    public static void init() {
        RtsAPI.setImplementation(INSTANCE);
    }

    /**
     * Injects the energy sub-API implementation, provided by the built-in
     * {@code rtsbuilding_technologized} addon.
     */
    @ApiStatus.Internal
    public static void setEnergyApi(RtsEnergyAPI energyApi) {
        INSTANCE.energyApi = energyApi;
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
    public RtsFluidAPI fluids() { return fluidApi; }

    @Override
    public RtsBindingsAPI bindings() { return bindingsApi; }

    @Override
    public RtsSessionQueryAPI sessions() { return sessionApi; }

    @Override
    public RtsEnergyAPI energy() { return energyApi; }
}
