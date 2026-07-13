package com.rtsbuilding.rtsbuilding.gametest;

import com.mojang.authlib.GameProfile;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsAPIImpl;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.RtsPipelineRegistration;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.ServerTickOrchestrator;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.page.PageResult;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(RtsbuildingMod.MODID)
@PrefixGameTestTemplate(false)
public final class RtsServerGameTests {
    private static final String EMPTY_TEMPLATE = "gametest/empty";
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
        helper.assertTrue(!requireSession(helper, player).placement.placeBatchJobs.isEmpty(),
                "RTS batch placement should enqueue a placement job");

        helper.succeedWhen(() -> {
            tickPlayerWork(player);
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
            for (ServerPlayer player : players) {
                tickPlayerWork(player);
            }
            for (List<BlockPos> supportsRel : supportGroupsRel) {
                for (BlockPos supportRel : supportsRel) {
                    helper.assertBlockPresent(Blocks.STONE, supportRel.above());
                }
            }
            for (ServerPlayer player : players) {
                helper.assertTrue(requireSession(helper, player).placement.placeBatchJobs.isEmpty(),
                        "Completed placement should not leave another player's job in this session");
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
            for (ServerPlayer player : players) {
                tickPlayerWork(player);
            }
            for (List<BlockPos> targetsRel : targetGroupsRel) {
                for (BlockPos targetRel : targetsRel) {
                    helper.assertBlockPresent(Blocks.AIR, targetRel);
                }
            }
            for (ServerPlayer player : players) {
                helper.assertTrue(requireSession(helper, player).destruction.destroyJobs.isEmpty(),
                        "Completed area destroy should not leave another player's job in this session");
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
            tickPlayerWork(player);
            for (BlockPos targetRel : targetsRel) {
                helper.assertBlockPresent(Blocks.AIR, targetRel);
            }
            helper.assertValueEqual(targetsRel.size(), countChestItem(helper, chestRel, Items.DIRT),
                    "Auto-store should put range-destroy drops into the linked chest");
            helper.assertTrue(requireSession(helper, player).destruction.destroyJobs.isEmpty(),
                    "Auto-store area destroy should finish without queued targets");
            stopPlayers(player);
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
        helper.assertValueEqual(11L, totalCount(honeycomb, Items.HONEYCOMB),
                "Newly stored honeycomb should keep its stored count");

        stopPlayers(player);
        helper.succeed();
    }

    private static ServerPlayer startRtsPlayer(GameTestHelper helper, GameType gameType) {
        return startRtsPlayer(helper, gameType, new Vec3(3.5D, 2.0D, 3.5D));
    }

    private static List<ServerPlayer> startRtsPlayers(GameTestHelper helper, int count, GameType gameType) {
        List<ServerPlayer> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(startRtsFakePlayer(helper, gameType, new Vec3(3.5D + i, 2.0D, 3.5D), "rtsgt-" + i));
        }
        return players;
    }

    private static ServerPlayer startRtsPlayer(GameTestHelper helper, GameType gameType, Vec3 relativePos) {
        ensureCoreServices();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 playerPos = helper.absoluteVec(relativePos);
        player.moveTo(playerPos.x, playerPos.y, playerPos.z, 0.0F, 0.0F);
        player.setGameMode(gameType);
        RtsCameraManager.start(player);
        helper.assertTrue(RtsCameraManager.isActive(player),
                "GameTest player should enter RTS mode");
        requireSession(helper, player);
        return player;
    }

    private static ServerPlayer startRtsFakePlayer(
            GameTestHelper helper, GameType gameType, Vec3 relativePos, String name) {
        ensureCoreServices();
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        Vec3 playerPos = helper.absoluteVec(relativePos);
        player.moveTo(playerPos.x, playerPos.y, playerPos.z, 0.0F, 0.0F);
        player.setGameMode(gameType);
        RtsCameraManager.start(player);
        helper.assertTrue(RtsCameraManager.isActive(player),
                "GameTest fake player should enter RTS mode");
        requireSession(helper, player);
        return player;
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
        List<BlockPos> supportsAbs = supportsRel.stream()
                .map(helper::absolutePos)
                .toList();
        Vec3 rayOrigin = player.getEyePosition();
        Vec3 firstHit = Vec3.atCenterOf(supportsAbs.getFirst()).add(0.0D, 0.5D, 0.0D);
        Vec3 rayDir = firstHit.subtract(rayOrigin).normalize();

        RtsAPI.get().placement().enqueueBatch(player, asApiPositions(supportsAbs), Direction.UP,
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

    private static List<Object> asApiPositions(GameTestHelper helper, List<BlockPos> relativePositions) {
        return asApiPositions(relativePositions.stream()
                .map(helper::absolutePos)
                .toList());
    }

    private static List<Object> asApiPositions(List<BlockPos> positions) {
        return new ArrayList<>(positions);
    }

    private static void tickPlayerWork(ServerPlayer player) {
        ServerTickOrchestrator.getInstance().onPlayerTickPost(player);
        if (player.getServer() != null) {
            ServerTickOrchestrator.getInstance().tickMining(player.getServer());
        }
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
