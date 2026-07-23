package com.rtsbuilding.rtsbuilding.gametest;

import com.mojang.authlib.GameProfile;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.common.RtsItems;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsAPIImpl;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.data.DataCluster;
import com.rtsbuilding.rtsbuilding.server.data.PlayerComponents;
import com.rtsbuilding.rtsbuilding.server.data.RtsAtomicNbtStore;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.RtsPipelineRegistration;
import com.rtsbuilding.rtsbuilding.server.plugin.BuiltInRtsPluginCatalog;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacedRecoveryService;
import com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.page.PageResult;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryClaim;
import com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryJob;
import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskCodec;
import com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(RtsbuildingMod.MODID)
@PrefixGameTestTemplate(false)
public final class RtsServerGameTests {
    private static final String EMPTY_TEMPLATE = "gametest/empty";
    private static final AtomicInteger PLAYER_SEQUENCE = new AtomicInteger();
    private static final List<Item> JUNK_ITEMS = List.of(
            Items.STONE,
            Items.DIAMOND,
            Items.EMERALD,
            Items.GRANITE,
            Items.DIORITE,
            Items.ANDESITE,
            Items.COBBLESTONE,
            Items.MOSSY_COBBLESTONE,
            Items.DIRT,
            Items.COARSE_DIRT,
            Items.ROOTED_DIRT,
            Items.SAND,
            Items.RED_SAND,
            Items.GRAVEL,
            Items.CLAY_BALL,
            Items.OAK_LOG,
            Items.SPRUCE_LOG,
            Items.BIRCH_LOG,
            Items.JUNGLE_LOG,
            Items.ACACIA_LOG,
            Items.DARK_OAK_LOG,
            Items.MANGROVE_LOG,
            Items.OAK_PLANKS,
            Items.SPRUCE_PLANKS,
            Items.BIRCH_PLANKS,
            Items.JUNGLE_PLANKS,
            Items.ACACIA_PLANKS,
            Items.DARK_OAK_PLANKS,
            Items.MANGROVE_PLANKS,
            Items.STICK,
            Items.COAL,
            Items.CHARCOAL,
            Items.IRON_INGOT,
            Items.GOLD_INGOT,
            Items.COPPER_INGOT,
            Items.LAPIS_LAZULI,
            Items.REDSTONE,
            Items.QUARTZ,
            Items.FLINT,
            Items.STRING,
            Items.FEATHER,
            Items.LEATHER,
            Items.PAPER,
            Items.BONE,
            Items.GUNPOWDER,
            Items.BLAZE_POWDER,
            Items.AMETHYST_SHARD,
            Items.PRISMARINE_SHARD,
            Items.PRISMARINE_CRYSTALS,
            Items.SLIME_BALL,
            Items.BRICK,
            Items.NETHER_BRICK,
            Items.WHEAT_SEEDS,
            Items.BEETROOT_SEEDS,
            Items.MELON_SEEDS,
            Items.PUMPKIN_SEEDS,
            Items.SUGAR,
            Items.GLOWSTONE_DUST,
            Items.NETHER_WART);

    private RtsServerGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void installedPluginIsDurableBeforeAutomaticSaveTick(GameTestHelper helper) {
        ServerPlayer player = startRtsPlayer(helper, GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(RtsItems.HARVEST_TIER_STONE.get()));

        helper.assertTrue(RtsPluginService.installFromInventorySlot(player, 0),
                "Stone harvest-tier plugin should install from the player inventory");
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                "Installed plugin item should be removed from the inventory");

