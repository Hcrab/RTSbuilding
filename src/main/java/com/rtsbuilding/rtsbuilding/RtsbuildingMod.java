package com.rtsbuilding.rtsbuilding;

import com.rtsbuilding.rtsbuilding.common.RtsBlocks;
import com.rtsbuilding.rtsbuilding.common.RtsCreativeTabs;
import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import com.rtsbuilding.rtsbuilding.common.RtsItems;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsAPIImpl;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import com.rtsbuilding.rtsbuilding.server.diagnostic.RtsOperationDiagnostics;
import com.rtsbuilding.rtsbuilding.server.feedback.RtsDamageFeedbackManager;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.RtsPipelineRegistration;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsDeveloperMetrics;
import com.rtsbuilding.rtsbuilding.server.service.RtsDeveloperScenarioCommand;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.RtsProgressRefresher;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.ServerTickOrchestrator;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsStoragePageRequestCoalescer;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsEndpointLeaseCache;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;

/**
 * RTSBuilding 的 Forge 1.12.2 入口。
 *
 * <p>FML 生命周期只留在本类；游戏运行事件由实例化的 {@link GameEvents} 接收。入口不引用
 * net.minecraft.client 或客户端 bootstrap，因此专用服务端可以安全加载整个类。</p>
 */
@Mod(modid = RtsbuildingMod.MODID)
public final class RtsbuildingMod {
    public static final String MODID = "rtsbuilding";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static RtsbuildingMod INSTANCE;

    private final GameEvents gameEvents = new GameEvents();
    private MinecraftServer activeServer;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.initialize(event.getModConfigurationDirectory(), event.getSide().isClient());
        if (Config.migrateLegacyServerDefaults()) {
            LOGGER.info("已迁移 RTSBuilding 旧版服务端吞吐默认值。");
        }
        // TODO(port-1.12.2/client-config-ui): 由客户端批次通过 1.12 GuiFactory 接回配置界面；
        // 此处只加载同一份 client.cfg，专用服务端绝不触发客户端类加载。

        // 1.12.2 的 Block/Item 由 RegistryEvent 提交；显式调用用于在事件前完成类初始化。
        RtsCreativeTabs.register();
        RtsBlocks.register();
        RtsItems.register();
        RtsEntities.register(this);
        MinecraftForge.EVENT_BUS.register(gameEvents);
        if (event.getSide().isClient()) {
            initializeClientSide();
        }

