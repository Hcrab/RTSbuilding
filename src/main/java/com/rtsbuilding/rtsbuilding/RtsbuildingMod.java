package com.rtsbuilding.rtsbuilding;


import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.blueprint.server.BlueprintPlacementService;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.network.RtsForgePayloadRegistrar;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsAPIImpl;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.feedback.RtsDamageFeedbackManager;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginItem;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
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
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<EntityType<RtsCameraEntity>> RTS_CAMERA_ENTITY = ENTITY_TYPES.register(
            "rts_camera",
            () -> EntityType.Builder.<RtsCameraEntity>of(RtsCameraEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .build("rts_camera"));

    public static final RegistryObject<Item> RTS_CONTROL_CORE = pluginItem("rts_control_core");
    public static final RegistryObject<Item> REMOTE_CONTROL_PLUGIN = pluginItem("remote_control_plugin");
    public static final RegistryObject<Item> STORAGE_INTEGRATION_PLUGIN = pluginItem("storage_integration_plugin");
    public static final RegistryObject<Item> CRAFT_TERMINAL_PLUGIN = pluginItem("craft_terminal_plugin");
    public static final RegistryObject<Item> CHAIN_BREAK_PLUGIN = pluginItem("chain_break_plugin");
    public static final RegistryObject<Item> AREA_DESTROY_PLUGIN = pluginItem("area_destroy_plugin");
    public static final RegistryObject<Item> BLUEPRINT_PLUGIN = pluginItem("blueprint_plugin");
    public static final RegistryObject<Item> FIELD_DEPLOYMENT_PLUGIN = pluginItem("field_deployment_plugin");
    public static final RegistryObject<Item> RANGE_EXTENSION_I = pluginItem("range_extension_i");
    public static final RegistryObject<Item> RANGE_EXTENSION_II = pluginItem("range_extension_ii");
    public static final RegistryObject<Item> RANGE_EXTENSION_III = pluginItem("range_extension_iii");
    public static final RegistryObject<Item> RANGE_EXTENSION_MAX = pluginItem("range_extension_max");

    public static final RegistryObject<CreativeModeTab> RTSBUILDING_TAB = CREATIVE_TABS.register(
            "rtsbuilding",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.rtsbuilding"))
                    .icon(() -> new ItemStack(RTS_CONTROL_CORE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(RTS_CONTROL_CORE.get());
                        output.accept(REMOTE_CONTROL_PLUGIN.get());
                        output.accept(STORAGE_INTEGRATION_PLUGIN.get());
                        output.accept(CRAFT_TERMINAL_PLUGIN.get());
                        output.accept(CHAIN_BREAK_PLUGIN.get());
                        output.accept(AREA_DESTROY_PLUGIN.get());
                        output.accept(BLUEPRINT_PLUGIN.get());
                        output.accept(FIELD_DEPLOYMENT_PLUGIN.get());
                        output.accept(RANGE_EXTENSION_I.get());
                        output.accept(RANGE_EXTENSION_II.get());
                        output.accept(RANGE_EXTENSION_III.get());
                        output.accept(RANGE_EXTENSION_MAX.get());
                    })
                    .build());

    public RtsbuildingMod(final FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);

        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        RtsForgePayloadRegistrar.register();
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.rtsbuilding.rtsbuilding.client.bootstrap.RtsClientBootstrap.registerConfigUi(ModLoadingContext.get()));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        RtsAPIImpl.init();
        LOGGER.info("RTSBuilding common setup complete");
    }

    private static RegistryObject<Item> pluginItem(String id) {
        return ITEMS.register(id, () -> new RtsPluginItem(new Item.Properties().stacksTo(64)));
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
            }
        }

        @SubscribeEvent
        static void onServerStarted(final ServerStartedEvent event) {
            RtsSessionService.warmCreativeTabCaches(event.getServer());
            RtsCameraManager.cleanupOrphanCameras(event.getServer());
        }

        @SubscribeEvent
        static void onPlayerLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                RtsCameraManager.stopIfActive(serverPlayer);
                BlueprintPlacementService.clear(serverPlayer);
                RtsDamageFeedbackManager.forget(serverPlayer);
                RtsSessionService.onPlayerLogout(serverPlayer);
                RtsProgressionManager.onPlayerLogout(serverPlayer);
                RtsPluginService.syncRelatedPlayers(serverPlayer);
                ServerHistoryManager.clear(serverPlayer.getUUID());
            }
        }

        @SubscribeEvent
        static void onPlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                RtsCameraManager.stopIfActive(serverPlayer);
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
        static void onServerTick(final TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                RtsSessionService.tickMining(server);
            }
        }
    }
}
