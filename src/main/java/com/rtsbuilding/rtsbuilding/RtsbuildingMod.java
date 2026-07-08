package com.rtsbuilding.rtsbuilding;


import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.blueprint.server.BlueprintPlacementService;
import com.rtsbuilding.rtsbuilding.common.RtsCreativeTabs;
import com.rtsbuilding.rtsbuilding.common.RtsItems;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.network.RtsForgePayloadRegistrar;
import com.rtsbuilding.rtsbuilding.server.RtsAPIImpl;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.feedback.RtsDamageFeedbackManager;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.RtsPipelineRegistration;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import com.rtsbuilding.rtsbuilding.server.service.RtsBenchmarkCommand;
import com.rtsbuilding.rtsbuilding.server.service.RtsGuiCompatSetupCommand;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

@Mod(RtsbuildingMod.MODID)
public final class RtsbuildingMod {
    public static final String MODID = "rtsbuilding";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final RegistryObject<EntityType<RtsCameraEntity>> RTS_CAMERA_ENTITY = ENTITY_TYPES.register(
            "rts_camera",
            () -> EntityType.Builder.<RtsCameraEntity>of(RtsCameraEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .build("rts_camera"));

    public RtsbuildingMod(final FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);

        ENTITY_TYPES.register(modEventBus);
        RtsItems.register(modEventBus);
        RtsCreativeTabs.register(modEventBus);
        RtsForgePayloadRegistrar.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.rtsbuilding.rtsbuilding.client.bootstrap.RtsClientBootstrap.registerConfigUi(ModLoadingContext.get()));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        RtsAPIImpl.init();
        RtsPipelineRegistration.registerAll();
        LOGGER.info("RTSBuilding common setup complete");
    }

    @SubscribeEvent
    public void onServerStarting(final ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    static final class GameEvents {
        private GameEvents() {
        }

        @SubscribeEvent
        static void onPlayerLogin(final PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                RtsCameraManager.cleanupOrphanCameras(serverPlayer.getServer());
                RtsDamageFeedbackManager.remember(serverPlayer);
                RtsProgressionManager.onPlayerLogin(serverPlayer);
                RtsPluginService.syncRelatedPlayers(serverPlayer);
                RtsWorkflowEngine.getInstance().loadPlayerFromStore(serverPlayer.getServer(), serverPlayer);
            }
        }

        @SubscribeEvent
        static void onServerStarted(final ServerStartedEvent event) {
            RtsSessionService.warmCreativeTabCaches(event.getServer());
            RtsCameraManager.cleanupOrphanCameras(event.getServer());
        }

        @SubscribeEvent
        static void onServerStopped(final ServerStoppedEvent event) {
            TickablePipelineRegistry.clearAll();
            RtsWorkflowEngine.getInstance().saveAll(event.getServer());
            RtsWorkflowEngine.getInstance().clearAllData();
        }

        @SubscribeEvent
        static void onPlayerLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                RtsCameraManager.stopIfActive(serverPlayer);
                BlueprintPlacementService.clear(serverPlayer);
                TickablePipelineRegistry.removeAll(serverPlayer.getUUID());
                RtsDamageFeedbackManager.forget(serverPlayer);
                // 会话清理会丢弃运行期队列，先把玩家可见的任务状态压成暂停并写盘。
                RtsWorkflowEngine.getInstance().pauseAllActive(serverPlayer.getUUID(), false);
                RtsWorkflowEngine.getInstance().saveAll(serverPlayer.getServer());
                RtsWorkflowEngine.getInstance().forgetPlayerReference(serverPlayer.getUUID());
                RtsPendingPlacementService.clearPlayerScanCache(serverPlayer.getUUID());
                RtsSessionService.onPlayerLogout(serverPlayer);
                RtsProgressionManager.onPlayerLogout(serverPlayer);
                ServerHistoryManager.clear(serverPlayer.getUUID());
            }
        }

        @SubscribeEvent
        static void onPlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                RtsCameraManager.stopIfActive(serverPlayer);
                TickablePipelineRegistry.removeAll(serverPlayer.getUUID(), event.getFrom());
                RtsStorageTickService.INSTANCE.unregisterPlayer(serverPlayer);
            }
        }

        @SubscribeEvent
        static void onPlayerTick(final TickEvent.PlayerTickEvent event) {
            if (!(event.player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
                return;
            }
            if (event.phase == TickEvent.Phase.START) {
                RtsSessionService.onPlayerTickPre(serverPlayer);
            } else {
                RtsSessionService.onPlayerTickPost(serverPlayer);
                RtsDamageFeedbackManager.tick(serverPlayer);
                BlueprintPlacementService.tick(serverPlayer);
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
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                RtsSessionService.tickMining(server);
                TickablePipelineRegistry.tickAll();
            }
        }
    }
}