        // TODO(port-1.12.2/gametest): 1.12.2 没有 RegisterGameTestsEvent；测试模块必须把
        // MekanismToolsCompatibilityGameTests 接入统一的 Forge 测试命令/测试世界入口，不能静默丢弃。
    }

    /**
     * 通过字符串边界接入客户端，保证专用服务端验证和加载本类时不会解析任何 client 类型。
     */
    private static void initializeClientSide() {
        try {
            Class<?> bootstrap = Class.forName(
                    "com.rtsbuilding.rtsbuilding.client.bootstrap.RtsClientBootstrap");
            bootstrap.getMethod("registerClient").invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("注册 RTSBuilding 1.12 客户端生命周期失败", failure);
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ServiceRegistry.init();
        RtsAPIImpl.init();
        RtsPipelineRegistration.registerAll();
        RtsOperationDiagnostics.install();
        LOGGER.info("RTSBuilding 通用初始化完成");
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        activeServer = event.getServer();
        event.registerServerCommand(new RtsDeveloperScenarioCommand());
        try {
            // 必须先于 durable task admission 读取；损坏时拒绝以空仓继续启动。
            TaskPersistenceRuntime.INSTANCE.start(activeServer);
        } catch (RuntimeException failure) {
            LOGGER.error("读取 durable task 仓库失败，服务器将 fail-closed 停止启动", failure);
            throw failure;
        }
        LOGGER.info("服务器正在启动……");
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        MinecraftServer server = requireActiveServer();
        RtsEffectAccumulator.INSTANCE.resetForServerStart();
        RtsCameraManager.cleanupOrphanCameras(server);
        SaveScheduler.INSTANCE.cleanupLegacyFiles(server);
        RtsWorkflowEngine.getInstance().startTimeoutService(Duration.ofSeconds(1), Duration.ofSeconds(30));
    }

    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        MinecraftServer server = requireActiveServer();
        try {
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                RtsTaskEngine.INSTANCE.preparePlayerDetach(player);
            }
            RtsTaskEngine.INSTANCE.checkpointAllDurableExecutions(server);
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                TaskPersistenceRuntime.INSTANCE.flushOwner(player.getUniqueID());
                RtsTaskEngine.INSTANCE.reconcilePlayerDetach(player);
            }
        } catch (RuntimeException failure) {
            LOGGER.error("停服时 durable task 冻结失败；未确认的 dirty 不会被伪装成已落盘", failure);
            throw failure;
        }
    }

    @Mod.EventHandler
    public void onServerStopped(FMLServerStoppedEvent event) {
        MinecraftServer server = requireActiveServer();
        RuntimeException durableFailure = null;
        RtsWorkflowEngine.getInstance().stopTimeoutService();
        try {
            if (TaskPersistenceRuntime.INSTANCE.isStarted()) {
                TaskPersistenceRuntime.INSTANCE.stop();
            }
            RtsTaskEngine.INSTANCE.resetDurableRuntimeAfterServerStop();
        } catch (RuntimeException failure) {
            durableFailure = failure;
            LOGGER.error("服务器停止后关闭 durable task writer 失败；保留故障状态以阻止静默复用", failure);
        }

        try {
            RtsWorkflowEngine.getInstance().saveAll(server);
            SaveScheduler.INSTANCE.onServerStopped();
            RtsWorkflowEngine.getInstance().clearAllData();
            RtsStoragePageRequestCoalescer.clearAll();
            RtsEffectAccumulator.INSTANCE.clearAll();
            RtsDeveloperMetrics.clearAll();
        } finally {
            activeServer = null;
        }
        if (durableFailure != null) throw durableFailure;
    }

    private MinecraftServer requireActiveServer() {
        if (activeServer == null) {
            MinecraftServer fallback = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (fallback != null) return fallback;
            throw new IllegalStateException("RTSBuilding 服务器生命周期缺少活动 MinecraftServer");
        }
        return activeServer;
    }

    /** 游戏运行事件；只使用共同端和服务端类型。 */
    private static final class GameEvents {
        @SubscribeEvent
        public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.player instanceof EntityPlayerMP)) return;
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            MinecraftServer server = player.getServer();
            RtsCameraManager.cleanupOrphanCameras(server);
            RtsDamageFeedbackManager.remember(player);
            RtsProgressionManager.onPlayerLogin(player);
            RtsPluginService.syncRelatedPlayers(player);
            RtsWorkflowEngine.getInstance().loadPlayerFromStore(server, player);
            RtsWorkflowEngine.getInstance().refreshPlayerIdleClocks(player);
        }

        @SubscribeEvent
        public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (!(event.player instanceof EntityPlayerMP)) return;
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            try {
                RtsTaskEngine.INSTANCE.preparePlayerDetach(player);
                RtsTaskEngine.INSTANCE.detachPlayer(player.getUniqueID());
                TaskPersistenceRuntime.INSTANCE.flushOwner(player.getUniqueID());
                RtsTaskEngine.INSTANCE.reconcilePlayerDetach(player);
            } catch (RuntimeException failure) {
                LOGGER.error("玩家 {} 登出时 durable task 冲刷失败，已保留 dirty 并拒绝静默继续",
                        player.getUniqueID(), failure);
            }
            RtsCameraManager.stopIfActive(player);
            RtsDamageFeedbackManager.forget(player);
            ServiceRegistry.getInstance().session().onPlayerLogout(player);
            RtsProgressionManager.onPlayerLogout(player);
            RtsPendingPlacementService.clearPlayerScanCache(player.getUniqueID());
            RtsPlacementSound.forgetPlayer(player.getUniqueID());
            RtsProgressRefresher.clearPlayerCache(player.getUniqueID());
            RtsStoragePageRequestCoalescer.clearPlayer(player.getUniqueID());
            RtsDeveloperMetrics.clearPlayer(player.getUniqueID());
            RtsPluginService.syncRelatedPlayers(player);
            RtsEffectAccumulator.INSTANCE.clearPlayer(player.getUniqueID());
            ServerHistoryManager.clear(player.getUniqueID());
            SaveScheduler.INSTANCE.onPlayerLogout(player);
        }

        @SubscribeEvent
        public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (!(event.player instanceof EntityPlayerMP)) return;
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            RtsCameraManager.stopIfActive(player);
            ServiceRegistry.getInstance().pathfinding().cancel(player);
            RtsStorageTickService.INSTANCE.unregisterPlayer(player);
            RtsEndpointLeaseCache.INSTANCE.invalidatePlayer(player.getUniqueID());
            RtsEffectAccumulator.INSTANCE.clearDimension(player.getUniqueID(), event.fromDim);
        }

        /** 仅唤醒等待这个 chunk 的任务，不扫描玩家或全服任务。 */
        @SubscribeEvent
        public void onChunkLoad(ChunkEvent.Load event) {
            if (event.getWorld() instanceof WorldServer) {
                RtsTaskEngine.INSTANCE.resumeLoadedChunk((WorldServer) event.getWorld(), event.getChunk().getPos());
            }
        }

        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) return;
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            ServerTickOrchestrator.getInstance().onPlayerTickPost(player);
            RtsDamageFeedbackManager.tick(player);
        }

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) return;
            ServerTickOrchestrator.getInstance().tickMining(server);
            SaveScheduler.INSTANCE.onTick(server);
            TaskPersistenceRuntime.INSTANCE.tick();
        }
    }
}
