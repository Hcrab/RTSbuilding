package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.service.api.*;
import com.rtsbuilding.rtsbuilding.server.service.impl.*;

/**
 * 服务注册中心——管理所有 RTS 服务的实例化与生命周期。
 *
 * <p>Phase 2 重构的中央依赖管理器。所有 Service 应通过此注册中心
 * 获取实例，而非直接调用静态方法。随着重构推进，静态 Service 类
 * 将逐步替换为接口 + 实现类 + 构造器注入。
 */
public final class ServiceRegistry {

    private static ServiceRegistry INSTANCE;

    private final PathfindingService pathfindingService;
    private final BindingService bindingService;
    private final FunnelService funnelService;
    private final PageService pageService;
    private final CraftingService craftingService;
    private final TransferService transferService;
    private final InteractionService interactionService;
    private final MiningService miningService;
    private final PlacementService placementService;
    private final FluidService fluidService;
    private final SessionService sessionService;

    private ServiceRegistry(/* future: MinecraftServer server */) {
        this.pathfindingService = new RtsPathfindingServiceImpl();
        this.bindingService = new RtsBindingServiceImpl();
        this.funnelService = new RtsFunnelServiceImpl();
        this.pageService = new RtsPageServiceImpl();
        this.craftingService = new RtsCraftingServiceImpl();
        this.transferService = new RtsTransferServiceImpl();
        this.interactionService = new RtsInteractionServiceImpl();
        this.miningService = new RtsMiningServiceImpl();
        this.placementService = new RtsPlacementServiceImpl();
        this.fluidService = new RtsFluidServiceImpl();
        this.sessionService = new RtsSessionServiceImpl();
    }

    /**
     * 初始化服务注册中心。应在服务器启动时调用一次。
     */
    public static ServiceRegistry init() {
        if (INSTANCE == null) {
            INSTANCE = new ServiceRegistry();
        }
        return INSTANCE;
    }

    /**
     * 获取全局单例。
     */
    public static ServiceRegistry getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("ServiceRegistry not initialized. Call init() first.");
        }
        return INSTANCE;
    }

    // ======================================================================
    // Service 访问器
    // ======================================================================

    public PathfindingService pathfinding() {
        return pathfindingService;
    }

    public BindingService binding() {
        return bindingService;
    }

    public FunnelService funnel() {
        return funnelService;
    }

    public PageService page() {
        return pageService;
    }

    public CraftingService crafting() {
        return craftingService;
    }

    public TransferService transfer() {
        return transferService;
    }

    public InteractionService interaction() {
        return interactionService;
    }

    public MiningService mining() {
        return miningService;
    }

    public PlacementService placement() {
        return placementService;
    }

    public FluidService fluid() {
        return fluidService;
    }

    public SessionService session() {
        return sessionService;
    }
}
