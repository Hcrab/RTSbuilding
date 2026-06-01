package com.rtsbuilding.rtsbuilding;

import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.blueprint.server.BlueprintPlacementService;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.server.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.RtsDamageFeedbackManager;
import com.rtsbuilding.rtsbuilding.server.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(RtsbuildingMod.MODID)
public class RtsbuildingMod {
    public static final String MODID = "rtsbuilding";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rtsbuilding"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> output.accept(EXAMPLE_ITEM.get()))
            .build());

    public static final DeferredHolder<EntityType<?>, EntityType<RtsCameraEntity>> RTS_CAMERA_ENTITY = ENTITY_TYPES.register(
            "rts_camera",
            () -> EntityType.Builder.<RtsCameraEntity>of(RtsCameraEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(128)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .build(ResourceLocation.fromNamespaceAndPath(MODID, "rts_camera").toString()));

    public RtsbuildingMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::addCreative);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.rtsbuilding.rtsbuilding.client.RtsClientBootstrap.registerConfigUi(modContainer);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());
        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    @EventBusSubscriber(modid = RtsbuildingMod.MODID, bus = EventBusSubscriber.Bus.GAME)
    static class GameEvents {
        @SubscribeEvent
        static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                RtsCameraManager.cleanupOrphanCameras(serverPlayer.getServer());
                RtsDamageFeedbackManager.remember(serverPlayer);
                RtsProgressionManager.onPlayerLogin(serverPlayer);
            }
        }

        @SubscribeEvent
        static void onServerStarted(ServerStartedEvent event) {
            RtsStorageManager.warmCreativeTabCaches(event.getServer());
            RtsCameraManager.cleanupOrphanCameras(event.getServer());
        }

        @SubscribeEvent
        static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                RtsCameraManager.stopIfActive(serverPlayer);
                BlueprintPlacementService.clear(serverPlayer);
                RtsDamageFeedbackManager.forget(serverPlayer);
                RtsStorageManager.onPlayerLogout(serverPlayer);
                RtsProgressionManager.onPlayerLogout(serverPlayer);
            }
        }

        @SubscribeEvent
        static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                RtsCameraManager.stopIfActive(serverPlayer);
            }
        }

        @SubscribeEvent
        static void onPlayerTickPre(PlayerTickEvent.Pre event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                RtsStorageManager.onPlayerTickPre(serverPlayer);
            }
        }

        @SubscribeEvent
        static void onPlayerTickPost(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                RtsStorageManager.onPlayerTickPost(serverPlayer);
                RtsDamageFeedbackManager.tick(serverPlayer);
                BlueprintPlacementService.tick(serverPlayer);
            }
        }

        @SubscribeEvent
        static void onServerTick(ServerTickEvent.Post event) {
            RtsStorageManager.tickMining(event.getServer());
        }
    }
}
