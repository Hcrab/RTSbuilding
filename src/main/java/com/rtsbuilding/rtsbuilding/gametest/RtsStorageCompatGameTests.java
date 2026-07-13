package com.rtsbuilding.rtsbuilding.gametest;

import com.mojang.authlib.GameProfile;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.compat.rs.RtsRsCompat;
import com.rtsbuilding.rtsbuilding.forgecompat.fml.ModList;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.api.RtsAPI;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.page.PageResult;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementExtractor;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedHandlerViews;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * 真实第三方储存网络的黑箱兼容测试。
 * <p>这些测试默认不注册，只有专门的 storage-compat 自动化脚本会启用。它们会在独立运行目录加载 RS/AE2/BD jar，搭最小真实网络，再通过 RTS 的链接、存入、分页和搜索入口观察结果，避免用假 handler 冒充玩家路径。</p>
 */
@GameTestHolder(RtsbuildingMod.MODID)
@PrefixGameTestTemplate(false)
public final class RtsStorageCompatGameTests {
    private static final String EMPTY_TEMPLATE = "gametest/empty";
    private static final int SINGLE_NETWORK_ITEM_TYPES = 192;
    private static final int MIXED_NETWORK_ITEM_TYPES = 128;

    private RtsStorageCompatGameTests() {
    }

