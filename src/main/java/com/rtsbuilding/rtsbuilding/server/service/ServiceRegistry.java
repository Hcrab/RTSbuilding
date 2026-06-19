package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.service.api.*;
import com.rtsbuilding.rtsbuilding.server.service.impl.*;
import javax.annotation.Nullable;

/**
 * 服务注册中心——管理所有 RTS 服务的实例化与生命周期。
 *
 * <p>Phase 2 重构引入的中央依赖管理器。所有 Service 应通过此注册中心
 * 获取实例，而非直接调用静态方法。随着重构推进，静态工具 Service 类
 * 将逐步替换为接口 + 实现类 + 构造器注入模式。
 *
 * <p><b>注册的服务（共 13 个）：</b>
 * <ul>
 *   <li>{@link #pathfinding()} — 寻路服务</li>
 *   <li>{@link #binding()} — 绑定服务（链接存储/快捷槽）</li>
 *   <li>{@link #funnel()} — 漏斗服务（物品收集）</li>
 *   <li>{@link #page()} — 储存浏览器页面服务</li>
 *   <li>{@link #crafting()} — 合成终端服务</li>
 *   <li>{@link #transfer()} — 物品传输服务</li>
 *   <li>{@link #interaction()} — 远程交互服务</li>
 *   <li>{@link #mining()} — 远程挖掘服务</li>
 *   <li>{@link #placement()} — 远程放置服务</li>
 *   <li>{@link #fluid()} — 流体服务</li>
 *   <li>{@link #session()} — 会话服务</li>
 *   <li>{@link #blueprint()} — 蓝图服务（可为 null，蓝图功能可选）</li>
 *   <li>{@link #serviceOp()} — 服务操作模板</li>
 * </ul>
 *
 * <p><b>生命周期：</b>
 * <ul>
 *   <li>{@link #init()} — 服务器启动时调用一次，创建全局单例并初始化所有服务</li>
 *   <li>{@link #getInstance()} — 获取全局单例，未初始化时抛出 {@link IllegalStateException}</li>
 * </ul>
 */
public final class ServiceRegistry {

    private static ServiceRegistry INSTANCE;

    private PathfindingService pathfindingService;
    private BindingService bindingService;
    private FunnelService funnelService;
    private PageService pageService;
    private CraftingService craftingService;
    private TransferService transferService;
    private InteractionService interactionService;
    private MiningService miningService;
    private PlacementService placementService;
    private FluidService fluidService;
    private SessionService sessionService;
    private BlueprintService blueprintService;
    private ServiceOperationTemplate serviceOperationTemplate;

    private ServiceRegistry() {
    }

    /**
     * 初始化服务注册中心。应在服务器启动时调用一次。
     */
    public static ServiceRegistry init() {
        if (INSTANCE == null) {
            INSTANCE = new ServiceRegistry();
            INSTANCE.initializeServices();
        }
        return INSTANCE;
    }

    private void initializeServices() {
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
        this.blueprintService = new RtsBlueprintServiceImpl();
        this.serviceOperationTemplate = new ServiceOperationTemplate(this);
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

    @Nullable
    public BlueprintService blueprint() {
        return blueprintService;
    }

    public ServiceOperationTemplate serviceOp() {
        return serviceOperationTemplate;
    }
}
