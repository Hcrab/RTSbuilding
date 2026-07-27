package com.rtsbuilding.rtsbuilding.server;

import com.rtsbuilding.rtsbuilding.server.service.ServiceOperationTemplate;
import com.rtsbuilding.rtsbuilding.server.service.impl.*;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public final class RtsServer {

    private static RtsServer INSTANCE;

    private RtsPathfindingServiceImpl pathfindingService;
    private RtsBindingServiceImpl bindingService;
    private RtsFunnelServiceImpl funnelService;
    private RtsPageServiceImpl pageService;
    private RtsCraftingServiceImpl craftingService;
    private RtsTransferServiceImpl transferService;
    private RtsInteractionServiceImpl interactionService;
    private RtsMiningServiceImpl miningService;
    private RtsPlacementServiceImpl placementService;
    private RtsFluidServiceImpl fluidService;
    private RtsSessionServiceImpl sessionService;
    private RtsBlueprintServiceImpl blueprintService;
    private ServiceOperationTemplate serviceOp;

    private RtsServer() {}

    public static RtsServer init() {
        if (INSTANCE == null) {
            INSTANCE = new RtsServer();
            INSTANCE.discoverServices();
        }
        return INSTANCE;
    }

    @SuppressWarnings("unchecked")
    private void discoverServices() {
        Map<Class<?>, ServiceLoader.Provider<RtsService>> providers = ServiceLoader.load(RtsService.class).stream()
                .collect(Collectors.toMap(p -> p.type(), p -> p));

        this.pathfindingService = get(providers, RtsPathfindingServiceImpl.class);
        this.bindingService = get(providers, RtsBindingServiceImpl.class);
        this.funnelService = get(providers, RtsFunnelServiceImpl.class);
        this.craftingService = get(providers, RtsCraftingServiceImpl.class);
        this.transferService = get(providers, RtsTransferServiceImpl.class);
        this.interactionService = get(providers, RtsInteractionServiceImpl.class);
        this.miningService = get(providers, RtsMiningServiceImpl.class);
        this.placementService = get(providers, RtsPlacementServiceImpl.class);
        this.fluidService = get(providers, RtsFluidServiceImpl.class);
        this.blueprintService = get(providers, RtsBlueprintServiceImpl.class);
        // pageService before sessionService (RtsSessionServiceImpl accesses server.page() during construction)
        this.pageService = get(providers, RtsPageServiceImpl.class);
        this.sessionService = get(providers, RtsSessionServiceImpl.class);

        this.serviceOp = new ServiceOperationTemplate(this);
    }

    @SuppressWarnings("unchecked")
    private <T> T get(Map<Class<?>, ServiceLoader.Provider<RtsService>> providers, Class<T> type) {
        ServiceLoader.Provider<RtsService> provider = providers.get(type);
        if (provider == null) {
            throw new IllegalStateException("Missing RTS service: " + type.getName()
                    + ". Check META-INF/services/" + RtsService.class.getName());
        }
        return (T) provider.get();
    }

    public static RtsServer get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("RtsServer not initialized. Call init() first.");
        }
        return INSTANCE;
    }

    // ── Service accessors ──

    public RtsPathfindingServiceImpl pathfinding() { return pathfindingService; }
    public RtsBindingServiceImpl binding() { return bindingService; }
    public RtsFunnelServiceImpl funnel() { return funnelService; }
    public RtsPageServiceImpl page() { return pageService; }
    public RtsCraftingServiceImpl crafting() { return craftingService; }
    public RtsTransferServiceImpl transfer() { return transferService; }
    public RtsInteractionServiceImpl interaction() { return interactionService; }
    public RtsMiningServiceImpl mining() { return miningService; }
    public RtsPlacementServiceImpl placement() { return placementService; }
    public RtsFluidServiceImpl fluid() { return fluidService; }
    public RtsSessionServiceImpl session() { return sessionService; }
    public RtsBlueprintServiceImpl blueprint() { return blueprintService; }
    public ServiceOperationTemplate serviceOp() { return serviceOp; }
}