    public static boolean isEnabled() {
        return flagEnabled(System.getProperty("rtsbuilding.storageCompatGameTests"))
                || flagEnabled(System.getenv("RTSBUILDING_STORAGE_COMPAT_GAMETESTS"));
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 480)
    public static void refinedStorageNetworkStoresAndSearchesRealItems(GameTestHelper helper) {
        if (skipUnlessEnabled(helper)) {
            return;
        }
        requireMod(helper, "refinedstorage");
        BlockPos controllerRel = new BlockPos(2, 1, 3);
        BlockPos storageRel = new BlockPos(3, 1, 3);
        setBlockById(helper, controllerRel, "refinedstorage:creative_controller");
        setBlockById(helper, storageRel, "refinedstorage:creative_storage_block");
        ServerPlayer player = startRtsPlayer(helper);

        helper.runAfterDelay(60, () -> {
            HandlerEndpoint endpoint = requireRsEndpoint(helper, player, storageRel, controllerRel);
            linkStorageAndAssert(helper, player, endpoint.rel());
            Map<Item, Long> expected = seedNetwork(helper, "RS", endpoint.handler(),
                    itemsForNetwork(Items.DIAMOND, "refinedstorage", SINGLE_NETWORK_ITEM_TYPES, 0),
                    211);
            storeHotbarThroughRts(helper, player, Items.DIAMOND, 37);
            expected.merge(Items.DIAMOND, 37L, Long::sum);
            assertLargeSearchPage(helper, player, expected, Items.DIAMOND, "zuanshi",
                    "RS 真实网络");
            stopPlayer(player);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 540)
    public static void ae2NetworkStoresAndSearchesRealItems(GameTestHelper helper) {
        if (skipUnlessEnabled(helper)) {
            return;
        }
        requireMod(helper, "ae2");
        BlockPos energyRel = new BlockPos(2, 1, 3);
        BlockPos driveRel = new BlockPos(3, 1, 3);
        setBlockById(helper, energyRel, "ae2:creative_energy_cell");
        setBlockById(helper, driveRel, "ae2:drive");
        insertAe2StorageCells(helper, driveRel, 4);
        ServerPlayer player = startRtsPlayer(helper);

        helper.runAfterDelay(140, () -> {
            HandlerEndpoint endpoint = requireAe2Endpoint(helper, player, driveRel, energyRel);
            linkStorageAndAssert(helper, player, endpoint.rel());
            Map<Item, Long> expected = seedNetwork(helper, "AE2", endpoint.handler(),
                    itemsForNetwork(Items.EMERALD, "ae2", SINGLE_NETWORK_ITEM_TYPES, 97),
                    233);
            storeHotbarThroughRts(helper, player, Items.EMERALD, 41);
            expected.merge(Items.EMERALD, 41L, Long::sum);
            assertLargeSearchPage(helper, player, expected, Items.EMERALD, "lvbaoshi",
                    "AE2 真实网络");
            stopPlayer(player);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 480)
    public static void beyondDimensionsNetworkStoresAndSearchesRealItems(GameTestHelper helper) {
        if (skipUnlessEnabled(helper)) {
            return;
        }
        requireMod(helper, "beyonddimensions");
        ServerPlayer player = startRtsPlayer(helper);
        helper.assertTrue(RtsBdCompat.ensurePrimaryNetworkForTesting(player, 2_000_000L, 1_000_000),
                "BD 测试玩家应该能通过 BD 公开 API 创建真实主网络");

        IItemHandler handler = RtsBdCompat.createNetworkItemHandler(player);
        helper.assertTrue(handler != null, "BD 主网络应该能被 RTS 包装为真实 item handler");
        Map<Item, Long> expected = seedNetwork(helper, "BD", handler,
                itemsForNetwork(Items.AMETHYST_SHARD, "beyonddimensions", SINGLE_NETWORK_ITEM_TYPES, 194),
                257);
        storeHotbarThroughRts(helper, player, Items.AMETHYST_SHARD, 43);
        expected.merge(Items.AMETHYST_SHARD, 43L, Long::sum);
        assertLargeSearchPage(helper, player, expected, Items.AMETHYST_SHARD, "zishuijing",
                "BD 真实网络");
        stopPlayer(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 480)
    public static void refinedStorageNetworkExtractsSelectedBlockForPlacement(GameTestHelper helper) {
        if (skipUnlessEnabled(helper)) {
            return;
        }
        requireMod(helper, "refinedstorage");
        BlockPos controllerRel = new BlockPos(2, 1, 3);
        BlockPos storageRel = new BlockPos(3, 1, 3);
        setBlockById(helper, controllerRel, "refinedstorage:creative_controller");
        setBlockById(helper, storageRel, "refinedstorage:creative_storage_block");
        ServerPlayer player = startRtsPlayer(helper);

        helper.runAfterDelay(60, () -> {
            HandlerEndpoint endpoint = requireRsEndpoint(helper, player, storageRel, controllerRel);
            linkStorageAndAssert(helper, player, endpoint.rel());
            insertLargeAmount(endpoint.handler(), Items.DIRT, 1_000_000);
            assertPlacementExtractorPullsLargeDirt(helper, player, "RS 真实网络");
            stopPlayer(player);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 540)
    public static void ae2NetworkExtractsSelectedBlockForPlacement(GameTestHelper helper) {
        if (skipUnlessEnabled(helper)) {
            return;
        }
        requireMod(helper, "ae2");
        BlockPos energyRel = new BlockPos(2, 1, 3);
        BlockPos driveRel = new BlockPos(3, 1, 3);
        setBlockById(helper, energyRel, "ae2:creative_energy_cell");
        setBlockById(helper, driveRel, "ae2:drive");
        insertAe2StorageCells(helper, driveRel, 4);
        ServerPlayer player = startRtsPlayer(helper);

        helper.runAfterDelay(140, () -> {
            HandlerEndpoint endpoint = requireAe2Endpoint(helper, player, driveRel, energyRel);
            linkStorageAndAssert(helper, player, endpoint.rel());
            insertLargeAmount(endpoint.handler(), Items.DIRT, 1_000_000);
            assertPlacementExtractorPullsLargeDirt(helper, player, "AE2 真实网络");
            stopPlayer(player);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 480)
    public static void beyondDimensionsNetworkExtractsSelectedBlockForPlacement(GameTestHelper helper) {
        if (skipUnlessEnabled(helper)) {
            return;
        }
        requireMod(helper, "beyonddimensions");
        ServerPlayer player = startRtsPlayer(helper);
        helper.assertTrue(RtsBdCompat.ensurePrimaryNetworkForTesting(player, 2_000_000L, 1_000_000),
                "BD 提取测试玩家应该能通过 BD 公开 API 创建真实主网络");

        IItemHandler handler = RtsBdCompat.createNetworkItemHandler(player);
        helper.assertTrue(handler != null, "BD 主网络应该能被 RTS 包装为真实 item handler");
        insertLargeAmount(handler, Items.DIRT, 1_000_000);
        assertPlacementExtractorPullsLargeDirt(helper, player, "BD 真实网络");
        stopPlayer(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 700)
    public static void mixedRsAe2BdNetworksSearchAcrossLargeJunk(GameTestHelper helper) {
        if (skipUnlessEnabled(helper)) {
            return;
        }
        requireMod(helper, "refinedstorage");
        requireMod(helper, "ae2");
        requireMod(helper, "beyonddimensions");

        BlockPos rsControllerRel = new BlockPos(1, 1, 3);
        BlockPos rsStorageRel = new BlockPos(2, 1, 3);
        BlockPos aeEnergyRel = new BlockPos(5, 1, 3);
        BlockPos aeDriveRel = new BlockPos(6, 1, 3);
        setBlockById(helper, rsControllerRel, "refinedstorage:creative_controller");
        setBlockById(helper, rsStorageRel, "refinedstorage:creative_storage_block");
        setBlockById(helper, aeEnergyRel, "ae2:creative_energy_cell");
        setBlockById(helper, aeDriveRel, "ae2:drive");
        insertAe2StorageCells(helper, aeDriveRel, 4);

        ServerPlayer player = startRtsPlayer(helper);
        helper.assertTrue(RtsBdCompat.ensurePrimaryNetworkForTesting(player, 3_000_000L, 1_000_000),
                "混合网络测试需要真实 BD 主网络");

        helper.runAfterDelay(150, () -> {
            Map<Item, Long> expected = new LinkedHashMap<>();
            HandlerEndpoint rsEndpoint = requireRsEndpoint(helper, player, rsStorageRel, rsControllerRel);
            HandlerEndpoint aeEndpoint = requireAe2Endpoint(helper, player, aeDriveRel, aeEnergyRel);
            linkStorageAndAssert(helper, player, rsEndpoint.rel());
            linkStorageAndAssert(helper, player, aeEndpoint.rel());
            mergeExpected(expected, seedNetwork(helper, "RS mixed", rsEndpoint.handler(),
                    itemsForNetwork(Items.DIAMOND, "refinedstorage", MIXED_NETWORK_ITEM_TYPES, 0),
                    173));
            mergeExpected(expected, seedNetwork(helper, "AE2 mixed", aeEndpoint.handler(),
                    itemsForNetwork(Items.EMERALD, "ae2", MIXED_NETWORK_ITEM_TYPES, 131),
                    191));
            IItemHandler bdHandler = RtsBdCompat.createNetworkItemHandler(player);
            helper.assertTrue(bdHandler != null, "混合网络测试需要 RTS 能打开 BD item handler");
            mergeExpected(expected, seedNetwork(helper, "BD mixed", bdHandler,
                    itemsForNetwork(Items.AMETHYST_SHARD, "beyonddimensions", MIXED_NETWORK_ITEM_TYPES, 262),
                    209));

            long start = System.nanoTime();
            S2CRtsStoragePagePayload all = buildFreshStoragePage(helper, player, 0, "", 32, List.of());
            long allNanos = System.nanoTime() - start;
            helper.assertTrue(all.totalEntries() == expected.size(),
                    "RS+AE2+BD 混合多杂物统计应该等于三个真实网络的去重物品种类");

            S2CRtsStoragePagePayload diamond = buildStoragePage(helper, player,
                    0, "zuanshi", 32, List.of(itemId(Items.DIAMOND)));
            assertSingleSearchResult(helper, diamond, Items.DIAMOND, expected.get(Items.DIAMOND),
                    "混合网络里拼音/本地化搜索应该能定位钻石");
            assertNamespaceSearch(helper, player, expected, "ae2");
            assertNamespaceSearch(helper, player, expected, "beyonddimensions");
            RtsbuildingMod.LOGGER.info(
                    "RTS StorageCompat 混合网络搜索: entries={} firstPage={} all={}us",
                    all.totalEntries(),
                    all.itemStacks().size(),
                    allNanos / 1_000L);

            stopPlayer(player);
            helper.succeed();
        });
    }

    private record HandlerEndpoint(BlockPos rel, IItemHandler handler) {
    }

    private static HandlerEndpoint requireRsEndpoint(GameTestHelper helper, ServerPlayer player, BlockPos... relPositions) {
        for (BlockPos rel : relPositions) {
            IItemHandler handler = RtsRsCompat.createNetworkItemHandler(player, helper.absolutePos(rel));
            if (handler != null) {
                return new HandlerEndpoint(rel, handler);
            }
        }
        helper.assertTrue(false, "RS 网络节点应该能被 RTS 识别为真实网络 handler");
        return null;
    }

    private static HandlerEndpoint requireAe2Endpoint(GameTestHelper helper, ServerPlayer player, BlockPos... relPositions) {
        for (BlockPos rel : relPositions) {
            IItemHandler handler = RtsAe2Compat.createNetworkItemHandler(player, helper.absolutePos(rel));
            if (handler != null) {
                return new HandlerEndpoint(rel, handler);
            }
        }
        helper.assertTrue(false, "AE2 网络节点应该能被 RTS 识别为真实网络 handler");
        return null;
    }

    private static void linkStorageAndAssert(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
        RtsStorageSession session = requireSession(helper, player);
        int before = session.linkedStorages.size();
        RtsAPI.get().bindings().linkStorage(player, helper.absolutePos(rel),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        helper.assertTrue(session.linkedStorages.size() > before,
                "RTS 应该能链接真实网络 endpoint: " + rel);
    }

    private static Map<Item, Long> seedNetwork(GameTestHelper helper, String label,
            IItemHandler handler, List<Item> items, int baseAmount) {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int amount = baseAmount + (i % 23) * 17;
            insertAmount(handler, item, amount);
        }
        Map<Item, Long> visible = snapshotVisibleCounts(handler);
        int expectedMinimum = Math.max(1, items.size() - 2);
        helper.assertTrue(visible.size() >= expectedMinimum,
                label + " real network should expose almost all inserted junk types, expected at least "
                        + expectedMinimum + ", actual " + visible.size());
        helper.assertTrue(visible.containsKey(items.get(0)),
                label + " real network should expose the localized search anchor " + itemId(items.get(0)));
        return visible;
    }

    private static Map<Item, Long> snapshotVisibleCounts(IItemHandler handler) {
        Map<Item, Long> counts = new LinkedHashMap<>();
        if (handler == null) {
            return counts;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            long count = handler instanceof ReportedCountItemHandler reported
                    ? reported.getReportedCount(slot)
                    : stack.getCount();
            if (count > 0L) {
                counts.merge(stack.getItem(), count, Long::sum);
            }
        }
        return counts;
    }

    private static void insertAmount(IItemHandler handler, Item item, int amount) {
        int remaining = amount;
        int maxStack = Math.max(1, item.getDefaultInstance().getMaxStackSize());
        while (remaining > 0) {
            int count = Math.min(maxStack, remaining);
            ItemStack remainder = RtsLinkedHandlerViews.insertItemAnywhere(
                    handler, new ItemStack(item, count), false);
            int inserted = count - (remainder == null || remainder.isEmpty() ? 0 : remainder.getCount());
            if (inserted <= 0) {
                throw new IllegalStateException("真实网络拒绝插入测试物品: " + itemId(item));
            }
            remaining -= inserted;
        }
    }

    private static void storeHotbarThroughRts(GameTestHelper helper, ServerPlayer player, Item item, int count) {
        player.getInventory().setItem(0, new ItemStack(item, count));
        RtsAPI.get().bindings().storeHotbarSlot(player, (byte) 0);
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                "RTS 快捷栏存入真实网络后，玩家原槽位应该清空");
    }

    private static void insertLargeAmount(IItemHandler handler, Item item, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int count = Math.min(65_536, remaining);
            ItemStack stack = new ItemStack(item, 1);
            stack.setCount(count);
            ItemStack remainder = RtsLinkedHandlerViews.insertItemAnywhere(handler, stack, false);
            int inserted = count - (remainder == null || remainder.isEmpty() ? 0 : remainder.getCount());
            if (inserted <= 0) {
                throw new IllegalStateException("真实网络拒绝大数量插入测试物品: " + itemId(item));
            }
            remaining -= inserted;
        }
    }

    private static void assertPlacementExtractorPullsLargeDirt(GameTestHelper helper, ServerPlayer player,
            String label) {
        long before = visibleCountFromResolvedHandlers(helper, player, Items.DIRT);
        helper.assertTrue(before >= 1_000_000L,
                label + " 应该已经包含大数量泥土，实际 " + before);

        RtsStorageSession session = requireSession(helper, player);
        session.bdHandlerStale = true;
        session.bdFluidHandlerStale = true;
        List<LinkedHandler> linkedHandlers = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        helper.assertTrue(!linkedHandlers.isEmpty(), label + " 应该能被 RTS 解析为 linked storage");
        RtsLinkedStorageResolver.registerStorageCaches(player, linkedHandlers);
        RtsStorageTickService.INSTANCE.forceRefresh(player);

        ItemStack extracted = RtsPlacementExtractor.extractSelectedFromNetwork(
                RtsLinkedStorageResolver.itemHandlersForExtract(linkedHandlers),
                player,
                Items.DIRT,
                new ItemStack(Items.DIRT));

        helper.assertTrue(!extracted.isEmpty() && extracted.getItem() == Items.DIRT && extracted.getCount() == 1,
                label + " placement extractor 应该从大数量网络里抽出 1 个泥土，实际为 " + extracted);
        long after = visibleCountFromResolvedHandlers(helper, player, Items.DIRT);
        helper.assertTrue(after == before - 1L,
                label + " 抽取后泥土计数应该减少 1，before=" + before + " after=" + after);
    }

    private static long visibleCountFromResolvedHandlers(GameTestHelper helper, ServerPlayer player, Item item) {
        RtsStorageSession session = requireSession(helper, player);
        session.bdHandlerStale = true;
        session.bdFluidHandlerStale = true;
        List<LinkedHandler> linkedHandlers = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        helper.assertTrue(!linkedHandlers.isEmpty(), "RTS 应该能解析用于计数的 linked storage");
        long total = 0L;
        for (IItemHandler handler : RtsLinkedStorageResolver.itemHandlersForExtract(linkedHandlers)) {
            total += visibleCount(handler, item);
        }
        return total;
    }

    private static long visibleCount(IItemHandler handler, Item item) {
        long total = 0L;
        for (int slot = 0; handler != null && slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getItem() != item) {
                continue;
            }
            long count = handler instanceof ReportedCountItemHandler reported
                    ? reported.getReportedCount(slot)
                    : stack.getCount();
            total += Math.max(0L, count);
        }
        return total;
    }

    private static void assertLargeSearchPage(GameTestHelper helper, ServerPlayer player,
            Map<Item, Long> expected, Item localizedAnchor, String pinyinQuery, String label) {
        long allStart = System.nanoTime();
        S2CRtsStoragePagePayload all = buildFreshStoragePage(helper, player, 0, "", 24, List.of());
        long allNanos = System.nanoTime() - allStart;
        helper.assertTrue(all.totalEntries() == expected.size(),
                label + " 多杂物统计应该保留所有不同物品种类，期望 "
                        + expected.size() + "，实际 " + all.totalEntries());
        helper.assertTrue(all.totalPages() >= 4,
                label + " 大库存应该产生多页结果");
        for (Map.Entry<Item, Long> entry : expected.entrySet()) {
            assertTotalCount(helper, all, entry.getKey(), entry.getValue(),
                    label + " 总量统计不应丢失 " + itemId(entry.getKey()));
        }

        long searchStart = System.nanoTime();
        S2CRtsStoragePagePayload localized = buildStoragePage(helper, player,
                0, pinyinQuery, 24, List.of(itemId(localizedAnchor)));
        long searchNanos = System.nanoTime() - searchStart;
        assertSingleSearchResult(helper, localized, localizedAnchor, expected.get(localizedAnchor),
                label + " 拼音/本地化搜索应该只命中锚点物品");
        RtsbuildingMod.LOGGER.info(
                "RTS StorageCompat {}: entries={} all={}us localized={}us",
                label,
                all.totalEntries(),
                allNanos / 1_000L,
                searchNanos / 1_000L);
    }

    private static void assertNamespaceSearch(GameTestHelper helper, ServerPlayer player,
            Map<Item, Long> expected, String namespace) {
        long expectedEntries = expected.keySet().stream()
                .filter(item -> namespace.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace()))
                .count();
        if (expectedEntries <= 0L) {
            return;
        }
        S2CRtsStoragePagePayload payload = buildStoragePage(helper, player,
                0, "@" + namespace, 32, List.of());
        helper.assertTrue(payload.totalEntries() == expectedEntries,
                "@" + namespace + " 搜索应该只返回该命名空间的真实网络条目");
    }

    private static S2CRtsStoragePagePayload buildStoragePage(GameTestHelper helper, ServerPlayer player,
            int requestedPage, String search, int pageSize, List<String> localizedSearchMatches) {
        return buildStoragePageInternal(helper, player, requestedPage, search, pageSize, localizedSearchMatches, false);
    }

    private static S2CRtsStoragePagePayload buildFreshStoragePage(GameTestHelper helper, ServerPlayer player,
            int requestedPage, String search, int pageSize, List<String> localizedSearchMatches) {
        return buildStoragePageInternal(helper, player, requestedPage, search, pageSize, localizedSearchMatches, true);
    }

    private static S2CRtsStoragePagePayload buildStoragePageInternal(GameTestHelper helper, ServerPlayer player,
            int requestedPage, String search, int pageSize, List<String> localizedSearchMatches,
            boolean refreshStorageSnapshot) {
        RtsStorageSession session = requireSession(helper, player);
        session.browser.search = search == null ? "" : search;
        session.browser.category = RtsStoragePageBuilder.normalizeCategory("all");
        session.browser.sort = RtsStorageSort.NAME;
        session.browser.ascending = true;
        session.browser.pageSize = RtsStoragePageBuilder.sanitizePageSize(pageSize);
        session.browser.pinyinSearchEnabled = true;
        session.browser.localizedSearchMatches.clear();
        session.browser.localizedSearchMatches.addAll(
                RtsStoragePageBuilder.sanitizeLocalizedSearchMatches(localizedSearchMatches));
        if (refreshStorageSnapshot) {
            session.bdHandlerStale = true;
            session.bdFluidHandlerStale = true;
        }

        List<LinkedHandler> itemHandlers = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> fluidHandlers = RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);
        RtsLinkedStorageResolver.registerStorageCaches(player, itemHandlers);
        if (refreshStorageSnapshot) {
            RtsStorageTickService.INSTANCE.forceRefresh(player);
        }
        PageResult result = RtsStoragePageBuilder.build(player, session,
                requestedPage, session.browser.pageSize, itemHandlers, fluidHandlers);
        session.browser.page = result.safePage();
        return result.payload();
    }

    private static List<Item> itemsForNetwork(Item anchor, String preferredNamespace, int count, int offset) {
        LinkedHashSet<Item> out = new LinkedHashSet<>();
        addCandidate(out, anchor);
        addNamespaceItems(out, preferredNamespace, 24);
        addNamespaceItems(out, "minecraft", 36);

        List<Item> all = allStackableItems();
        for (int i = 0; out.size() < count && i < all.size(); i++) {
            addCandidate(out, all.get((offset + i) % all.size()));
        }
        return new ArrayList<>(out).subList(0, Math.min(count, out.size()));
    }

    private static void addNamespaceItems(Set<Item> out, String namespace, int limit) {
        int added = 0;
        for (Item item : allStackableItems()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null && namespace.equals(id.getNamespace()) && addCandidate(out, item)) {
                added++;
                if (added >= limit) {
                    return;
                }
            }
        }
    }

    private static boolean addCandidate(Set<Item> out, Item item) {
        if (item == null || item == Items.AIR) {
            return false;
        }
        ItemStack stack = item.getDefaultInstance();
        if (stack.isEmpty() || stack.getMaxStackSize() <= 1 || stack.isDamageableItem()) {
            return false;
        }
        return out.add(item);
    }

    private static List<Item> allStackableItems() {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (item != Items.AIR && !stack.isEmpty() && stack.getMaxStackSize() > 1 && !stack.isDamageableItem()) {
                items.add(item);
            }
        }
        items.sort((left, right) -> itemId(left).compareTo(itemId(right)));
        return items;
    }

    private static ServerPlayer startRtsPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "rts-storage-compat"));
        Vec3 playerPos = helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D));
        player.moveTo(playerPos.x, playerPos.y, playerPos.z, 0.0F, 0.0F);
        RtsCameraManager.start(player);
        helper.assertTrue(RtsCameraManager.isActive(player), "Storage compat 测试玩家应该能进入 RTS 模式");
        requireSession(helper, player);
        return player;
    }

    private static void stopPlayer(ServerPlayer player) {
        RtsCameraManager.stopIfActive(player);
    }

    private static RtsStorageSession requireSession(GameTestHelper helper, ServerPlayer player) {
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        helper.assertTrue(session != null, "RTS 模式开启后应该存在服务端会话");
        return session;
    }

    private static void setBlockById(GameTestHelper helper, BlockPos rel, String idText) {
        ResourceLocation id = ResourceLocation.tryParse(idText);
        helper.assertTrue(id != null && BuiltInRegistries.BLOCK.containsKey(id),
                "测试需要已注册方块: " + idText);
        Block block = BuiltInRegistries.BLOCK.get(id);
        helper.assertTrue(block != Blocks.AIR, "测试方块不能是空 " + idText);
        helper.setBlock(rel, block);
    }

    private static void insertIntoBlockItemHandler(GameTestHelper helper, BlockPos rel, ItemStack stack) {
        BlockEntity blockEntity = helper.getBlockEntity(rel);
        helper.assertTrue(blockEntity != null, "测试方块需要方块实体用于插入储存组件");
        IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        helper.assertTrue(handler != null, "测试方块实体需要暴露 item handler");
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, false);
        }
        helper.assertTrue(remaining.isEmpty(), "测试储存组件应该能插入目标方块实体");
    }

    private static void insertAe2StorageCells(GameTestHelper helper, BlockPos driveRel, int count) {
        for (int i = 0; i < count; i++) {
            insertIntoBlockItemHandler(helper, driveRel, itemStackById("ae2:item_storage_cell_64k", 1));
        }
    }

    private static ItemStack itemStackById(String idText, int count) {
        ResourceLocation id = ResourceLocation.tryParse(idText);
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("测试需要已注册物品: " + idText);
        }
        return new ItemStack(item, count);
    }

    private static void assertTotalCount(GameTestHelper helper, S2CRtsStoragePagePayload payload,
            Item item, long expected, String message) {
        long actual = totalCount(payload, item);
        helper.assertTrue(actual == expected,
                message + "，期望 " + expected + "，实际 " + actual);
    }

    private static void assertSingleSearchResult(GameTestHelper helper, S2CRtsStoragePagePayload payload,
            Item expectedItem, Long expectedCount, String message) {
        helper.assertTrue(payload.totalEntries() == 1,
                message + "，但结果数量为 " + payload.totalEntries());
        helper.assertTrue(payload.itemStacks().size() == 1 && payload.itemStacks().get(0).getItem() == expectedItem,
                message + "，但第一页没有目标物品");
        if (expectedCount != null) {
            assertTotalCount(helper, payload, expectedItem, expectedCount, message + "，数量也应正确");
        }
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

    private static void mergeExpected(Map<Item, Long> target, Map<Item, Long> source) {
        for (Map.Entry<Item, Long> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
    }

    private static String itemId(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "minecraft:air" : id.toString();
    }

    private static void requireMod(GameTestHelper helper, String modId) {
        helper.assertTrue(ModList.get().isLoaded(modId),
                "Storage compat GameTest 需要加载真实模 " + modId);
    }

    private static boolean skipUnlessEnabled(GameTestHelper helper) {
        if (!isEnabled()) {
            helper.succeed();
            return true;
        }
        return false;
    }

    private static boolean flagEnabled(String value) {
        return value != null && (value.equalsIgnoreCase("true") || value.equals("1")
                || value.equalsIgnoreCase("yes"));
    }
}
