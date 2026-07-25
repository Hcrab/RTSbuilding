package com.rtsbuilding.rtsbuilding;

import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.common.RtsBlocks;
import com.rtsbuilding.rtsbuilding.common.RtsCreativeTabs;
import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import com.rtsbuilding.rtsbuilding.common.RtsItems;
import com.rtsbuilding.rtsbuilding.network.RtsForgePayloadRegistrar;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsAPIImpl;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import com.rtsbuilding.rtsbuilding.server.feedback.RtsDamageFeedbackManager;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.diagnostic.RtsOperationDiagnostics;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.RtsPipelineRegistration;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsBenchmarkCommand;
import com.rtsbuilding.rtsbuilding.server.service.RtsDeveloperMetrics;
import com.rtsbuilding.rtsbuilding.server.service.RtsGuiCompatSetupCommand;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.time.Duration;

/**
 * Forge 1.20.1 平台入口。
 *
 * <p>业务生命周期与 1.21.1 主线保持同构；这里只翻译 Forge 的注册表和事件类型，
 * 不再维护第二套会话、蓝图或后台任务调度。</p>
 */
@Mod(RtsbuildingMod.MODID)
public final class RtsbuildingMod {
    public static final String MODID = "rtsbuilding";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RtsbuildingMod(final FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);