        // 不等待 200 tick 自动刷盘：立即建立一个全新的 DataCluster，从真实文件重新读取。
        DataCluster reloaded = new DataCluster(new RtsAtomicNbtStore(
                helper.getLevel().getServer(),
                "rtsbuilding/players/" + player.getUUID(),
                "session.dat"));
        CompoundTag pluginRoot = reloaded.get(PlayerComponents.PLUGINS);
        ListTag installed = pluginRoot.getList("installed", Tag.TAG_COMPOUND);
        boolean persisted = false;
        for (int index = 0; index < installed.size(); index++) {
            if (BuiltInRtsPluginCatalog.HARVEST_TIER_STONE.toString()
                    .equals(installed.getCompound(index).getString("plugin_id"))) {
                persisted = true;
                break;
            }
        }
        helper.assertTrue(persisted,
                "Installed plugin must already exist on disk before the scheduled 10-second flush");
        stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80, batch = "survival_progression")
    public static void remoteControlReinstallRestoresMiningFeaturesWithoutBecomingTool(GameTestHelper helper) {
        Config.setSurvivalProgressionEnabled(false);
        ServerPlayer player = startRtsPlayer(helper, GameType.SURVIVAL);
        try {
            player.getInventory().setItem(0, new ItemStack(RtsItems.RTS_CONTROL_CORE.get()));
            player.getInventory().setItem(1, new ItemStack(RtsItems.AREA_DESTROY_PLUGIN.get()));
            player.getInventory().setItem(2, new ItemStack(RtsItems.CHAIN_BREAK_PLUGIN.get()));
            player.getInventory().setItem(3, new ItemStack(RtsItems.REMOTE_CONTROL_PLUGIN.get()));

            for (int slot = 0; slot < 4; slot++) {
                helper.assertTrue(RtsPluginService.installFromInventorySlot(player, slot),
                        "Initial plugin set should install");
            }
            Config.setSurvivalProgressionEnabled(true);
            helper.assertTrue(RtsPluginService.canUse(player, RtsFeature.REMOTE_PLACE),
                    "Remote placement should be unlocked while survival progression is enabled");
            helper.assertTrue(RtsPluginService.canUse(player, RtsFeature.REMOTE_BREAK),
                    "Remote break should be unlocked while survival progression is enabled");
            helper.assertTrue(RtsPluginService.uninstall(
                            player, BuiltInRtsPluginCatalog.REMOTE_CONTROL_PLUGIN),
                    "Remote-control plugin should uninstall");

            int returnedSlot = -1;
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(RtsItems.REMOTE_CONTROL_PLUGIN.get())) {
                    returnedSlot = slot;
                    break;
                }
            }
            helper.assertTrue(returnedSlot >= 0, "Uninstall should return the plugin item");
            helper.assertTrue(RtsPluginService.installFromInventorySlot(player, returnedSlot),
                    "Returned remote-control plugin should reinstall");

            helper.assertTrue(RtsPluginService.canUse(player, RtsFeature.REMOTE_BREAK),
                    "Remote break should be unlocked immediately after reinstall");
            helper.assertTrue(RtsPluginService.canUse(player, RtsFeature.AREA_DESTROY),
                    "Area destroy should stay unlocked after dependency reinstall");
            helper.assertTrue(RtsPluginService.canUse(player, RtsFeature.ULTIMINE),
                    "Chain break should stay unlocked after dependency reinstall");
            helper.assertTrue(!RtsMiningValidator.isSelectedMiningToolRequested(
                            BuiltInRtsPluginCatalog.REMOTE_CONTROL_PLUGIN.toString(),
                            new ItemStack(RtsItems.REMOTE_CONTROL_PLUGIN.get())),
                    "RTS plugin items must never be interpreted as selected mining tools");

            helper.succeed();
        } finally {
            // GameTest 使用独立配置目录；必须恢复为关闭，避免污染随后并行运行的普通测试批次。
            Config.setSurvivalProgressionEnabled(false);
            stopPlayers(player);
        }
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80, batch = "survival_progression")
    public static void areaDestroyStoneWithoutHarvestTierShowsWarning(GameTestHelper helper) {
        Config.setSurvivalProgressionEnabled(false);
        BlockPos stoneRel = new BlockPos(4, 1, 4);
        helper.setBlock(stoneRel, Blocks.STONE);
        ServerPlayer player = startRtsPlayer(helper, GameType.SURVIVAL);
        try {
            player.getInventory().setItem(0, new ItemStack(RtsItems.RTS_CONTROL_CORE.get()));
            player.getInventory().setItem(1, new ItemStack(RtsItems.REMOTE_CONTROL_PLUGIN.get()));
            player.getInventory().setItem(2, new ItemStack(RtsItems.AREA_DESTROY_PLUGIN.get()));
            for (int slot = 0; slot < 3; slot++) {
                helper.assertTrue(RtsPluginService.installFromInventorySlot(player, slot),
                        "Required non-harvest plugins should install");
            }

            ItemStack diamondPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            player.getInventory().setItem(0, diamondPickaxe.copy());
            player.getInventory().selected = 0;
            Config.setSurvivalProgressionEnabled(true);
            RtsProgressionManager.beginHomeSelection(player);
            helper.assertTrue(RtsProgressionManager.commitHome(player, helper.absolutePos(stoneRel)),
                    "GameTest player should be able to set RTS home near the target");

            RtsAPI.get().mining().areaDestroy(
                    player,
                    asApiPositions(helper, List.of(stoneRel)),
                    (byte) 0,
                    BuiltInRegistries.ITEM.getKey(Items.DIAMOND_PICKAXE).toString(),
                    diamondPickaxe,
                    false);

            helper.assertBlockPresent(Blocks.STONE, stoneRel);
            helper.assertTrue(!hasActiveTask(player, TaskType.DESTRUCTION),
                    "Harvest-tier-blocked range destroy should not start a destruction task");
            helper.assertTrue(!hasActiveTask(player, TaskType.MINING),
                    "Harvest-tier-blocked range destroy should not start a mining task");
            helper.succeed();
        } finally {
            Config.setSurvivalProgressionEnabled(false);
            stopPlayers(player);
        }
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160, batch = "survival_progression")
    public static void areaDestroySnowWithoutHarvestTierStillWorks(GameTestHelper helper) {
        Config.setSurvivalProgressionEnabled(false);
        BlockPos snowBlockRel = new BlockPos(4, 1, 4);
        BlockPos snowLayerSupportRel = new BlockPos(5, 1, 4);
        BlockPos snowLayerRel = snowLayerSupportRel.above();
        helper.setBlock(snowBlockRel, Blocks.SNOW_BLOCK);
        helper.setBlock(snowLayerSupportRel, Blocks.DIRT);
        helper.setBlock(snowLayerRel, Blocks.SNOW);
        ServerPlayer player = startRtsPlayer(helper, GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(RtsItems.RTS_CONTROL_CORE.get()));
        player.getInventory().setItem(1, new ItemStack(RtsItems.REMOTE_CONTROL_PLUGIN.get()));
        player.getInventory().setItem(2, new ItemStack(RtsItems.AREA_DESTROY_PLUGIN.get()));
        for (int slot = 0; slot < 3; slot++) {
            helper.assertTrue(RtsPluginService.installFromInventorySlot(player, slot),
                    "Required non-harvest plugins should install");
        }

        ItemStack diamondPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        player.getInventory().setItem(0, diamondPickaxe.copy());
        player.getInventory().selected = 0;
        Config.setSurvivalProgressionEnabled(true);
        RtsProgressionManager.beginHomeSelection(player);
        helper.assertTrue(RtsProgressionManager.commitHome(player, helper.absolutePos(snowBlockRel)),
                "GameTest player should be able to set RTS home near the snow targets");

        RtsAPI.get().mining().areaDestroy(
                player,
                asApiPositions(helper, List.of(snowBlockRel, snowLayerRel)),
                (byte) 0,
                BuiltInRegistries.ITEM.getKey(Items.DIAMOND_PICKAXE).toString(),
                diamondPickaxe,
                false);

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, snowBlockRel);
            helper.assertBlockPresent(Blocks.AIR, snowLayerRel);
            helper.assertTrue(!hasActiveTask(player, TaskType.MINING),
                    "Snow range destroy should finish without an active mining task");
            helper.assertTrue(!hasActiveTask(player, TaskType.DESTRUCTION),
                    "Snow range destroy should finish without an active destruction task");
            Config.setSurvivalProgressionEnabled(false);
            stopPlayers(player);
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void rtsEmptyHandRightClickOpensChest(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(3, 1, 3);
        helper.setBlock(chestRel, Blocks.CHEST);
        ServerPlayer player = startRtsPlayer(helper, GameType.SURVIVAL);

        BlockPos chestAbs = helper.absolutePos(chestRel);
        Vec3 hit = Vec3.atCenterOf(chestAbs);
        Vec3 rayOrigin = player.getEyePosition();
        Vec3 rayDir = hit.subtract(rayOrigin).normalize();

        RtsAPI.get().interaction().interactTarget(
                player,
                C2SRtsInteractPayload.NO_ENTITY,
                chestAbs,
                Direction.UP,
                hit.x,
                hit.y,
                hit.z,
                C2SRtsInteractPayload.SOURCE_EMPTY_HAND,
                (byte) 0,
                "",
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z);

        helper.assertTrue(player.containerMenu instanceof ChestMenu,
                "RTS empty-hand right click should open the chest menu");
        stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void linkedStorageCountsChestContents(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(3, 1, 3);
        helper.setBlock(chestRel, Blocks.CHEST);
        setChestStack(helper, chestRel, 0, new ItemStack(Items.STONE, 19));
        ServerPlayer player = startRtsPlayer(helper, GameType.SURVIVAL);

        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);

        RtsStorageSession session = requireSession(helper, player);
        helper.assertValueEqual(1, session.linkedStorageInfo.size(),
                "RTS should keep one linked storage after linking a chest");
        long stoneCount = RtsAPI.get().storage().countItemsMatching(player, stack -> stack.getItem() == Items.STONE);
        helper.assertValueEqual(19L, stoneCount,
                "RTS linked storage should count items in the linked chest");
        stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void storeHotbarSlotMovesItemsIntoLinkedChest(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(3, 1, 3);
        helper.setBlock(chestRel, Blocks.CHEST);
        ServerPlayer player = startRtsPlayer(helper, GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.DIRT, 12));

        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        RtsAPI.get().bindings().storeHotbarSlot(player, (byte) 0);

        helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                "Storing a hotbar slot should clear the player's original slot");
        helper.assertValueEqual(12, countChestItem(helper, chestRel, Items.DIRT),
                "Storing a hotbar slot should move the items into the linked chest");
        stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void placeBatchBuildsBlocksInWorld(GameTestHelper helper) {
        List<BlockPos> supportRel = linePositions(2, 1, 2, 3);
        for (BlockPos pos : supportRel) {
            helper.setBlock(pos, Blocks.DIRT);
        }
        ServerPlayer player = startRtsPlayer(helper, GameType.CREATIVE);
        player.getInventory().setItem(0, new ItemStack(Items.STONE, supportRel.size()));

        enqueuePlacementThroughApi(helper, player, supportRel, "minecraft:stone", new ItemStack(Items.STONE));
        helper.assertTrue(hasActiveTask(player, TaskType.PLACEMENT),
                "New placement commands should enter TaskStore immediately");

        helper.succeedWhen(() -> {
            for (BlockPos support : supportRel) {
                helper.assertBlockPresent(Blocks.STONE, support.above());
            }
            stopPlayers(player);
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void fiveRtsPlayersKeepIndependentSessions(GameTestHelper helper) {
        List<ServerPlayer> players = startRtsPlayers(helper, 5, GameType.CREATIVE);

        for (ServerPlayer player : players) {
            RtsStorageSession session = requireSession(helper, player);
            helper.assertTrue(RtsCameraManager.isActive(player),
                    "Every GameTest player should independently enter RTS mode");
            helper.assertTrue(session.linkedStorageInfo.isEmpty(),
                    "A fresh RTS session should not inherit another player's linked storage");
        }

        stopPlayers(players);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void fivePlayersPlaceBatchesWithoutCrossTalk(GameTestHelper helper) {
        List<ServerPlayer> players = startRtsPlayers(helper, 5, GameType.CREATIVE);
        List<List<BlockPos>> supportGroupsRel = new ArrayList<>();

        for (int i = 0; i < players.size(); i++) {
            List<BlockPos> supportsRel = linePositions(1, 1, 1 + i, 3);
            supportGroupsRel.add(supportsRel);
            for (BlockPos supportRel : supportsRel) {
                helper.setBlock(supportRel, Blocks.DIRT);
            }
            players.get(i).getInventory().setItem(0, new ItemStack(Items.STONE, supportsRel.size()));
            enqueuePlacementThroughApi(helper, players.get(i), supportsRel, "minecraft:stone", new ItemStack(Items.STONE));
        }

        helper.succeedWhen(() -> {
            for (List<BlockPos> supportsRel : supportGroupsRel) {
                for (BlockPos supportRel : supportsRel) {
                    helper.assertBlockPresent(Blocks.STONE, supportRel.above());
                }
            }
            for (ServerPlayer player : players) {
                helper.assertTrue(!hasActiveTask(player, TaskType.PLACEMENT),
                        "Completed placement should not leave an active durable task");
            }
            stopPlayers(players);
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void fivePlayersAreaDestroyWithoutCrossTalk(GameTestHelper helper) {
        List<ServerPlayer> players = startRtsPlayers(helper, 5, GameType.CREATIVE);
        List<List<BlockPos>> targetGroupsRel = new ArrayList<>();

        for (int i = 0; i < players.size(); i++) {
            List<BlockPos> targetsRel = linePositions(1, 1, 1 + i, 3);
            targetGroupsRel.add(targetsRel);
            for (BlockPos targetRel : targetsRel) {
                helper.setBlock(targetRel, Blocks.DIRT);
            }
            RtsAPI.get().mining().areaDestroy(players.get(i), asApiPositions(helper, targetsRel),
                    (byte) 0, "", ItemStack.EMPTY, false);
        }

        helper.succeedWhen(() -> {
            for (List<BlockPos> targetsRel : targetGroupsRel) {
                for (BlockPos targetRel : targetsRel) {
                    helper.assertBlockPresent(Blocks.AIR, targetRel);
                }
            }
            for (ServerPlayer player : players) {
                helper.assertTrue(!hasActiveTask(player, TaskType.DESTRUCTION),
                        "Completed area destroy should not leave an active durable task");
            }
            stopPlayers(players);
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void areaDestroyAutoStoresDropsIntoLinkedChest(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        List<BlockPos> targetsRel = List.of(
                new BlockPos(3, 1, 3),
                new BlockPos(4, 1, 3),
                new BlockPos(5, 1, 3),
                new BlockPos(6, 1, 3));
        helper.setBlock(chestRel, Blocks.CHEST);
        for (BlockPos targetRel : targetsRel) {
            helper.setBlock(targetRel, Blocks.DIRT);
        }

        ServerPlayer player = startRtsPlayer(helper, GameType.SURVIVAL);
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        RtsAPI.get().bindings().setAutoStoreMinedDrops(player, true);
        RtsAPI.get().mining().areaDestroy(player, asApiPositions(helper, targetsRel),
                (byte) 0, "", ItemStack.EMPTY, false);

        helper.succeedWhen(() -> {
            for (BlockPos targetRel : targetsRel) {
                helper.assertBlockPresent(Blocks.AIR, targetRel);
            }
            helper.assertValueEqual(targetsRel.size(), countChestItem(helper, chestRel, Items.DIRT),
                    "Auto-store should put range-destroy drops into the linked chest");
            helper.assertTrue(!hasActiveTask(player, TaskType.DESTRUCTION),
                    "Auto-store area destroy should finish without an active durable task");
            stopPlayers(player);
        });
    }

    /**
     * 回归 #132：水下方块被远程破坏后，掉落必须在水流推动实体前进入自动入库缓存。
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void underwaterAreaDestroyAutoStoresDropsIntoLinkedChest(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        BlockPos targetRel = new BlockPos(4, 1, 4);
        helper.setBlock(chestRel, Blocks.CHEST);
        helper.setBlock(targetRel, Blocks.DIRT);
        helper.setBlock(targetRel.above(), Blocks.WATER);
        helper.setBlock(targetRel.above(2), Blocks.WATER);

        ServerPlayer player = startRtsPlayer(helper, GameType.SURVIVAL);
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        RtsAPI.get().bindings().setAutoStoreMinedDrops(player, true);
        RtsAPI.get().mining().areaDestroy(player, asApiPositions(helper, List.of(targetRel)),
                (byte) 0, "", ItemStack.EMPTY, false);

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.WATER, targetRel);
            helper.assertValueEqual(1, countChestItem(helper, chestRel, Items.DIRT),
                    "Underwater range-destroy drops should enter linked storage");
            helper.assertValueEqual(0, countPlayerItem(player, Items.DIRT),
                    "Underwater drops should not fall back to the player inventory");
            helper.assertValueEqual(0, countWorldItem(helper, List.of(targetRel), Items.DIRT),
                    "Underwater drops should not remain in or drift through the world");
            helper.assertTrue(!hasActiveTask(player, TaskType.DESTRUCTION),
                    "Underwater auto-store destruction should finish without an active task");
            stopPlayers(player);
        });
    }

    /**
     * 对照组：同一命中点不携带 R preset 时，必须保留原版楼梯朝向。
     * 这样下面的 preset 测试不是碰巧命中了本来就朝西的输入。
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void singlePlacementWithoutPresetKeepsVanillaStairFacing(GameTestHelper helper) {
        BlockPos supportRel = new BlockPos(2, 1, 2);
        helper.setBlock(supportRel, Blocks.DIRT);
        ServerPlayer player = startRtsPlayer(helper, GameType.CREATIVE);
        BlockPos supportAbs = helper.absolutePos(supportRel);
        Vec3 hitLocation = Vec3.atBottomCenterOf(supportAbs.above());
        Vec3 rayOrigin = player.getEyePosition();
        Vec3 rayDir = hitLocation.subtract(rayOrigin).normalize();

        ServiceRegistry.getInstance().placement().placeSelected(
                player,
                supportAbs,
                Direction.UP,
                hitLocation.x,
                hitLocation.y,
                hitLocation.z,
                (byte) 0,
                "",
                false,
                false,
                "minecraft:oak_stairs",
                new ItemStack(Items.OAK_STAIRS),
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z,
                false,
                false);

        helper.succeedWhen(() -> {
            BlockPos placedRel = supportRel.above();
            helper.assertBlockPresent(Blocks.OAK_STAIRS, placedRel);
            Direction facing = helper.getBlockState(placedRel)
                    .getValue(BlockStateProperties.HORIZONTAL_FACING);
            helper.assertTrue(facing != Direction.WEST,
                    "Control fixture must not naturally produce the requested west-facing stair");
            stopPlayers(player);
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void singlePlacementAppliesSelectedBlockStatePreset(GameTestHelper helper) {
        BlockPos supportRel = new BlockPos(2, 1, 2);
        helper.setBlock(supportRel, Blocks.DIRT);
        ServerPlayer player = startRtsPlayer(helper, GameType.CREATIVE);
        BlockPos supportAbs = helper.absolutePos(supportRel);
        Vec3 hitLocation = Vec3.atBottomCenterOf(supportAbs.above());
        Vec3 rayOrigin = player.getEyePosition();
        Vec3 rayDir = hitLocation.subtract(rayOrigin).normalize();

        ServiceRegistry.getInstance().placement().placeSelected(
                player,
                supportAbs,
                Direction.UP,
                hitLocation.x,
                hitLocation.y,
                hitLocation.z,
                (byte) 0,
                "facing=west;half=top",
                false,
                false,
                "minecraft:oak_stairs",
                new ItemStack(Items.OAK_STAIRS),
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z,
                false,
                false);

        helper.succeedWhen(() -> {
            BlockPos placedRel = supportRel.above();
            helper.assertBlockPresent(Blocks.OAK_STAIRS, placedRel);
            helper.assertBlockProperty(
                    placedRel,
                    BlockStateProperties.HORIZONTAL_FACING,
                    Direction.WEST);
            helper.assertBlockProperty(
                    placedRel,
                    BlockStateProperties.HALF,
                    net.minecraft.world.level.block.state.properties.Half.TOP);
            stopPlayers(player);
        });
    }

    /**
     * 对照组：点击支撑方块顶面且没有 R preset 时，原版结果确实是下半砖。
     * 若这个测试不成立，上半砖覆盖测试就没有证明它真正推翻了命中位置。
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void singlePlacementWithoutPresetUsesVanillaBottomSlab(GameTestHelper helper) {
        BlockPos supportRel = new BlockPos(2, 1, 2);
        helper.setBlock(supportRel, Blocks.DIRT);
        ServerPlayer player = startRtsPlayer(helper, GameType.CREATIVE);
        BlockPos supportAbs = helper.absolutePos(supportRel);
        Vec3 hitLocation = Vec3.atBottomCenterOf(supportAbs.above());
        Vec3 rayOrigin = player.getEyePosition();
        Vec3 rayDir = hitLocation.subtract(rayOrigin).normalize();

        ServiceRegistry.getInstance().placement().placeSelected(
                player,
                supportAbs,
                Direction.UP,
                hitLocation.x,
                hitLocation.y,
                hitLocation.z,
                (byte) 0,
                "",
                false,
                false,
                "minecraft:oak_slab",
                new ItemStack(Items.OAK_SLAB),
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z,
                false,
                false);

        helper.succeedWhen(() -> {
            BlockPos placedRel = supportRel.above();
            helper.assertBlockPresent(Blocks.OAK_SLAB, placedRel);
            helper.assertBlockProperty(
                    placedRel,
                    BlockStateProperties.SLAB_TYPE,
                    SlabType.BOTTOM);
            stopPlayers(player);
        });
    }

    /**
     * 真实复现玩家反馈：点击支撑方块顶面时，原版一定倾向放下半砖；
     * R 预设必须在完整单方块放置链末端把它覆盖成上半砖。
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void singlePlacementOverridesVanillaSlabHitHalf(GameTestHelper helper) {
        BlockPos supportRel = new BlockPos(2, 1, 2);
        helper.setBlock(supportRel, Blocks.DIRT);
        ServerPlayer player = startRtsPlayer(helper, GameType.CREATIVE);
        BlockPos supportAbs = helper.absolutePos(supportRel);
        Vec3 hitLocation = Vec3.atBottomCenterOf(supportAbs.above());
        Vec3 rayOrigin = player.getEyePosition();
        Vec3 rayDir = hitLocation.subtract(rayOrigin).normalize();

        ServiceRegistry.getInstance().placement().placeSelected(
                player,
                supportAbs,
                Direction.UP,
                hitLocation.x,
                hitLocation.y,
                hitLocation.z,
                (byte) 0,
                "type=top",
                false,
                false,
                "minecraft:oak_slab",
                new ItemStack(Items.OAK_SLAB),
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z,
                false,
                false);

        helper.succeedWhen(() -> {
            BlockPos placedRel = supportRel.above();
            helper.assertBlockPresent(Blocks.OAK_SLAB, placedRel);
            helper.assertBlockProperty(
                    placedRel,
                    BlockStateProperties.SLAB_TYPE,
                    SlabType.TOP);
            stopPlayers(player);
        });
    }

    /**
     * 方块形状的 Quick Build 使用预解析状态路径；它也必须覆盖点击顶面产生的
     * 原版下半砖，而不是只让单放置路径正确。
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void quickBuildPlacementOverridesVanillaSlabHitHalf(GameTestHelper helper) {
        BlockPos supportRel = new BlockPos(2, 1, 2);
        helper.setBlock(supportRel, Blocks.DIRT);
        ServerPlayer player = startRtsPlayer(helper, GameType.CREATIVE);
        BlockPos supportAbs = helper.absolutePos(supportRel);
        Vec3 rayOrigin = player.getEyePosition();
        Vec3 hitLocation = Vec3.atBottomCenterOf(supportAbs.above());
        Vec3 rayDir = hitLocation.subtract(rayOrigin).normalize();

        ServiceRegistry.getInstance().placement().enqueuePlaceBatch(
                player,
                // Quick Build 的位置列表是最终目标格，不是交互式放置所点击的支撑格。
                List.of(supportAbs.above()),
                Direction.UP,
                0.5D,
                1.0D,
                0.5D,
                (byte) 0,
                "type=top",
                false,
                false,
                "minecraft:oak_slab",
                new ItemStack(Items.OAK_SLAB),
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z);

        helper.succeedWhen(() -> {
            BlockPos placedRel = supportRel.above();
            helper.assertBlockPresent(Blocks.OAK_SLAB, placedRel);
            helper.assertBlockProperty(
                    placedRel,
                    BlockStateProperties.SLAB_TYPE,
                    SlabType.TOP);
            stopPlayers(player);
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void restoredTaskWorkflowIdDoesNotSwallowStatefulPlacement(GameTestHelper helper) {
        BlockPos oldSupportRel = new BlockPos(1, 1, 2);
        BlockPos newSupportRel = new BlockPos(3, 1, 2);
        helper.setBlock(oldSupportRel, Blocks.DIRT);
        helper.setBlock(newSupportRel, Blocks.DIRT);
        ServerPlayer player = startRtsPlayer(helper, GameType.CREATIVE);
        RtsStorageSession session = requireSession(helper, player);
        Vec3 rayOrigin = player.getEyePosition();
        BlockPos oldSupportAbs = helper.absolutePos(oldSupportRel);
        Vec3 oldHit = Vec3.atBottomCenterOf(oldSupportAbs.above());
        Vec3 oldRayDir = oldHit.subtract(rayOrigin).normalize();

        boolean restoredTaskQueued = RtsPlacementBatch.enqueuePlaceBatch(
                player,
                session,
                List.of(oldSupportAbs),
                Direction.UP,
                0.5D,
                1.0D,
                0.5D,
                (byte) 0,
                "",
                false,
                false,
                "minecraft:oak_stairs",
                new ItemStack(Items.OAK_STAIRS),
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                oldRayDir.x,
                oldRayDir.y,
                oldRayDir.z,
                false,
                false,
                false,
                0);
        helper.assertTrue(restoredTaskQueued,
                "Fixture must reserve workflow id 0 before the new operation starts");

        BlockPos newSupportAbs = helper.absolutePos(newSupportRel);
        Vec3 newHit = Vec3.atBottomCenterOf(newSupportAbs.above());
        Vec3 newRayDir = newHit.subtract(rayOrigin).normalize();
        ServiceRegistry.getInstance().placement().placeSelected(
                player,
                newSupportAbs,
                Direction.UP,
                newHit.x,
                newHit.y,
                newHit.z,
                (byte) 0,
                "facing=west",
                false,
                false,
                "minecraft:oak_stairs",
                new ItemStack(Items.OAK_STAIRS),
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                newRayDir.x,
                newRayDir.y,
                newRayDir.z,
                false,
                false);

        helper.succeedWhen(() -> {
            BlockPos placedRel = newSupportRel.above();
            helper.assertBlockPresent(Blocks.OAK_STAIRS, placedRel);
            helper.assertBlockProperty(
                    placedRel,
                    BlockStateProperties.HORIZONTAL_FACING,
                    Direction.WEST);
            stopPlayers(player);
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void chainMiningAdvancesContinuouslyAndAutoStoresEveryDrop(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        List<BlockPos> targetsRel = new ArrayList<>();
        for (int z = 3; z < 7; z++) {
            for (int x = 3; x < 11; x++) {
                BlockPos target = new BlockPos(x, 1, z);
                targetsRel.add(target);
                helper.setBlock(target, Blocks.DIRT);
            }
        }
        helper.setBlock(chestRel, Blocks.CHEST);

        ServerPlayer player = startRtsPlayer(helper, GameType.SURVIVAL);
        ItemStack shovel = new ItemStack(Items.DIAMOND_SHOVEL);
        player.getInventory().setItem(0, shovel.copy());
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        RtsAPI.get().bindings().setAutoStoreMinedDrops(player, true);
        RtsStorageSession session = requireSession(helper, player);
        RtsAPI.get().mining().startUltimine(player, helper.absolutePos(targetsRel.getFirst()),
                Direction.UP, (byte) 0, "", shovel, targetsRel.size(), (byte) 0, false);

        boolean[] terminalLogged = {false};
        helper.succeedWhen(() -> {
            for (BlockPos targetRel : targetsRel) helper.assertBlockPresent(Blocks.AIR, targetRel);
            boolean active = hasActiveTask(player, TaskType.MINING);
            int chestItems = countChestItem(helper, chestRel, Items.DIRT);
            int bufferItems = countBufferedItem(session, Items.DIRT);
            int inventoryItems = countPlayerItem(player, Items.DIRT);
            int worldItems = countWorldItem(helper, targetsRel, Items.DIRT);
            if (!active && !terminalLogged[0]) {
                terminalLogged[0] = true;
                RtsbuildingMod.LOGGER.info(
                        "RTS GameTest chain drop conservation: chest={} buffer={} inventory={} world={} total={}",
                        chestItems, bufferItems, inventoryItems, worldItems,
                        chestItems + bufferItems + inventoryItems + worldItems);
            }
            helper.assertValueEqual(targetsRel.size(), chestItems,
                    "Chain mining should put every drop into linked storage without escrow delay"
                            + " (buffer=" + bufferItems + ", inventory=" + inventoryItems
                            + ", world=" + worldItems + ")");
            helper.assertTrue(!active,
                    "Completed chain mining should not leave an active durable task");
            stopPlayers(player);
        });
    }

    /**
     * 回归：上一轮连锁挖掘进入批处理后，新提交的另一轮连锁挖掘仍必须从独立的首块蓄力开始。
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void queuedChainMiningStartsWithIndependentProgress(GameTestHelper helper) {
        BlockPos firstRel = new BlockPos(3, 1, 3);
        BlockPos secondRel = new BlockPos(8, 1, 8);
        helper.setBlock(firstRel, Blocks.DIRT);
        helper.setBlock(secondRel, Blocks.DIRT);

        ServerPlayer player = startRtsPlayer(helper, GameType.SURVIVAL);
        ItemStack firstShovel = new ItemStack(Items.DIAMOND_SHOVEL);
        ItemStack secondShovel = new ItemStack(Items.DIAMOND_SHOVEL);
        player.getInventory().setItem(0, firstShovel.copy());
        player.getInventory().setItem(1, secondShovel.copy());

        RtsAPI.get().mining().startUltimine(
                player, helper.absolutePos(firstRel), Direction.UP,
                (byte) 0, "", firstShovel, 1, (byte) 0, false);
        RtsAPI.get().mining().startUltimine(
                player, helper.absolutePos(secondRel), Direction.UP,
                (byte) 1, "", secondShovel, 1, (byte) 0, false);

        var activeMiningStates = TaskPersistenceRuntime.INSTANCE.coordinator().query()
                .ownedBy(player.getUUID()).stream()
                .filter(snapshot -> snapshot.type() == TaskType.MINING && !snapshot.state().terminal())
                .map(snapshot -> MiningTaskCodec.decode(snapshot.payload()).state())
                .toList();
        helper.assertValueEqual(2, activeMiningStates.size(),
                "Two separate chain-mining inputs should create two independent tasks");
        for (MiningTaskState state : activeMiningStates) {
            helper.assertTrue(state.mode() == MiningTaskState.Mode.PROGRESSIVE_SINGLE,
                    "Every queued chain-mining task must charge its own first block");
            helper.assertTrue(state.blockProgress() == 0.0F && state.visibleStage() == -1,
                    "A new chain-mining task must not inherit progress from another task");
        }

        stopPlayers(player);
        helper.succeed();
    }

    /**
     * 两个连锁任务的目标允许部分重叠，但同一世界方块只能产生一次掉落；
     * 已被另一个任务挖掉的目标应安全跳过，两个任务最终都必须结束。
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 220)
    public static void overlappingChainMiningCompletesWithoutDuplicateDrops(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        List<BlockPos> chainRel = new ArrayList<>();
        for (int x = 3; x < 11; x++) {
            BlockPos targetRel = new BlockPos(x, 1, 4);
            chainRel.add(targetRel);
            helper.setBlock(targetRel, Blocks.DIRT);
        }
        helper.setBlock(chestRel, Blocks.CHEST);

        ServerPlayer player = startRtsPlayer(helper, GameType.SURVIVAL);
        ItemStack firstShovel = new ItemStack(Items.DIAMOND_SHOVEL);
        ItemStack secondShovel = new ItemStack(Items.DIAMOND_SHOVEL);
        player.getInventory().setItem(0, firstShovel.copy());
        player.getInventory().setItem(1, secondShovel.copy());
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        RtsAPI.get().bindings().setAutoStoreMinedDrops(player, true);
        RtsStorageSession session = requireSession(helper, player);

        RtsAPI.get().mining().startUltimine(
                player, helper.absolutePos(chainRel.getFirst()), Direction.UP,
                (byte) 0, "", firstShovel, 5, (byte) 0, false);
        RtsAPI.get().mining().startUltimine(
                player, helper.absolutePos(chainRel.get(2)), Direction.UP,
                (byte) 1, "", secondShovel, 5, (byte) 0, false);

        var states = TaskPersistenceRuntime.INSTANCE.coordinator().query()
                .ownedBy(player.getUUID()).stream()
                .filter(snapshot -> snapshot.type() == TaskType.MINING && !snapshot.state().terminal())
                .map(snapshot -> MiningTaskCodec.decode(snapshot.payload()).state())
                .toList();
        helper.assertValueEqual(2, states.size(),
                "Overlapping chain inputs should remain two independent tasks");
        Set<BlockPos> firstTargets = new LinkedHashSet<>(states.get(0).remainingTargets());
        Set<BlockPos> secondTargets = new LinkedHashSet<>(states.get(1).remainingTargets());
        Set<BlockPos> overlap = new LinkedHashSet<>(firstTargets);
        overlap.retainAll(secondTargets);
        helper.assertTrue(!overlap.isEmpty(),
                "The regression fixture must contain overlapping chain-mining targets");
        helper.assertTrue(firstTargets.contains(helper.absolutePos(chainRel.get(2))),
                "The first task must be able to remove the second task's charged target");
        Set<BlockPos> uniqueTargets = new LinkedHashSet<>(firstTargets);
        uniqueTargets.addAll(secondTargets);
        List<BlockPos> uniqueTargetsRel = uniqueTargets.stream().map(helper::relativePos).toList();

        /*
         * 给终态写入、工具归还和掉落入库留出稳定窗口，再移除假玩家。
         * 这同时避免 GameTest 在任务刚转终态的同一 tick 关闭玩家连接。
         */
        helper.runAfterDelay(40, () -> {
            for (BlockPos targetRel : uniqueTargetsRel) {
                helper.assertBlockPresent(Blocks.AIR, targetRel);
            }
            int chestItems = countChestItem(helper, chestRel, Items.DIRT);
            int bufferItems = countBufferedItem(session, Items.DIRT);
            int inventoryItems = countPlayerItem(player, Items.DIRT);
            int worldItems = countWorldItem(helper, chainRel, Items.DIRT);
            helper.assertValueEqual(uniqueTargets.size(),
                    chestItems + bufferItems + inventoryItems + worldItems,
                    "Overlapping chain tasks must conserve exactly one drop per unique world block");
            helper.assertValueEqual(0, bufferItems + inventoryItems + worldItems,
                    "Completed overlapping chain drops should settle in linked storage");
            helper.assertTrue(!hasActiveTask(player, TaskType.MINING),
                    "Both overlapping chain-mining tasks must reach a terminal state");
            helper.assertValueEqual(2, countPlayerItem(player, Items.DIAMOND_SHOVEL),
                    "Overlapping chain tasks must return the borrowed tool exactly once");
            stopPlayers(player);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void singleLinkedChestJunkSearchAndPaginationStayCorrect(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(3, 1, 3);
        helper.setBlock(chestRel, Blocks.CHEST);
        Map<Item, Integer> expected = fillChestsWithJunk(helper, List.of(chestRel), 24);

        ServerPlayer player = startRtsPlayer(helper, GameType.CREATIVE);
        linkChests(helper, player, List.of(chestRel));

        S2CRtsStoragePagePayload firstPage = buildStoragePage(helper, player, 0, "", 8, false, List.of());
        helper.assertValueEqual(expected.size(), firstPage.totalEntries(),
                "Single chest junk storage should preserve every distinct item");
        helper.assertValueEqual(3, firstPage.totalPages(),
                "24 junk entries at 8 entries per page should produce three pages");
        assertPageCount(helper, firstPage, 8, "First page should contain the requested page size");
        assertTotalCount(helper, firstPage, Items.DIAMOND, expected.get(Items.DIAMOND),
                "Total counts should include diamonds");

        S2CRtsStoragePagePayload secondPage = buildStoragePage(helper, player, 1, "", 8, false, List.of());
        helper.assertTrue(secondPage.page() == 1 && secondPage.totalEntries() == expected.size(),
                "Changing page should not change the total entry count");
        assertPageCount(helper, secondPage, 8, "Second page should contain the requested page size");

        S2CRtsStoragePagePayload diamondById = buildStoragePage(helper, player,
                0, itemId(Items.DIAMOND), 8, false, List.of());
        assertSingleSearchResult(helper, diamondById, Items.DIAMOND,
                "Full item-id search should return only diamonds");

        S2CRtsStoragePagePayload diamondByLocalizedClientMatch = buildStoragePage(helper, player,
                0, "zuanshi", 8, false, List.of(itemId(Items.DIAMOND)));
        assertSingleSearchResult(helper, diamondByLocalizedClientMatch, Items.DIAMOND,
                "Client localized/pinyin matches should filter the server page");

        stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 220)
    public static void manyLinkedChestsJunkSearchCacheAndDirtyRefreshStayCorrect(GameTestHelper helper) {
        List<BlockPos> chestsRel = List.of(
                new BlockPos(1, 1, 1),
                new BlockPos(5, 1, 1),
                new BlockPos(9, 1, 1));
        for (BlockPos chestRel : chestsRel) {
            helper.setBlock(chestRel, Blocks.CHEST);
        }
        Map<Item, Integer> expected = fillChestsWithJunk(helper, chestsRel, 48);

        ServerPlayer player = startRtsPlayer(helper, GameType.CREATIVE);
        linkChests(helper, player, chestsRel);
        RtsStorageSession session = requireSession(helper, player);

        long versionBeforeRead = session.transfer.pageDataVersion.get();
        long firstStart = System.nanoTime();
        S2CRtsStoragePagePayload allFirst = buildStoragePage(helper, player, 0, "", 12, false, List.of());
        long firstNanos = System.nanoTime() - firstStart;

        long secondStart = System.nanoTime();
        S2CRtsStoragePagePayload allSecond = buildStoragePage(helper, player, 1, "", 12, false, List.of());
        long secondNanos = System.nanoTime() - secondStart;

        helper.assertValueEqual(expected.size(), allFirst.totalEntries(),
                "Multi-chest junk storage should preserve every distinct item");
        helper.assertTrue(allSecond.page() == 1 && allSecond.totalEntries() == allFirst.totalEntries(),
                "Same search parameters should reuse the same aggregate boundary while paging");
        helper.assertValueEqual(versionBeforeRead, session.transfer.pageDataVersion.get(),
                "Read-only page/search requests should not dirty the storage data version");
        helper.assertTrue(allFirst.totalPages() >= 4,
                "48 junk entries at 12 entries per page should produce multiple pages");
        assertTotalCount(helper, allFirst, Items.DIAMOND, expected.get(Items.DIAMOND),
                "Multi-chest total counts should include diamonds");
        RtsbuildingMod.LOGGER.info(
                "RTS GameTest junk storage page timings: first={}us second={}us entries={}",
                firstNanos / 1_000L,
                secondNanos / 1_000L,
                allFirst.totalEntries());

        S2CRtsStoragePagePayload minecraftNamespace = buildStoragePage(helper, player,
                0, "@minecraft", 16, false, List.of());
        helper.assertValueEqual(expected.size(), minecraftNamespace.totalEntries(),
                "@minecraft should match every vanilla junk entry");

        S2CRtsStoragePagePayload localizedEmerald = buildStoragePage(helper, player,
                0, "lvbaoshi", 16, false, List.of(itemId(Items.EMERALD)));
        assertSingleSearchResult(helper, localizedEmerald, Items.EMERALD,
                "Client localized/pinyin matches should locate emeralds in multi-chest storage");

        long versionBeforeStore = session.transfer.pageDataVersion.get();
        player.getInventory().setItem(0, new ItemStack(Items.HONEYCOMB, 11));
        RtsAPI.get().bindings().storeHotbarSlot(player, (byte) 0);

        helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                "Storing into a multi-chest junk setup should clear the original hotbar slot");
        helper.assertTrue(session.transfer.pageDataVersion.get() > versionBeforeStore,
                "Storing into a multi-chest junk setup should bump the storage data version");
        S2CRtsStoragePagePayload honeycomb = buildStoragePage(helper, player,
                0, itemId(Items.HONEYCOMB), 16, false, List.of());
        assertSingleSearchResult(helper, honeycomb, Items.HONEYCOMB,
                "Newly stored honeycomb should be immediately searchable");
        long storedHoneycomb = chestsRel.stream()
                .mapToLong(chestRel -> countChestItem(helper, chestRel, Items.HONEYCOMB))
                .sum();
        helper.assertValueEqual(11L, storedHoneycomb,
                "Newly stored honeycomb should keep its stored count in the backing storage");

        stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 240)
    public static void durableBlueprintWaitsForRootAckThenPlacesExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = startRtsPlayer(helper, GameType.CREATIVE);
        BlockPos anchorRel = new BlockPos(2, 1, 2);
        BlockPos anchor = helper.absolutePos(anchorRel);
        SubmissionId submissionId = new SubmissionId(UUID.randomUUID());
        TaskId taskId = TaskId.fromSubmission(player.getUUID(), submissionId);
        RtsBlueprint blueprint = simpleBlueprint("ack-blueprint", Blocks.STONE, 3);
        BlueprintContext context = blueprintContext(player, submissionId, blueprint, anchor);

        PipelineResult first = PipelineRegistry.execute(RtsWorkflowType.BLUEPRINT_BUILD, context);
        helper.assertTrue(first instanceof PipelineResult.Success,
                "Durable blueprint command should be accepted into the admission queue");
        PipelineResult duplicate = PipelineRegistry.execute(RtsWorkflowType.BLUEPRINT_BUILD,
                blueprintContext(player, submissionId, blueprint, anchor));
        helper.assertTrue(duplicate instanceof PipelineResult.Success,
                "Repeating the same submission while pending should be idempotent");

        // 同步 command 返回只代表进入有界 admission；本次服务器 tick 的 root ACK 尚未发生。
        helper.assertTrue(TaskPersistenceRuntime.INSTANCE.coordinator().query().get(taskId).isEmpty(),
                "Blueprint root must not be visible before the durability ACK");
        helper.assertValueEqual(0, RtsWorkflowEngine.getInstance().activeWorkflowCount(player),
                "Workflow projection must not exist before the durability ACK");
        helper.assertValueEqual(0,
                RtsTaskEngine.INSTANCE.diagnostics(player.getUUID()).activeByType()
                        .getOrDefault(TaskType.BLUEPRINT, 0),
                "Blueprint executor must not exist before the durability ACK");
        for (int i = 0; i < 3; i++) {
            helper.assertBlockPresent(Blocks.AIR, anchorRel.offset(i, 0, 0));
        }

        // 不手动调用全局 Task Engine；真实 ServerTickEvent 每服每 tick 驱动一次。
        helper.succeedWhen(() -> {
            for (int i = 0; i < 3; i++) {
                helper.assertBlockPresent(Blocks.STONE, anchorRel.offset(i, 0, 0));
            }
            var query = TaskPersistenceRuntime.INSTANCE.coordinator().query();
            var activeRoot = query.get(taskId);
            var terminalReceipt = query.receipt(taskId);
            helper.assertValueEqual(1,
                    (activeRoot.isPresent() ? 1 : 0) + (terminalReceipt.isPresent() ? 1 : 0),
                    "The deterministic TaskId must have exactly one active root or terminal receipt");
            long sameSubmissionRoots = query.ownedBy(player.getUUID()).stream()
                    .filter(snapshot -> snapshot.submissionId().equals(submissionId))
                    .count();
            long sameSubmissionFacts = sameSubmissionRoots + (terminalReceipt.isPresent() ? 1L : 0L);
            helper.assertValueEqual(1L, sameSubmissionFacts,
                    "Repeating one blueprint submission must leave exactly one durable fact");
            stopPlayers(player);
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 180)
    public static void denseFunnelIsBoundedAndNeverUsesAnotherDimensionTarget(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        BlockPos targetRel = new BlockPos(4, 1, 4);
        helper.setBlock(chestRel, Blocks.CHEST);
        ServerPlayer player = startRtsPlayer(helper, GameType.CREATIVE);
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        RtsAPI.get().bindings().setMode(player, BuilderMode.FUNNEL);
        RtsAPI.get().bindings().setFunnelEnabled(player, true);
        RtsAPI.get().bindings().updateFunnelTarget(player, helper.absolutePos(targetRel));
        RtsStorageSession session = requireSession(helper, player);

        final int entityCount = 60;
        AABB scanBox = new AABB(helper.absolutePos(targetRel)).inflate(RtsServiceConstants.FUNNEL_RADIUS);
        for (int i = 0; i < entityCount; i++) {
            Vec3 dropPos = Vec3.atCenterOf(helper.absolutePos(targetRel));
            ItemEntity drop = new ItemEntity(helper.getLevel(), dropPos.x, dropPos.y, dropPos.z,
                    new ItemStack(Items.COBBLESTONE));
            helper.getLevel().addFreshEntity(drop);
        }

        var bounded = ServiceRegistry.getInstance().funnel().tickBudgeted(
                player, session, 7, Long.MAX_VALUE);
        helper.assertValueEqual(7, bounded.processedUnits(),
                "A funnel slice must obey the caller's smaller unit budget");
        helper.assertValueEqual(7, countChestItem(helper, chestRel, Items.COBBLESTONE),
                "One bounded funnel slice should move only seven one-item entities");
        helper.assertValueEqual(entityCount - 7, countLiveDrops(helper, scanBox),
                "Entities outside the current slice budget must remain in the world");

        session.funnel.funnelTickCooldown = 0;
        session.funnel.funnelTargetDimension = Level.NETHER;
        int storedBeforeWrongDimension = countChestItem(helper, chestRel, Items.COBBLESTONE);
        int liveBeforeWrongDimension = countLiveDrops(helper, scanBox);
        var wrongDimension = ServiceRegistry.getInstance().funnel().tickBudgeted(
                player, session, 7, Long.MAX_VALUE);
        helper.assertValueEqual(0, wrongDimension.processedUnits(),
                "A funnel target from another dimension must yield without scanning this world");
        helper.assertValueEqual(storedBeforeWrongDimension,
                countChestItem(helper, chestRel, Items.COBBLESTONE),
                "Wrong-dimension funnel work must not mutate linked storage");
        helper.assertValueEqual(liveBeforeWrongDimension, countLiveDrops(helper, scanBox),
                "Wrong-dimension funnel work must not consume same-coordinate entities");

        session.funnel.funnelTargetDimension = player.serverLevel().dimension();
        session.funnel.funnelTickCooldown = 0;
        helper.succeedWhen(() -> {
            helper.assertValueEqual(entityCount, countChestItem(helper, chestRel, Items.COBBLESTONE),
                    "The real Task Engine should eventually drain all reachable funnel drops");
            helper.assertValueEqual(0, countLiveDrops(helper, scanBox),
                    "Fully stored funnel drops should leave no live ItemEntity behind");
            stopPlayers(player);
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void placedRecoveryPreservesUnavailableClaimsAndConsumesOnlyExactLoadedClaim(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        BlockPos targetRel = new BlockPos(4, 1, 4);
        helper.setBlock(chestRel, Blocks.CHEST);
        ServerPlayer player = startRtsPlayer(helper, GameType.CREATIVE);
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        RtsStorageSession session = requireSession(helper, player);

        BlockPos target = helper.absolutePos(targetRel);
        BlockPos unloadedTarget = findUnloadedTarget(helper);
        helper.assertTrue(!helper.getLevel().hasChunkAt(unloadedTarget),
                "Recovery fixture requires a genuinely unloaded target chunk");

        ItemEntity mismatch = spawnDrop(helper, target, new ItemStack(Items.DIRT, 2));
        ItemEntity exact = spawnDrop(helper, target, new ItemStack(Items.IRON_INGOT, 5));
        PlacedRecoveryJob unloaded = recoveryJob(
                player, unloadedTarget, UUID.randomUUID(), new ItemStack(Items.GOLD_INGOT), 0);
        PlacedRecoveryJob mismatched = recoveryJob(
                player, target, mismatch.getUUID(), new ItemStack(Items.STONE, 2), 0);
        PlacedRecoveryJob matching = recoveryJob(
                player, target, exact.getUUID(), exact.getItem(), 0);
        session.placement.recoveryJobs.addLast(unloaded);
        session.placement.recoveryJobs.addLast(mismatched);
        session.placement.recoveryJobs.addLast(matching);

        var result = RtsPlacedRecoveryService.tickBudgeted(player, session, 1, Long.MAX_VALUE);
        helper.assertValueEqual(1, result.processedUnits(),
                "One recovery slice should consume exactly one runnable matching claim");
        helper.assertTrue(!exact.isAlive(),
                "A matching loaded claim should release its source ItemEntity after insertion");
        helper.assertValueEqual(5, countChestItem(helper, chestRel, Items.IRON_INGOT),
                "Recovered items should reach the linked storage exactly once");
        helper.assertTrue(mismatch.isAlive() && mismatch.getItem().is(Items.DIRT),
                "A stale claim must not consume an entity whose stack identity changed");
        helper.assertTrue(session.placement.recoveryJobs.contains(unloaded)
                        && unloaded.claims().size() == 1,
                "An unloaded-chunk claim must remain queued without forcing its chunk");
        helper.assertTrue(session.placement.recoveryJobs.contains(mismatched)
                        && mismatched.claims().size() == 1,
                "A mismatched claim must remain queued for conservative recovery");
        helper.assertTrue(!helper.getLevel().hasChunkAt(unloadedTarget),
                "Recovery readiness checks must not load the unavailable chunk");
        stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 260)
    public static void twoPlayersCanUseSameBlueprintSubmissionWithoutCrossTalk(GameTestHelper helper) {
        List<ServerPlayer> players = startRtsPlayers(helper, 2, GameType.CREATIVE);
        ServerPlayer first = players.get(0);
        ServerPlayer second = players.get(1);
        SubmissionId sharedSubmission = new SubmissionId(UUID.randomUUID());
        TaskId firstTask = TaskId.fromSubmission(first.getUUID(), sharedSubmission);
        TaskId secondTask = TaskId.fromSubmission(second.getUUID(), sharedSubmission);
        helper.assertTrue(!firstTask.equals(secondTask),
                "Task identity must include the owner even when submission UUIDs match");

        BlockPos firstRel = new BlockPos(2, 1, 2);
        BlockPos secondRel = new BlockPos(7, 1, 7);
        PipelineResult firstResult = PipelineRegistry.execute(RtsWorkflowType.BLUEPRINT_BUILD,
                blueprintContext(first, sharedSubmission,
                        simpleBlueprint("owner-one", Blocks.GOLD_BLOCK, 1), helper.absolutePos(firstRel)));
        PipelineResult secondResult = PipelineRegistry.execute(RtsWorkflowType.BLUEPRINT_BUILD,
                blueprintContext(second, sharedSubmission,
                        simpleBlueprint("owner-two", Blocks.DIAMOND_BLOCK, 1), helper.absolutePos(secondRel)));
        helper.assertTrue(firstResult instanceof PipelineResult.Success
                        && secondResult instanceof PipelineResult.Success,
                "Both owners should independently enter durable blueprint admission");
        helper.assertTrue(TaskPersistenceRuntime.INSTANCE.coordinator().query().get(firstTask).isEmpty()
                        && TaskPersistenceRuntime.INSTANCE.coordinator().query().get(secondTask).isEmpty(),
                "Neither owner's executor may appear before its own root ACK");

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.GOLD_BLOCK, firstRel);
            helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, secondRel);
            var query = TaskPersistenceRuntime.INSTANCE.coordinator().query();
            helper.assertValueEqual(1,
                    (query.get(firstTask).isPresent() ? 1 : 0)
                            + (query.receipt(firstTask).isPresent() ? 1 : 0),
                    "First player must own exactly one active root or terminal receipt");
            helper.assertValueEqual(1,
                    (query.get(secondTask).isPresent() ? 1 : 0)
                            + (query.receipt(secondTask).isPresent() ? 1 : 0),
                    "Second player must own exactly one active root or terminal receipt");
            helper.assertTrue(query.ownedBy(first.getUUID()).stream()
                            .noneMatch(snapshot -> snapshot.id().equals(secondTask)),
                    "First player's durable roots must never contain the second player's task");
            helper.assertTrue(query.ownedBy(second.getUUID()).stream()
                            .noneMatch(snapshot -> snapshot.id().equals(firstTask)),
                    "Second player's durable roots must never contain the first player's task");
            stopPlayers(players);
        });
    }

    private static boolean hasActiveTask(ServerPlayer player, TaskType type) {
        return TaskPersistenceRuntime.INSTANCE.coordinator().query().ownedBy(player.getUUID()).stream()
                .anyMatch(snapshot -> snapshot.type() == type && !snapshot.state().terminal());
    }

    private static ServerPlayer startRtsPlayer(GameTestHelper helper, GameType gameType) {
        return startRtsPlayer(helper, gameType, new Vec3(3.5D, 2.0D, 3.5D));
    }

    private static List<ServerPlayer> startRtsPlayers(GameTestHelper helper, int count, GameType gameType) {
        List<ServerPlayer> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(startRegisteredRtsPlayer(
                    helper, gameType, new Vec3(3.5D + i, 4.0D, 3.5D), nextPlayerName()));
        }
        return players;
    }

    private static ServerPlayer startRtsPlayer(GameTestHelper helper, GameType gameType, Vec3 relativePos) {
        return startRegisteredRtsPlayer(helper, gameType, relativePos, nextPlayerName());
    }

    /**
     * 创建真正登记到 PlayerList 的测试玩家。
     *
     * <p>Task Engine、durable activator 和在线 owner 查询均以 PlayerList 为准；
     * FakePlayerFactory 只创建世界实体，会让测试绕过生产生命周期。每个玩家使用唯一名称，
     * 避免并行 GameTest 中多个默认 test-mock-player 相互覆盖。</p>
     */
    private static ServerPlayer startRegisteredRtsPlayer(
            GameTestHelper helper, GameType gameType, Vec3 relativePos, String name) {
        // GameTest 配置目录会跨运行保留；普通测试必须从关闭生存门禁的确定状态开始。
        if (Config.ENABLE_SURVIVAL_PROGRESSION.getAsBoolean()) {
            Config.setSurvivalProgressionEnabled(false);
        }
        ensureCoreServices();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        // 与原版 GameTest 的 mock player 保持相同的模式判定语义，同时仍使用唯一名称并注册进 PlayerList。
        // 生产放置链会直接查询 isCreative()/isSpectator()；普通 ServerPlayer 在测试连接刚建立时可能尚未
        // 完成这些派生状态的同步，导致实际 useItemOn 已执行却被错误归入跳过。
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return gameType == GameType.SPECTATOR;
            }

            @Override
            public boolean isCreative() {
                return gameType == GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        Vec3 playerPos = helper.absoluteVec(relativePos);
        player.moveTo(playerPos.x, playerPos.y, playerPos.z, 0.0F, 0.0F);
        player.setGameMode(gameType);
        RtsCameraManager.start(player);
        helper.assertTrue(RtsCameraManager.isActive(player),
                "GameTest fake player should enter RTS mode");
        requireSession(helper, player);
        return player;
    }

    private static String nextPlayerName() {
        return "rtsgt-" + Integer.toUnsignedString(PLAYER_SEQUENCE.incrementAndGet(), 36);
    }

    private static void ensureCoreServices() {
        ServiceRegistry.init();
        if (RtsAPI.get() == null) {
            RtsAPIImpl.init();
        }
        if (PipelineRegistry.size() == 0) {
            RtsPipelineRegistration.registerAll();
        }
    }

    private static void enqueuePlacementThroughApi(GameTestHelper helper, ServerPlayer player,
            List<BlockPos> supportsRel, String itemId, ItemStack prototype) {
        // 快速建造 API 接收的是最终目标坐标，而不是交互式右键放置所使用的支撑方块坐标。
        List<BlockPos> targetsAbs = supportsRel.stream()
                .map(BlockPos::above)
                .map(helper::absolutePos)
                .toList();
        Vec3 rayOrigin = player.getEyePosition();
        Vec3 firstHit = Vec3.atCenterOf(targetsAbs.getFirst());
        Vec3 rayDir = firstHit.subtract(rayOrigin).normalize();

        RtsAPI.get().placement().enqueueBatch(player, asApiPositions(targetsAbs), Direction.UP,
                0.5D, 1.0D, 0.5D,
                (byte) 0, false, false,
                itemId, prototype,
                rayOrigin.x, rayOrigin.y, rayOrigin.z,
                rayDir.x, rayDir.y, rayDir.z);
    }

    private static List<BlockPos> linePositions(int startX, int y, int z, int length) {
        List<BlockPos> positions = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            positions.add(new BlockPos(startX + i, y, z));
        }
        return positions;
    }

    /** 创建只包含同一种方块的最小蓝图，避免 GameTest 依赖外部蓝图文件。 */
    private static RtsBlueprint simpleBlueprint(String name, Block block, int length) {
        List<RtsBlueprintBlock> blocks = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            blocks.add(new RtsBlueprintBlock(
                    new BlockPos(i, 0, 0), block.defaultBlockState(), new CompoundTag()));
        }
        return RtsBlueprint.create(
                name, name + ".nbt", BlueprintFormat.VANILLA_NBT,
                new Vec3i(length, 1, 1), blocks);
    }

    /** 为真实管道构造完整蓝图上下文，submissionId 由测试显式控制。 */
    private static BlueprintContext blueprintContext(ServerPlayer player, SubmissionId submissionId,
            RtsBlueprint blueprint, BlockPos anchor) {
        return BlueprintContext.builder(player)
                .submissionId(submissionId.value())
                .blueprint(blueprint)
                .anchor(anchor)
                .yRotationSteps(0)
                .xRotationSteps(0)
                .zRotationSteps(0)
                .totalBlocks(blueprint.blockCount())
                .build();
    }

    private static int countLiveDrops(GameTestHelper helper, AABB bounds) {
        return helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, bounds,
                entity -> entity.isAlive() && !entity.getItem().isEmpty()).size();
    }

    private static int countBufferedItem(RtsStorageSession session, Item item) {
        return session.miningDropBuffer.stacks.stream()
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    private static int countPlayerItem(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int countWorldItem(GameTestHelper helper, List<BlockPos> targetsRel, Item item) {
        BlockPos first = helper.absolutePos(targetsRel.getFirst());
        BlockPos last = helper.absolutePos(targetsRel.getLast());
        AABB bounds = new AABB(
                first.getX(), first.getY(), first.getZ(),
                last.getX() + 1.0D, last.getY() + 1.0D, last.getZ() + 1.0D).inflate(4.0D);
        return helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class, bounds,
                        entity -> entity.isAlive() && entity.getItem().is(item))
                .stream()
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
    }

    /** 找到远离测试结构且当前未加载的位置，用来验证服务不会隐式强加载区块。 */
    private static BlockPos findUnloadedTarget(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int offset : new int[] {512, 1024, 2048, 4096}) {
            BlockPos candidate = origin.offset(offset, 0, offset);
            if (!helper.getLevel().hasChunkAt(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("GameTest could not find an unloaded recovery target");
    }

    private static ItemEntity spawnDrop(GameTestHelper helper, BlockPos absolutePos, ItemStack stack) {
        Vec3 center = Vec3.atCenterOf(absolutePos);
        ItemEntity entity = new ItemEntity(
                helper.getLevel(), center.x, center.y, center.z, stack.copy());
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static PlacedRecoveryJob recoveryJob(ServerPlayer player, BlockPos target,
            UUID entityId, ItemStack expectedStack, int ordinal) {
        ArrayDeque<PlacedRecoveryClaim> claims = new ArrayDeque<>();
        claims.addLast(new PlacedRecoveryClaim(entityId, ordinal, expectedStack));
        return new PlacedRecoveryJob(
                UUID.randomUUID(), player.serverLevel().dimension(), target, claims);
    }

    private static List<Object> asApiPositions(GameTestHelper helper, List<BlockPos> relativePositions) {
        return asApiPositions(relativePositions.stream()
                .map(helper::absolutePos)
                .toList());
    }

    private static List<Object> asApiPositions(List<BlockPos> positions) {
        return new ArrayList<>(positions);
    }

    private static void linkChests(GameTestHelper helper, ServerPlayer player, List<BlockPos> chestsRel) {
        for (BlockPos chestRel : chestsRel) {
            RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                    RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        }
        RtsStorageSession session = requireSession(helper, player);
        helper.assertValueEqual(chestsRel.size(), session.linkedStorageInfo.size(),
                "Linked storage count should equal the test chest count");
    }

    private static Map<Item, Integer> fillChestsWithJunk(GameTestHelper helper, List<BlockPos> chestsRel, int itemCount) {
        helper.assertTrue(itemCount <= chestsRel.size() * 27,
                "Junk item count must fit into the provided chests");
        helper.assertTrue(itemCount <= JUNK_ITEMS.size(),
                "Junk item count must fit into the fixture item list");
        Map<Item, Integer> expected = new LinkedHashMap<>();
        for (int index = 0; index < itemCount; index++) {
            BlockPos chestRel = chestsRel.get(index / 27);
            int slot = index % 27;
            Item item = JUNK_ITEMS.get(index);
            int count = 3 + (index % 29);
            setChestStack(helper, chestRel, slot, new ItemStack(item, count));
            expected.put(item, count);
        }
        return expected;
    }

    private static S2CRtsStoragePagePayload buildStoragePage(GameTestHelper helper, ServerPlayer player,
            int requestedPage, String search, int pageSize, boolean pinyinSearchEnabled,
            List<String> localizedSearchMatches) {
        RtsStorageSession session = requireSession(helper, player);
        session.browser.search = search == null ? "" : search;
        session.browser.category = RtsStoragePageBuilder.normalizeCategory("all");
        session.browser.sort = RtsStorageSort.NAME;
        session.browser.ascending = true;
        session.browser.pageSize = RtsStoragePageBuilder.sanitizePageSize(pageSize);
        session.browser.pinyinSearchEnabled = pinyinSearchEnabled;
        session.browser.localizedSearchMatches.clear();
        session.browser.localizedSearchMatches.addAll(
                RtsStoragePageBuilder.sanitizeLocalizedSearchMatches(localizedSearchMatches).stream().toList());
        session.bdCache.handlerStale = true;
        session.bdCache.fluidHandlerStale = true;

        List<LinkedHandler> itemHandlers = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> fluidHandlers = RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);
        RtsLinkedHandlerResolutionService.registerStorageCaches(player, itemHandlers);
        RtsStorageTickService.INSTANCE.forceRefresh(player);

        PageResult result = RtsStoragePageBuilder.build(player, session,
                requestedPage, session.browser.pageSize, itemHandlers, fluidHandlers);
        session.browser.page = result.safePage();
        return result.payload();
    }

    private static void assertPageCount(GameTestHelper helper, S2CRtsStoragePagePayload payload,
            int expectedCount, String message) {
        helper.assertTrue(payload.itemStacks().size() == expectedCount && payload.counts().size() == expectedCount,
                message);
    }

    private static void assertTotalCount(GameTestHelper helper, S2CRtsStoragePagePayload payload,
            Item item, long expected, String message) {
        long actual = totalCount(payload, item);
        helper.assertValueEqual(expected, actual, message);
    }

    private static void assertSingleSearchResult(GameTestHelper helper, S2CRtsStoragePagePayload payload,
            Item expectedItem, String message) {
        helper.assertValueEqual(1, payload.totalEntries(), message);
        helper.assertTrue(payload.itemStacks().size() == 1 && payload.itemStacks().getFirst().getItem() == expectedItem,
                message);
    }

    private static long totalCount(S2CRtsStoragePagePayload payload, Item item) {
        String id = itemId(item);
        long total = 0L;
        int size = Math.min(payload.totalItemIds().size(), payload.totalItemCounts().size());
        for (int i = 0; i < size; i++) {
            if (id.equals(payload.totalItemIds().get(i))) {
                total += payload.totalItemCounts().get(i);
            }
        }
        return total;
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static void stopPlayers(ServerPlayer player) {
        RtsCameraManager.stopIfActive(player);
        if (player.getServer() != null
                && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
            player.getServer().getPlayerList().remove(player);
        }
    }

    private static void stopPlayers(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            stopPlayers(player);
        }
    }

    private static RtsStorageSession requireSession(GameTestHelper helper, ServerPlayer player) {
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        helper.assertTrue(session != null, "RTS mode should create a server session");
        return session;
    }

    private static void setChestStack(GameTestHelper helper, BlockPos chestRel, int slot, ItemStack stack) {
        ChestBlockEntity chest = requireChest(helper, chestRel);
        chest.setItem(slot, stack);
        chest.setChanged();
    }

    private static int countChestItem(GameTestHelper helper, BlockPos chestRel, Item item) {
        ChestBlockEntity chest = requireChest(helper, chestRel);
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static ChestBlockEntity requireChest(GameTestHelper helper, BlockPos chestRel) {
        BlockEntity blockEntity = helper.getBlockEntity(chestRel);
        helper.assertTrue(blockEntity instanceof ChestBlockEntity,
                "Test scene should contain an accessible chest block entity");
        return (ChestBlockEntity) blockEntity;
    }
}
