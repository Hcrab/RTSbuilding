package com.rtsbuilding.rtsbuilding.client.infrastructure.di;

import com.rtsbuilding.rtsbuilding.client.application.port.GameTickPort;
import com.rtsbuilding.rtsbuilding.client.application.port.RenderFramePort;
import com.rtsbuilding.rtsbuilding.client.application.service.EventBusImpl;
import com.rtsbuilding.rtsbuilding.client.application.service.ModuleManager;
import com.rtsbuilding.rtsbuilding.client.application.service.ScreenCoordinator;
import com.rtsbuilding.rtsbuilding.client.application.service.SessionService;
import com.rtsbuilding.rtsbuilding.client.domain.event.EventBus;
import com.rtsbuilding.rtsbuilding.client.domain.module.capability.RenderFrameAware;
import com.rtsbuilding.rtsbuilding.client.domain.time.Clock;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.render.RenderPipeline;
import com.rtsbuilding.rtsbuilding.client.infrastructure.network.adapter.ClientNetworkAdapter;
import com.rtsbuilding.rtsbuilding.client.infrastructure.time.MinecraftClock;

public final class CompositionRoot {
    private static CompositionRoot INSTANCE;

    private final MinecraftClock clock;
    private final EventBus eventBus;
    private final ModuleManager moduleManager;
    private final SessionService sessionService;
    private final ScreenCoordinator screenCoordinator;
    private final ClientNetworkAdapter networkAdapter;

    private final GameTickPort tickPort = new GameTickPort() {
        @Override
        public void onTickPre() {
            clock.tick();
        }

        @Override
        public void onTickPost() {
            moduleManager.tick();
        }
    };

    private final RenderFramePort renderFramePort = new RenderFramePort() {
        @Override
        public void registerRenderPass(RenderFrameAware pass) {
            // Phase 4: modules register via ModuleManager, not directly here
        }

        @Override
        public void onRenderFrame(float partialTick) {
            moduleManager.onRenderFrame(partialTick);
        }
    };

    private CompositionRoot() {
        this.clock = new MinecraftClock();
        this.eventBus = new EventBusImpl();
        this.moduleManager = new ModuleManager(eventBus, clock);
        this.sessionService = new SessionService(eventBus);
        this.screenCoordinator = new ScreenCoordinator();
        this.networkAdapter = new ClientNetworkAdapter();
    }

    public static void init() {
        INSTANCE = new CompositionRoot();
    }

    public static CompositionRoot get() {
        return INSTANCE;
    }

    // ======================== 新系统接口 ========================

    public GameTickPort tickPort() { return tickPort; }
    public RenderFramePort renderFramePort() { return renderFramePort; }
    public EventBus eventBus() { return eventBus; }
    public ModuleManager moduleManager() { return moduleManager; }
    public SessionService sessionService() { return sessionService; }
    public ScreenCoordinator screenCoordinator() { return screenCoordinator; }
    public Clock clock() { return clock; }
    public ClientNetworkAdapter networkAdapter() { return networkAdapter; }

    // ======================== 旧内核桥接（迁移期间使用）=======================

    /** 获取旧内核实例。迁移期间使用。 */
    public RtsClientKernel kernel() { return RtsClientKernel.get(); }

    /** 按类型查找模块（先查新系统，回退旧系统）。 */
    @SuppressWarnings("unchecked")
    public <T> T module(Class<T> type) {
        T m = moduleManager.module(type);
        if (m != null) return m;
        return (T) RtsClientKernel.get().module((Class) type);
    }

    /** 分发事件。 */
    public void dispatch(StateEvent event) {
        RtsClientKernel.get().dispatch(event);
    }

    /** 获取渲染管线。 */
    public RenderPipeline renderPipeline() {
        return RtsClientKernel.get().renderPipeline();
    }
}