        RtsEntities.register(modEventBus);
        RtsBlocks.register(modEventBus);
        RtsItems.register(modEventBus);
        RtsCreativeTabs.register(modEventBus);
        RtsForgePayloadRegistrar.register();
        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.rtsbuilding.rtsbuilding.client.bootstrap.RtsClientBootstrap
                        .registerConfigUi(ModLoadingContext.get()));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ServiceRegistry.init();
        RtsAPIImpl.init();
        RtsPipelineRegistration.registerAll();
        RtsOperationDiagnostics.install();
        LOGGER.info("RTSBuilding Forge common setup complete");
    }

    private void onConfigLoading(ModConfigEvent.Loading event) {
        migrateServerConfigIfNeeded(event.getConfig());
    }

    private void onConfigReloading(ModConfigEvent.Reloading event) {
        migrateServerConfigIfNeeded(event.getConfig());
    }

    private void migrateServerConfigIfNeeded(ModConfig config) {
        if (config != null && config.getSpec() == Config.SPEC && Config.migrateLegacyServerDefaults()) {
            LOGGER.info("已迁移 RTSBuilding 旧版服务端默认值。");
        }
    }

    @SubscribeEvent
    public void onServerStarting(final ServerStartingEvent event) {
        try {
            TaskPersistenceRuntime.INSTANCE.start(event.getServer());
        } catch (RuntimeException failure) {
            LOGGER.error("读取 durable task 仓库失败，服务端将 fail-closed 停止启动", failure);
            throw failure;
        }
    }

    /**
     * 业务事件统一从这里进入。事件签名是 Forge 1.20.1 的平台插头，调用顺序与主线一致。
     */
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    static final class GameEvents {
        private GameEvents() {
        }

        @SubscribeEvent
        static void onPlayerLogin(final PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                RtsCameraManager.cleanupOrphanCameras(player.getServer());
                RtsDamageFeedbackManager.remember(player);
                RtsProgressionManager.onPlayerLogin(player);
                RtsPluginService.syncRelatedPlayers(player);
                RtsWorkflowEngine.getInstance().loadPlayerFromStore(player.getServer(), player);
                RtsWorkflowEngine.getInstance().refreshPlayerIdleClocks(player);
            }
        }

        @SubscribeEvent
        static void onPlayerClone(final PlayerEvent.Clone event) {
            if (event.getOriginal() instanceof ServerPlayer original
                    && event.getEntity() instanceof ServerPlayer replacement) {
                com.rtsbuilding.rtsbuilding.server.culling.RtsCullingPersistence.copyFrom(original, replacement);
            }
        }

        @SubscribeEvent
        static void onServerStarted(final ServerStartedEvent event) {
            RtsEffectAccumulator.INSTANCE.resetForServerStart();
            RtsCameraManager.cleanupOrphanCameras(event.getServer());
            SaveScheduler.INSTANCE.cleanupLegacyFiles(event.getServer());
            RtsWorkflowEngine.getInstance().startTimeoutService(
                    Duration.ofSeconds(1), Duration.ofSeconds(30));
        }

        @SubscribeEvent
        static void onServerStopping(final ServerStoppingEvent event) {
            try {
                for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                    RtsTaskEngine.INSTANCE.preparePlayerDetach(player);
                }
                RtsTaskEngine.INSTANCE.checkpointAllDurableExecutions(event.getServer());
                for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                    TaskPersistenceRuntime.INSTANCE.flushOwner(player.getUUID());
                    RtsTaskEngine.INSTANCE.reconcilePlayerDetach(player);
                }
            } catch (RuntimeException failure) {
                LOGGER.error("停服时 durable task 冻结失败，未确认的 dirty 数据不会伪装成已落盘", failure);
                throw failure;
            }
        }

        @SubscribeEvent
        static void onServerStopped(final ServerStoppedEvent event) {
            RuntimeException durableFailure = null;
            RtsWorkflowEngine.getInstance().stopTimeoutService();
            try {
                if (TaskPersistenceRuntime.INSTANCE.isStarted()) {
                    TaskPersistenceRuntime.INSTANCE.stop();
                }
                RtsTaskEngine.INSTANCE.resetDurableRuntimeAfterServerStop();
            } catch (RuntimeException failure) {
                durableFailure = failure;
                LOGGER.error("服务端停止后关闭 durable task writer 失败", failure);
            }
            RtsWorkflowEngine.getInstance().saveAll(event.getServer());
            SaveScheduler.INSTANCE.onServerStopped();
            RtsWorkflowEngine.getInstance().clearAllData();
            RtsStoragePageRequestCoalescer.clearAll();
            RtsEffectAccumulator.INSTANCE.clearAll();
            RtsDeveloperMetrics.clearAll();
            if (durableFailure != null) {
                throw durableFailure;
            }
        }

        @SubscribeEvent
        static void onPlayerLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            try {
                RtsTaskEngine.INSTANCE.preparePlayerDetach(player);
                RtsTaskEngine.INSTANCE.detachPlayer(player.getUUID());
                TaskPersistenceRuntime.INSTANCE.flushOwner(player.getUUID());
                RtsTaskEngine.INSTANCE.reconcilePlayerDetach(player);
            } catch (RuntimeException failure) {
                LOGGER.error("玩家 {} 登出时 durable task 冲刷失败，已保留 dirty 状态", player.getUUID(), failure);
            }

            RtsCameraManager.stopIfActive(player);
            RtsDamageFeedbackManager.forget(player);
            ServiceRegistry.getInstance().session().onPlayerLogout(player);
            RtsProgressionManager.onPlayerLogout(player);
            RtsPendingPlacementService.clearPlayerScanCache(player.getUUID());
            RtsPlacementSound.forgetPlayer(player.getUUID());
            RtsProgressRefresher.clearPlayerCache(player.getUUID());
            RtsStoragePageRequestCoalescer.clearPlayer(player.getUUID());
            RtsDeveloperMetrics.clearPlayer(player.getUUID());
            RtsPluginService.syncRelatedPlayers(player);
            RtsEffectAccumulator.INSTANCE.clearPlayer(player.getUUID());
            ServerHistoryManager.clear(player.getUUID());
            SaveScheduler.INSTANCE.onPlayerLogout(player);
        }

        @SubscribeEvent
        static void onPlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                RtsCameraManager.stopIfActive(player);
                ServiceRegistry.getInstance().pathfinding().cancel(player);
                RtsStorageTickService.INSTANCE.unregisterPlayer(player);
                RtsEndpointLeaseCache.INSTANCE.invalidatePlayer(player.getUUID());
                RtsEffectAccumulator.INSTANCE.clearDimension(player.getUUID(), event.getFrom());
            }
        }

        @SubscribeEvent
        static void onChunkLoad(final ChunkEvent.Load event) {
            if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
                RtsTaskEngine.INSTANCE.resumeLoadedChunk(level, event.getChunk().getPos());
            }
        }

        @SubscribeEvent
        static void onPlayerTick(final TickEvent.PlayerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
                ServerTickOrchestrator.getInstance().onPlayerTickPost(player);
                RtsDamageFeedbackManager.tick(player);
            }
        }

        @SubscribeEvent
        static void onRegisterCommands(final RegisterCommandsEvent event) {
            RtsBenchmarkCommand.register(event.getDispatcher());
            RtsGuiCompatSetupCommand.register(event.getDispatcher());
        }

        @SubscribeEvent
        static void onServerTick(final TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            ServerTickOrchestrator.getInstance().tickMining(event.getServer());
            SaveScheduler.INSTANCE.onTick(event.getServer());
            TaskPersistenceRuntime.INSTANCE.tick();
        }
    }
}
