package com.rtsbuilding.rtsbuilding;

import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.common.RtsBlocks;
import com.rtsbuilding.rtsbuilding.common.RtsCreativeTabs;
import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import com.rtsbuilding.rtsbuilding.common.RtsItems;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
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
import com.rtsbuilding.rtsbuilding.server.tracking.RtsBlockTrackingEvents;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import java.nio.file.Path;
import java.time.Duration;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

/**
 * RTSBuilding 的公共初始化与服务端生命周期所有者。
 *
 * <p>它不引用客户端类，也不再承担加载器注解扫描。Fabric 入口只调用 {@link #initialize()}；
 * 本类随后按明确顺序注册原版条目、网络、服务和生命周期回调。业务行为保持与 913d930 +
 * 5a7ca132 基线一致，加载器差异只停留在此边界。
 */
public final class RtsbuildingMod {
    public static final String MODID = "rtsbuilding";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized;

    private RtsbuildingMod() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        loadCommonConfig();
        RtsBlocks.register();
        RtsItems.register();
        RtsEntities.register();
        RtsCreativeTabs.register();
        RtsPayloadRegistrar.registerCommon();

        ServiceRegistry.init();
        RtsAPIImpl.init();
        RtsPipelineRegistration.registerAll();
        RtsOperationDiagnostics.install();
        RtsBlockTrackingEvents.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            RtsDeveloperScenarioCommand.register(dispatcher);
            RtsGuiCompatSetupCommand.registerIfEnabled(dispatcher);
        });
        registerServerEvents();
        LOGGER.info("RTSBuilding Fabric 公共初始化完成");
    }

    private static void loadCommonConfig() {
        Path directory = FabricLoader.getInstance().getConfigDir().resolve("rts_building");
        Config.SPEC.load(directory.resolve("rtsbuilding-common.json"));
        Config.SERVER_SPEC.load(directory.resolve("rtsbuilding-server.json"));
        if (Config.migrateLegacyServerDefaults()) {
            LOGGER.info("已迁移 RTSBuilding 旧版服务端默认值");
        }
    }

    private static void registerServerEvents() {
        ServerLifecycleEvents.SERVER_STARTING.register(RtsbuildingMod::onServerStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(RtsbuildingMod::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(RtsbuildingMod::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(RtsbuildingMod::onServerStopped);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerLogin(handler.player));
        // Fabric 的断开回调可能来自 Netty IO 线程；durable task 冲刷必须回到服务器主线程。
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> server.execute(() -> onPlayerLogout(handler.player)));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                RtsbuildingMod::onPlayerChangedDimension);
        ServerChunkEvents.CHUNK_LOAD.register(RtsbuildingMod::onChunkLoad);
        ServerTickEvents.END_SERVER_TICK.register(RtsbuildingMod::onServerTick);
    }

    private static void onServerStarting(MinecraftServer server) {
        try {
            // 必须先于任何 durable task admission 读取；损坏时拒绝以空仓继续启动。
            TaskPersistenceRuntime.INSTANCE.start(server);
        } catch (RuntimeException failure) {
            LOGGER.error("读取 durable task 仓库失败，服务端将 fail-closed 停止启动", failure);
            throw failure;
        }
        LOGGER.info("RTSBuilding 服务端正在启动");
    }

    private static void onPlayerLogin(ServerPlayer player) {
        RtsCameraManager.cleanupOrphanCameras(player.getServer());
        RtsDamageFeedbackManager.remember(player);
        RtsProgressionManager.onPlayerLogin(player);
        RtsPluginService.syncRelatedPlayers(player);
        RtsWorkflowEngine.getInstance().loadPlayerFromStore(player.getServer(), player);
        RtsWorkflowEngine.getInstance().refreshPlayerIdleClocks(player);
    }

    private static void onServerStarted(MinecraftServer server) {
        RtsEffectAccumulator.INSTANCE.resetForServerStart();
        RtsCameraManager.cleanupOrphanCameras(server);
        SaveScheduler.INSTANCE.cleanupLegacyFiles(server);
        RtsWorkflowEngine.getInstance().startTimeoutService(
                server, Duration.ofSeconds(1), Duration.ofSeconds(30));
    }

    private static void onServerStopping(MinecraftServer server) {
        try {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                RtsTaskEngine.INSTANCE.preparePlayerDetach(player);
            }
            RtsTaskEngine.INSTANCE.checkpointAllDurableExecutions(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                TaskPersistenceRuntime.INSTANCE.flushOwner(player.getUUID());
                RtsTaskEngine.INSTANCE.reconcilePlayerDetach(player);
            }
        } catch (RuntimeException failure) {
            LOGGER.error("停服时 durable task 冻结失败；未确认的 dirty 不会被伪装成已落盘", failure);
            throw failure;
        }
    }

    private static void onServerStopped(MinecraftServer server) {
        RuntimeException durableFailure = null;
        RtsWorkflowEngine.getInstance().stopTimeoutService();
        try {
            if (TaskPersistenceRuntime.INSTANCE.isStarted()) {
                TaskPersistenceRuntime.INSTANCE.stop();
            }
            RtsTaskEngine.INSTANCE.resetDurableRuntimeAfterServerStop();
        } catch (RuntimeException failure) {
            durableFailure = failure;
            LOGGER.error("服务端停止后关闭 durable task writer 失败；保留故障状态", failure);
        }
        RtsWorkflowEngine.getInstance().saveAll(server);
        SaveScheduler.INSTANCE.onServerStopped();
        RtsWorkflowEngine.getInstance().clearAllData();
        RtsStoragePageRequestCoalescer.clearAll();
        RtsEffectAccumulator.INSTANCE.clearAll();
        RtsDeveloperMetrics.clearAll();
        if (durableFailure != null) {
            throw durableFailure;
        }
    }

    private static void onPlayerLogout(ServerPlayer player) {
        try {
            RtsTaskEngine.INSTANCE.preparePlayerDetach(player);
            RtsTaskEngine.INSTANCE.detachPlayer(player.getUUID());
            TaskPersistenceRuntime.INSTANCE.flushOwner(player.getUUID());
            RtsTaskEngine.INSTANCE.reconcilePlayerDetach(player);
        } catch (RuntimeException failure) {
            LOGGER.error("玩家 {} 登出时 durable task 冲刷失败，已保留 dirty 并继续清理",
                    player.getUUID(), failure);
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

    private static void onPlayerChangedDimension(
            ServerPlayer player, ServerLevel origin, ServerLevel destination) {
        RtsCameraManager.stopIfActive(player);
        ServiceRegistry.getInstance().pathfinding().cancel(player);
        RtsStorageTickService.INSTANCE.unregisterPlayer(player);
        RtsEndpointLeaseCache.INSTANCE.invalidatePlayer(player.getUUID());
        RtsEffectAccumulator.INSTANCE.clearDimension(player.getUUID(), origin.dimension());
    }

    private static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        RtsTaskEngine.INSTANCE.resumeLoadedChunk(level, chunk.getPos());
    }

    private static void onServerTick(MinecraftServer server) {
        // Fabric 1.21.1 没有独立玩家 tick 事件；在同一 END_SERVER_TICK 中逐玩家保持原顺序。
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerTickOrchestrator.getInstance().onPlayerTickPost(player);
            RtsDamageFeedbackManager.tick(player);
        }
        ServerTickOrchestrator.getInstance().tickMining(server);
        SaveScheduler.INSTANCE.onTick(server);
        TaskPersistenceRuntime.INSTANCE.tick();
    }
}
