package com.rtsbuilding.rtsbuilding.gametest;

import com.mojang.authlib.GameProfile;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.page.PageResult;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsAggregateStorage;
import com.rtsbuilding.rtsbuilding.server.storage.RtsHandlerCache;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * 服务端基础链路烟测。
 * <p>这些测试把 RTSBuilding 当作可操作的黑箱：通过公开 API 触发玩家行为，再从世界、箱子、玩家会话和储存分页结果观察行为是否正确。客户端渲染、真实鼠标输入和第三方 GUI 生命周期由后续客户端探针覆盖。</p>
 */
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
        ServerPlayer player = startRtsPlayer(helper);

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
                "RTS 空手右键箱子后应该打开箱子菜单");
        stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void linkedStorageCountsChestContents(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(3, 1, 3);
        helper.setBlock(chestRel, Blocks.CHEST);
        setChestStack(helper, chestRel, 0, new ItemStack(Items.STONE, 19));
        ServerPlayer player = startRtsPlayer(helper);

        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);

        RtsStorageSession session = requireSession(helper, player);
        helper.assertTrue(session.linkedStorages.size() == 1,
                "RTS 链接箱子后会话里应该有 1 个链接存储");
        long stoneCount = RtsAPI.get().storage().countItemsMatching(player, stack -> stack.getItem() == Items.STONE);
        helper.assertTrue(stoneCount == 19L,
                "RTS 链接存储应该能统计箱子里的石头数量");
        stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void storeHotbarSlotMovesItemsIntoLinkedChest(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(3, 1, 3);
        helper.setBlock(chestRel, Blocks.CHEST);
        ServerPlayer player = startRtsPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(Items.DIRT, 12));

        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        RtsAPI.get().bindings().storeHotbarSlot(player, (byte) 0);

        helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                "RTS 存入快捷栏后，玩家快捷栏原槽位应该被清空");
        helper.assertTrue(countChestItem(helper, chestRel, Items.DIRT) == 12,
                "RTS 存入快捷栏应该把泥土放进链接箱子");
        stopPlayers(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void largeNetworkStyleHandlersExtractAndStoreWithoutSlotScan(GameTestHelper helper) {
        for (NetworkKind kind : NetworkKind.values()) {
            InstrumentedNetworkHandler handler = InstrumentedNetworkHandler.seeded(kind,
                    Map.of(Items.DIRT, 12_000_000L, Items.DIAMOND, 32L));
            RtsHandlerCache cache = new RtsHandlerCache();
            cache.update(handler);
            int readsAfterInitialRefresh = handler.stackReads;

            RtsAggregateStorage storage = new RtsAggregateStorage();
            storage.mount(100, handler, cache);
            ItemStack extracted = storage.extractMatching(Items.DIRT, new ItemStack(Items.DIRT), 1);

            assertStack(helper, extracted, Items.DIRT, 1,
                    kind + " 聚合层应该能从巨量网络直接提取被选中的方块");
            helper.assertTrue(handler.extractAnywhereCalls == 1,
                    kind + " 聚合层提取必须走 direct extract");
            helper.assertTrue(handler.perSlotExtractCalls == 0,
                    kind + " 聚合层提取不应该退回逐槽扫描");
            helper.assertTrue(handler.stackReads == readsAfterInitialRefresh,
                    kind + " 缓存热了之后，提取不应该重新读取网络槽");
            helper.assertTrue(handler.storedCount(Items.DIRT) == 11_999_999L,
                    kind + " direct extract 后巨量计数应该减少 1");

            ItemStack fallbackExtracted = RtsTransferExtractor.extractMatching(
                    handler, Items.DIRT, new ItemStack(Items.DIRT), 1);
            assertStack(helper, fallbackExtracted, Items.DIRT, 1,
                    kind + " fallback transfer 也应该能直接提取被选中的方块");
            helper.assertTrue(handler.extractAnywhereCalls == 2,
                    kind + " fallback transfer 必须继续走 direct extract");
            helper.assertTrue(handler.perSlotExtractCalls == 0,
                    kind + " fallback transfer 不应该退回逐槽扫描");

            ItemStack remainder = storage.insert(new ItemStack(Items.STONE, 64), false);
            helper.assertTrue(remainder.isEmpty(), kind + " 聚合层存入应该被巨量网络接受");
            helper.assertTrue(handler.insertAnywhereCalls == 1,
                    kind + " 聚合层存入应该走 direct insert");
            helper.assertTrue(handler.perSlotInsertCalls == 0,
                    kind + " 聚合层存入不应该退回逐槽扫描");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void placeBatchBuildsBlocksInWorld(GameTestHelper helper) {
        List<BlockPos> supportRel = List.of(
                new BlockPos(2, 1, 2),
                new BlockPos(3, 1, 2),
                new BlockPos(4, 1, 2));
        for (BlockPos pos : supportRel) {
            helper.setBlock(pos, Blocks.DIRT);
        }
        ServerPlayer player = startRtsPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(Items.STONE, supportRel.size()));

        enqueuePlacementThroughApi(helper, player, supportRel, "minecraft:stone", new ItemStack(Items.STONE));
        helper.assertTrue(!requireSession(helper, player).placement.placeBatchJobs.isEmpty(),
                "RTS 批量放置任务应该能入队");

        helper.succeedWhen(() -> {
            RtsAPI.get().lifecycle().onPlayerTickPost(player);
            for (BlockPos support : supportRel) {
                helper.assertBlockPresent(Blocks.STONE, support.above());
            }
            stopPlayers(player);
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void fiveRtsPlayersKeepIndependentSessions(GameTestHelper helper) {
        List<ServerPlayer> players = startRtsPlayers(helper, 5);

        for (ServerPlayer player : players) {
            RtsStorageSession session = requireSession(helper, player);
            helper.assertTrue(RtsCameraManager.isActive(player),
                    "每个 GameTest 玩家都应该能独立进入 RTS 模式");
            helper.assertTrue(session.linkedStorages.isEmpty(),
                    "RTS 会话不应该继承其他玩家的链接存储");
        }

        stopPlayers(players);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void fivePlayersPlaceBatchesWithoutCrossTalk(GameTestHelper helper) {
        List<ServerPlayer> players = startRtsPlayers(helper, 5);
        List<List<BlockPos>> supportGroupsRel = new ArrayList<>();

        for (int i = 0; i < players.size(); i++) {
            List<BlockPos> supportsRel = linePositions(1, 1, 2 + i * 2, 3);
            supportGroupsRel.add(supportsRel);
            for (BlockPos supportRel : supportsRel) {
                helper.setBlock(supportRel, Blocks.DIRT);
            }
            players.get(i).getInventory().setItem(0, new ItemStack(Items.STONE, supportsRel.size()));
            enqueuePlacementThroughApi(helper, players.get(i), supportsRel, "minecraft:stone", new ItemStack(Items.STONE));
        }

        helper.succeedWhen(() -> {
            for (ServerPlayer player : players) {
                RtsAPI.get().lifecycle().onPlayerTickPost(player);
            }
            for (List<BlockPos> supportsRel : supportGroupsRel) {
                for (BlockPos supportRel : supportsRel) {
                    helper.assertBlockPresent(Blocks.STONE, supportRel.above());
                }
            }
            for (ServerPlayer player : players) {
                helper.assertTrue(requireSession(helper, player).placement.placeBatchJobs.isEmpty(),
                        "批量建造完成后不应该留下玩家自己的放置任务");
            }
            stopPlayers(players);
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void fivePlayersAreaDestroyWithoutCrossTalk(GameTestHelper helper) {
        List<ServerPlayer> players = startRtsPlayers(helper, 5);
        List<List<BlockPos>> targetGroupsRel = new ArrayList<>();

        for (int i = 0; i < players.size(); i++) {
            List<BlockPos> targetsRel = linePositions(1, 1, 2 + i * 2, 3);
            targetGroupsRel.add(targetsRel);
            for (BlockPos targetRel : targetsRel) {
                helper.setBlock(targetRel, Blocks.DIRT);
            }
            RtsAPI.get().mining().areaDestroy(players.get(i), asApiPositions(helper, targetsRel),
                    (byte) 0, "", ItemStack.EMPTY, false);
        }

        helper.succeedWhen(() -> {
            for (ServerPlayer player : players) {
                tickMiningPlayer(helper, player, 1);
            }
            for (List<BlockPos> targetsRel : targetGroupsRel) {
                for (BlockPos targetRel : targetsRel) {
                    helper.assertBlockPresent(Blocks.AIR, targetRel);
                }
            }
            for (ServerPlayer player : players) {
                helper.assertTrue(requireSession(helper, player).mining.ultimineTargets.isEmpty(),
                        "范围破坏完成后不应该留下玩家自己的破坏任务");
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

        ServerPlayer player = startRtsPlayer(helper);
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        RtsAPI.get().bindings().setAutoStoreMinedDrops(player, true);
        RtsAPI.get().mining().areaDestroy(player, asApiPositions(helper, targetsRel),
                (byte) 0, "", ItemStack.EMPTY, false);

        helper.succeedWhen(() -> {
            tickMiningPlayer(helper, player, 1);
            for (BlockPos targetRel : targetsRel) {
                helper.assertBlockPresent(Blocks.AIR, targetRel);
            }
            helper.assertTrue(countChestItem(helper, chestRel, Items.DIRT) == targetsRel.size(),
                    "开启自动入库后，范围破坏掉落应该进入已链接箱子");
            helper.assertTrue(requireSession(helper, player).mining.ultimineTargets.isEmpty(),
                    "自动入库范围破坏完成后不应该留下未处理目标");
            stopPlayers(player);
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void singleLinkedChestJunkSearchAndPaginationStayCorrect(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(3, 1, 3);
        helper.setBlock(chestRel, Blocks.CHEST);
        Map<Item, Integer> expected = fillChestsWithJunk(helper, List.of(chestRel), 24);

        ServerPlayer player = startRtsPlayer(helper);
        linkChests(helper, player, List.of(chestRel));

        S2CRtsStoragePagePayload firstPage = buildStoragePage(helper, player, 0, "", 8, false, List.of());
        helper.assertTrue(firstPage.totalEntries() == expected.size(),
                "单箱多杂物统计应该保留所有不同物品种类");
        helper.assertTrue(firstPage.totalPages() == 3,
                "单箱 24 种杂物按 8 个一页应该分页为 3 页");
        assertPageCount(helper, firstPage, 8, "第一页应该只返回请求页大小的条目");
        assertTotalCount(helper, firstPage, Items.DIAMOND, expected.get(Items.DIAMOND),
                "总量统计应该包含钻石数量");

        S2CRtsStoragePagePayload secondPage = buildStoragePage(helper, player, 1, "", 8, false, List.of());
        helper.assertTrue(secondPage.page() == 1 && secondPage.totalEntries() == expected.size(),
                "翻到第二页不应该改变总条目数");
        assertPageCount(helper, secondPage, 8, "第二页应该只返回请求页大小的条目");

        S2CRtsStoragePagePayload diamondById = buildStoragePage(helper, player,
                0, itemId(Items.DIAMOND), 8, false, List.of());
        assertSingleSearchResult(helper, diamondById, Items.DIAMOND,
                "按完整 item id 搜索应该只命中钻石");

        S2CRtsStoragePagePayload diamondByLocalizedClientMatch = buildStoragePage(helper, player,
                0, "zuanshi", 8, false, List.of(itemId(Items.DIAMOND)));
        assertSingleSearchResult(helper, diamondByLocalizedClientMatch, Items.DIAMOND,
                "客户端本地化/拼音搜索命中列表应该能把钻石带回服务端分页");

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

        ServerPlayer player = startRtsPlayer(helper);
        linkChests(helper, player, chestsRel);
        RtsStorageSession session = requireSession(helper, player);

        long versionBeforeRead = session.transfer.pageDataVersion.get();
        long firstStart = System.nanoTime();
        S2CRtsStoragePagePayload allFirst = buildStoragePage(helper, player, 0, "", 12, false, List.of());
        long firstNanos = System.nanoTime() - firstStart;

        long secondStart = System.nanoTime();
        S2CRtsStoragePagePayload allSecond = buildStoragePage(helper, player, 1, "", 12, false, List.of());
        long secondNanos = System.nanoTime() - secondStart;

        helper.assertTrue(allFirst.totalEntries() == expected.size(),
                "多箱多杂物统计应该保留所有不同物品种类");
        helper.assertTrue(allSecond.page() == 1 && allSecond.totalEntries() == allFirst.totalEntries(),
                "相同搜索条件翻页应该复用同一组统计边界");
        helper.assertTrue(session.transfer.pageDataVersion.get() == versionBeforeRead,
                "纯分页和搜索读取不应该把储存数据标脏");
        helper.assertTrue(allFirst.totalPages() >= 4,
                "48 种杂物按 12 个一页应该产生多页结果");
        assertTotalCount(helper, allFirst, Items.DIAMOND, expected.get(Items.DIAMOND),
                "多箱总量统计应该包含钻石数量");
        RtsbuildingMod.LOGGER.info(
                "RTS GameTest 多杂物储存分页耗时: first={}us second={}us entries={}",
                firstNanos / 1_000L,
                secondNanos / 1_000L,
                allFirst.totalEntries());

        S2CRtsStoragePagePayload minecraftNamespace = buildStoragePage(helper, player,
                0, "@minecraft", 16, false, List.of());
        helper.assertTrue(minecraftNamespace.totalEntries() == expected.size(),
                "@minecraft 搜索应该命中所有 vanilla 杂物条目");

        S2CRtsStoragePagePayload localizedEmerald = buildStoragePage(helper, player,
                0, "lvbaoshi", 16, false, List.of(itemId(Items.EMERALD)));
        assertSingleSearchResult(helper, localizedEmerald, Items.EMERALD,
                "客户端本地化/拼音搜索命中列表应该能在多箱环境里定位绿宝石");

        long versionBeforeStore = session.transfer.pageDataVersion.get();
        player.getInventory().setItem(0, new ItemStack(Items.HONEYCOMB, 11));
        RtsAPI.get().bindings().storeHotbarSlot(player, (byte) 0);

        helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                "多箱多杂物环境下快捷栏存入后原槽位应该清空");
        helper.assertTrue(session.transfer.pageDataVersion.get() > versionBeforeStore,
                "多箱多杂物入库后应该推进储存页数据版本");
        S2CRtsStoragePagePayload honeycomb = buildStoragePage(helper, player,
                0, itemId(Items.HONEYCOMB), 16, false, List.of());
        assertSingleSearchResult(helper, honeycomb, Items.HONEYCOMB,
                "入库后的蜂巢脾应该立刻能被搜索页看到");
        helper.assertTrue(totalCount(honeycomb, Items.HONEYCOMB) == 11L,
                "入库后的蜂巢脾数量应该等于实际存入数量");

        stopPlayers(player);
        helper.succeed();
    }

    private static ServerPlayer startRtsPlayer(GameTestHelper helper) {
        return startRtsPlayer(helper, "rts-gametest", new Vec3(3.5D, 2.0D, 3.5D));
    }

    private static List<ServerPlayer> startRtsPlayers(GameTestHelper helper, int count) {
        List<ServerPlayer> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(startRtsPlayer(helper, "rts-gametest-" + i, new Vec3(3.5D + i, 2.0D, 3.5D)));
        }
        return players;
    }

    private static ServerPlayer startRtsPlayer(GameTestHelper helper, String name, Vec3 relativePos) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        Vec3 playerPos = helper.absoluteVec(relativePos);
        player.moveTo(playerPos.x, playerPos.y, playerPos.z, 0.0F, 0.0F);
        RtsCameraManager.start(player);
        helper.assertTrue(RtsCameraManager.isActive(player), "GameTest 玩家应该能进入 RTS 模式");
        requireSession(helper, player);
        return player;
    }

    private static void enqueuePlacementThroughApi(GameTestHelper helper, ServerPlayer player,
            List<BlockPos> supportsRel, String itemId, ItemStack prototype) {
        List<BlockPos> supportsAbs = supportsRel.stream()
                .map(helper::absolutePos)
                .toList();
        Vec3 rayOrigin = player.getEyePosition();
        Vec3 firstHit = Vec3.atCenterOf(supportsAbs.get(0)).add(0.0D, 0.5D, 0.0D);
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

    private static void tickMiningPlayer(GameTestHelper helper, ServerPlayer player, int ticks) {
        RtsStorageSession session = requireSession(helper, player);
        for (int i = 0; i < ticks; i++) {
            RtsMiningStateMachine.tickActiveMining(player, session);
        }
    }

    private static void linkChests(GameTestHelper helper, ServerPlayer player, List<BlockPos> chestsRel) {
        for (BlockPos chestRel : chestsRel) {
            RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(chestRel),
                    RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        }
        RtsStorageSession session = requireSession(helper, player);
        helper.assertTrue(session.linkedStorages.size() == chestsRel.size(),
                "链接后的储存数量应该等于测试摆放的箱子数");
    }

    private static Map<Item, Integer> fillChestsWithJunk(GameTestHelper helper, List<BlockPos> chestsRel, int itemCount) {
        helper.assertTrue(itemCount <= chestsRel.size() * 27,
                "测试杂物数量不能超过单箱容量总和");
        helper.assertTrue(itemCount <= JUNK_ITEMS.size(),
                "测试杂物数量不能超过预置物品列表");
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
                RtsStoragePageBuilder.sanitizeLocalizedSearchMatches(localizedSearchMatches));
        session.bdHandlerStale = true;
        session.bdFluidHandlerStale = true;

        List<LinkedHandler> itemHandlers = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> fluidHandlers = RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);
        RtsLinkedStorageResolver.registerStorageCaches(player, itemHandlers);
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
        helper.assertTrue(actual == expected,
                message + "，期望 " + expected + "，实际 " + actual);
    }

    private static void assertSingleSearchResult(GameTestHelper helper, S2CRtsStoragePagePayload payload,
            Item expectedItem, String message) {
        helper.assertTrue(payload.totalEntries() == 1,
                message + "，但结果数量为 " + payload.totalEntries());
        helper.assertTrue(payload.itemStacks().size() == 1 && payload.itemStacks().get(0).getItem() == expectedItem,
                message + "，但第一页没有目标物品");
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

    private static void assertStack(GameTestHelper helper, ItemStack stack, Item item, int count, String message) {
        helper.assertTrue(stack != null && !stack.isEmpty() && stack.getItem() == item && stack.getCount() == count,
                message + "，实际为 " + (stack == null ? "<null>" : stack));
    }

    private enum NetworkKind {
        AE2,
        RS,
        BD
    }

    private static final class InstrumentedNetworkHandler implements IItemHandler,
            ReportedCountItemHandler,
            AnySlotInsertItemHandler,
            RefreshableSnapshotHandler {
        private final NetworkKind kind;
        private final Map<Item, Long> stored = new LinkedHashMap<>();
        private List<Item> snapshot = List.of();
        private int stackReads;
        private int insertAnywhereCalls;
        private int extractAnywhereCalls;
        private int perSlotInsertCalls;
        private int perSlotExtractCalls;

        private InstrumentedNetworkHandler(NetworkKind kind) {
            this.kind = kind;
        }

        static InstrumentedNetworkHandler seeded(NetworkKind kind, Map<Item, Long> stacks) {
            InstrumentedNetworkHandler handler = new InstrumentedNetworkHandler(kind);
            handler.stored.putAll(stacks);
            handler.ensureFreshSnapshot();
            return handler;
        }

        long storedCount(Item item) {
            return this.stored.getOrDefault(item, 0L);
        }

        @Override
        public void ensureFreshSnapshot() {
            this.snapshot = this.stored.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && entry.getValue() > 0L)
                    .map(Map.Entry::getKey)
                    .toList();
        }

        @Override
        public int getSlots() {
            return this.snapshot.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            this.stackReads++;
            if (slot < 0 || slot >= this.snapshot.size()) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(this.snapshot.get(slot), 1);
        }

        @Override
        public long getReportedCount(int slot) {
            if (slot < 0 || slot >= this.snapshot.size()) {
                return 0L;
            }
            return Math.max(0L, this.stored.getOrDefault(this.snapshot.get(slot), 0L));
        }

        @Override
        public ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
            this.insertAnywhereCalls++;
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                this.stored.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
                ensureFreshSnapshot();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
            this.extractAnywhereCalls++;
            if (targetItem == null || amount <= 0) {
                return ItemStack.EMPTY;
            }
            long available = this.stored.getOrDefault(targetItem, 0L);
            if (available <= 0L) {
                return ItemStack.EMPTY;
            }
            int extracted = (int) Math.min(Integer.MAX_VALUE, Math.min(available, amount));
            if (!simulate) {
                long remaining = available - extracted;
                if (remaining <= 0L) {
                    this.stored.remove(targetItem);
                } else {
                    this.stored.put(targetItem, remaining);
                }
                ensureFreshSnapshot();
            }
            return new ItemStack(targetItem, extracted);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            this.perSlotInsertCalls++;
            throw new AssertionError(this.kind + " 测试网络不应该调用逐槽 insertItem");
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            this.perSlotExtractCalls++;
            throw new AssertionError(this.kind + " 测试网络不应该调用逐槽 extractItem");
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack != null && !stack.isEmpty();
        }
    }

    private static void stopPlayers(ServerPlayer player) {
        RtsCameraManager.stopIfActive(player);
    }

    private static void stopPlayers(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            RtsCameraManager.stopIfActive(player);
        }
    }

    private static RtsStorageSession requireSession(GameTestHelper helper, ServerPlayer player) {
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        helper.assertTrue(session != null, "RTS 模式开启后应该存在服务端会话");
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
                "测试场景需要一个可访问的箱子方块实体");
        return (ChestBlockEntity) blockEntity;
    }
}
