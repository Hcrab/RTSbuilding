package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.RtsStorageUiPayloads;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageFluids;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsAggregateStorage;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.*;

/**
 * 储存浏览器页面构建器核心，从会话和链接存储快照构建只读的储存浏览器页面。
 *
 * <p>这是页面系统的核心编排器，负责：
 * <ul>
 *   <li><b>页面构建</b>（{@link #build}）— 从链接处理器、聚合缓存、玩家背包中收集物品计数，
 *   构建精确条目、流体条目、类别列表，执行搜索过滤和排序，组装完整的
 *   {@link com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload}</li>
 *   <li><b>缓存集成</b>— 先检查 LRU 缓存（{@link RtsPageCache}），命中时直接返回缓存结果；
 *   缓存未命中或 dataVersion 过期时执行完全重建并更新缓存</li>
 *   <li><b>快速路径</b>— 优先使用 {@link com.rtsbuilding.rtsbuilding.server.storage.cache.RtsAggregateStorage}
 *   聚合缓存加速大量物品的统计，回退到逐处理器、逐槽位扫描</li>
 * </ul>
 *
 * <p>数据包组装委托给 {@link RtsPagePayloadFactory}，
 * 搜索/排序/类别逻辑委托给 {@link RtsPageSharedHelpers}。
 */
public final class RtsPageCore {

    private RtsPageCore() {
    }

    /**
     * 移除玩家的缓存页面数据，以便在禁用 RTS 或退出时 GC 可以回收内存。
     */
    public static void clearCache(UUID playerUuid) {
        RtsPageCache.INSTANCE.remove(playerUuid);
    }

    public static PageResult build(
            ServerPlayer player,
            RtsStorageSession session,
            int requestedPage,
            int requestedPageSize,
            List<LinkedHandler> activeHandlers,
            List<LinkedFluidHandler> activeFluidHandlers) {
        List<LinkedHandler> itemHandlers = activeHandlers == null ? List.of() : activeHandlers;
        List<LinkedFluidHandler> fluidHandlers = activeFluidHandlers == null ? List.of() : activeFluidHandlers;
        boolean includePlayerMainInventory = RtsPageSharedHelpers.shouldIncludePlayerMainInventoryInStorageView(player, session);
        LinkedRefPayload linkedRefs = RtsPagePayloadFactory.buildLinkedRefPayload(player, session);
        List<Long> linkedPackedPositions = linkedRefs.positions();
        if (session.linkedStorageInfo.isEmpty()
                && itemHandlers.isEmpty()
                && fluidHandlers.isEmpty()
                && !hasPositiveInternalFluid(session)
                && !includePlayerMainInventory) {
            return new PageResult(RtsPagePayloadFactory.buildEmpty(player, session), 0);
        }

        // ── Page cache check: avoid O(n log n) sort + filter rebuild on pure pagination ──
        RtsPageCache.CachedPageKey cacheKey = new RtsPageCache.CachedPageKey(
                session.browser.search, session.browser.sort, session.browser.category, session.browser.ascending,
                requestedPageSize, session.browser.pinyinSearchEnabled, includePlayerMainInventory);
        RtsPageCache.CachedPage cached = RtsPageCache.INSTANCE.get(player.getUUID());

        final Map<String, Long> counts;
        final Map<String, Long> namespaceTotals;
        final List<String> categories;
        final List<Entry> sortedEntries;
        final List<FluidEntry> sortedFluidEntries;
        final int totalEntries;

        boolean cacheHit = cached != null
                && cached.key().equals(cacheKey)
                && cached.dataVersion() == session.transfer.pageDataVersion.get();

        if (cacheHit) {
            counts = cached.counts();
            namespaceTotals = cached.namespaceTotals();
            categories = cached.categories();
            sortedEntries = cached.sortedEntries();
            sortedFluidEntries = cached.sortedFluidEntries();
            totalEntries = sortedEntries.size();
        } else {
            // ── Full build: counts → exactEntries → fluid → categories → sort → filter ──
            Map<String, Long> localCounts = new HashMap<>();
            List<Entry> exactEntries = new ArrayList<>();
            Map<String, Long> localNamespaceTotals = new HashMap<>();

            // Fast path: build from slot cache
            RtsAggregateStorage aggregate = RtsStorageTickService.INSTANCE.getStorage(player);
            boolean usedCache = false;
            if (aggregate != null && !aggregate.isEmpty()) {
                Map<ItemStack, Long> componentEntries = new HashMap<>();
                aggregate.getAvailableItems(localCounts);
                aggregate.getAvailableEntries(componentEntries);
                if (!componentEntries.isEmpty()) {
                    for (var entry : componentEntries.entrySet()) {
                        ItemStack prototype = entry.getKey();
                        long count = entry.getValue();
                        ResourceLocation id = BuiltInRegistries.ITEM.getKey(prototype.getItem());
                        if (id == null) continue;
                        mergeExactEntry(exactEntries, prototype, count);
                        mergeCount(localNamespaceTotals, id.getNamespace(), count);
                    }
                    usedCache = true;
                }
            }

            if (!usedCache) {
                for (LinkedHandler linked : itemHandlers) {
                    IItemHandler handler = linked.handler();
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStackInSlot(i);
                        if (stack.isEmpty()) continue;
                        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                        if (id == null) continue;
                        long reportedCount = getHandlerReportedCount(handler, i, stack);
                        mergeCount(localCounts, id.toString(), reportedCount);
                        mergeExactEntry(exactEntries, stack, reportedCount);
                        mergeCount(localNamespaceTotals, id.getNamespace(), reportedCount);
                    }
                }
            }
            if (includePlayerMainInventory) {
                accumulatePlayerMainInventoryCounts(player, localCounts, localNamespaceTotals);
                accumulatePlayerMainInventoryEntries(player, exactEntries);
            }

            // Build fluid entries
            Map<String, Long> fluidAmounts = new HashMap<>();
            Map<String, Long> fluidCapacities = new HashMap<>();
            for (var entry : session.sessionFlags.internalFluidMb.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0L) continue;
                mergeCount(fluidAmounts, entry.getKey(), entry.getValue());
            }
            for (LinkedFluidHandler linked : fluidHandlers) {
                IFluidHandler handler = linked.handler();
                for (int tank = 0; tank < handler.getTanks(); tank++) {
                    FluidStack fluid = handler.getFluidInTank(tank);
                    if (fluid.isEmpty()) continue;
                    ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
                    if (id == null) continue;
                    String fluidId = id.toString();
                    mergeCount(fluidAmounts, fluidId, fluid.getAmount());
                    mergeCount(fluidCapacities, fluidId, Math.max(0, handler.getTankCapacity(tank)));
                }
            }

            // 从 AE2 等网络节点收集流体（仅收集一次，跳过已被常规 handler 计数的流体 ID）
            Set<String> fluidCountedByRegular = new HashSet<>(fluidAmounts.keySet());
            boolean ae2FluidCollected = false;
            for (var ref : session.linkedStorageInfo.getAll()) {
                if (ref == null || ref.pos() == null) continue;
                if (!player.serverLevel().dimension().equals(ref.dimension())) continue;
                if (ae2FluidCollected) break;
                if (RtsAe2Compat.isPositionAE2Node(player, ref.pos())) {
                    RtsAe2Compat.collectNetworkFluids(player, ref.pos(), fluidAmounts, fluidCapacities,
                            fluidCountedByRegular);
                    ae2FluidCollected = true;
                }
            }

            long internalFluidCapacityMb = RtsStorageFluids.internalFluidCapacityMb(player);
            for (String fluidId : fluidAmounts.keySet()) {
                mergeCount(fluidCapacities, fluidId, internalFluidCapacityMb);
                ResourceLocation rl = ResourceLocation.tryParse(fluidId);
                if (rl != null) {
                    mergeCount(localNamespaceTotals, rl.getNamespace(), fluidAmounts.getOrDefault(fluidId, 0L));
                }
            }

            // Build categories
            Map<String, Set<String>> itemTabKeys = new HashMap<>();
            Map<String, Set<String>> modTabKeys = new HashMap<>();
            if (!localCounts.isEmpty()) {
                boolean operatorTabs = player.canUseGameMasterBlocks();
                if (RtsPageCreativeTabIndexer.ensureCreativeTabContents(player)) {
                    RtsPageCreativeTabIndexer.buildItemTabMapping(localCounts, itemTabKeys, modTabKeys, operatorTabs);
                }
            }

            List<String> nsList = new ArrayList<>(localNamespaceTotals.keySet());
            nsList.sort(RtsPageSharedHelpers::compareNamespace);

            List<String> localCategories = new ArrayList<>();
            localCategories.add(RtsPageSharedHelpers.CATEGORY_ALL);
            for (String ns : nsList) {
                localCategories.add(RtsPageSharedHelpers.encodeModCategory(ns));
                List<String> tabs = new ArrayList<>(modTabKeys.getOrDefault(ns, Set.of()));
                tabs.sort(RtsPageSharedHelpers::compareTabKey);
                for (String tabKey : tabs) {
                    localCategories.add(RtsPageSharedHelpers.encodeTabCategory(ns, tabKey));
                }
            }

            // Filter and sort entries
            CategorySelection selectedCategory = RtsPageSharedHelpers.parseCategorySelection(session.browser.category);
            if (!RtsPageSharedHelpers.isValidCategorySelection(selectedCategory, localCategories)) {
                session.browser.category = RtsPageSharedHelpers.CATEGORY_ALL;
                selectedCategory = CategorySelection.all();
            }

            String query = session.browser.search.toLowerCase(Locale.ROOT).trim();
            List<Entry> entries = new ArrayList<>();
            for (Entry exactEntry : exactEntries) {
                String id = exactEntry.itemId();
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (!RtsPageSharedHelpers.matchesSearchQuery(
                        rl, id, exactEntry.label(), query,
                        session.browser.pinyinSearchEnabled, session.browser.localizedSearchMatches)) {
                    continue;
                }
                Set<String> tabs = itemTabKeys.getOrDefault(id, Set.of());
                if (!selectedCategory.matches(exactEntry.namespace(), tabs)) {
                    continue;
                }
                entries.add(exactEntry);
            }

            List<FluidEntry> fluidEntries = new ArrayList<>();
            for (var e : fluidAmounts.entrySet()) {
                String id = e.getKey();
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (!RtsPageSharedHelpers.matchesSearchQuery(
                        rl, id, null, query, session.browser.pinyinSearchEnabled, session.browser.localizedSearchMatches)) {
                    continue;
                }
                String namespace = rl == null ? "unknown" : rl.getNamespace();
                if (selectedCategory.isCreativeTab() || !selectedCategory.matches(namespace, Set.of())) {
                    continue;
                }
                long amount = Math.max(0L, e.getValue());
                long capacity = Math.max(amount, fluidCapacities.getOrDefault(id, internalFluidCapacityMb));
                fluidEntries.add(new FluidEntry(id, namespace, rl == null ? id : rl.getPath(), amount, capacity));
            }

            entries.sort(RtsPageSharedHelpers.entryComparator(session.browser.sort, session.browser.ascending));
            fluidEntries.sort(RtsPageSharedHelpers.fluidComparator(session.browser.sort, session.browser.ascending));

            counts = localCounts;
            namespaceTotals = localNamespaceTotals;
            categories = localCategories;
            sortedEntries = entries;
            sortedFluidEntries = fluidEntries;
            totalEntries = entries.size();

            // Update page cache
            RtsPageCache.INSTANCE.put(player.getUUID(), new RtsPageCache.CachedPage(
                    cacheKey, session.transfer.pageDataVersion.get(),
                    sortedEntries, sortedFluidEntries,
                    counts, namespaceTotals, categories));
        }

        int pageSize = RtsPageSharedHelpers.sanitizePageSize(requestedPageSize);
        int totalPages = Math.max(1, (totalEntries + pageSize - 1) / pageSize);
        int safePage = Math.max(0, Math.min(requestedPage, totalPages - 1));
        int from = safePage * pageSize;
        int to = Math.min(from + pageSize, totalEntries);

        List<ItemStack> itemStacks = new ArrayList<>();
        List<Long> itemCounts = new ArrayList<>();
        for (int i = from; i < to; i++) {
            Entry e = sortedEntries.get(i);
            itemStacks.add(e.stack().copy());
            itemCounts.add(e.count());
        }

        List<String> totalItemIds = new ArrayList<>(counts.size());
        List<Long> totalItemCounts = new ArrayList<>(counts.size());
        for (var entry : counts.entrySet()) {
            totalItemIds.add(entry.getKey());
            totalItemCounts.add(entry.getValue());
        }

        List<String> fluidIds = new ArrayList<>(sortedFluidEntries.size());
        List<Long> fluidAmountList = new ArrayList<>(sortedFluidEntries.size());
        List<Long> fluidCapacityList = new ArrayList<>(sortedFluidEntries.size());
        for (FluidEntry entry : sortedFluidEntries) {
            fluidIds.add(entry.fluidId());
            fluidAmountList.add(entry.amount());
            fluidCapacityList.add(entry.capacity());
        }

        int qSlotCount = RtsStorageBindings.QUICK_SLOT_COUNT;
        int gbSlotCount = RtsStorageBindings.GUI_BINDING_SLOT_COUNT;

        var recentEntries = session.uiMemory.getRecentEntries();
        List<String> recentIds = new ArrayList<>(recentEntries.size());
        List<Long> recentAmounts = new ArrayList<>(recentEntries.size());
        List<Long> recentCapacities = new ArrayList<>(recentEntries.size());
        List<Byte> recentKinds = new ArrayList<>(recentEntries.size());
        for (var recent : recentEntries) {
            recentIds.add(recent.id());
            recentAmounts.add(recent.amount());
            recentCapacities.add(recent.capacity());
            recentKinds.add(recent.kind());
        }

        Map<String, Long> funnelBufferSummary = RtsPagePayloadFactory.summarizeFunnelBuffer(session);
        List<String> funnelBufferItemIds = new ArrayList<>(funnelBufferSummary.size());
        List<Long> funnelBufferCounts = new ArrayList<>(funnelBufferSummary.size());
        for (var entry : funnelBufferSummary.entrySet()) {
            funnelBufferItemIds.add(entry.getKey());
            funnelBufferCounts.add(entry.getValue());
        }

        return new PageResult(new S2CRtsStoragePagePayload(
                RtsLinkedStorageResolver.hasAnyStorage(player, session),
                RtsLinkedStorageResolver.buildAnyStorageSummary(player, session),
                linkedPackedPositions,
                linkedRefs.names(), linkedRefs.modes(), linkedRefs.priorities(),
                linkedRefs.iconItemIds(), linkedRefs.worldAvailable(),
                safePage, totalPages, totalEntries,
                session.browser.search, session.browser.category,
                (byte) session.browser.sort.ordinal(), session.browser.ascending,
                session.sessionFlags.autoStoreMinedDrops, session.sessionFlags.useBdNetwork,
                categories,
                itemStacks, itemCounts,
                totalItemIds, totalItemCounts,
                fluidIds, fluidAmountList, fluidCapacityList,
                recentIds, recentAmounts, recentCapacities, recentKinds,
                RtsStorageUiPayloads.buildQuickSlotPayload(session, qSlotCount),
                RtsStorageUiPayloads.buildQuickSlotPreviewPayload(session, qSlotCount),
                RtsStorageUiPayloads.buildGuiBindingLabelPayload(session, gbSlotCount),
                RtsStorageUiPayloads.buildGuiBindingItemIdPayload(session, gbSlotCount),
                session.funnel.funnelEnabled, funnelBufferItemIds, funnelBufferCounts), safePage);
    }

    // ---- helpers ---------------------------------------------------------------

    public static long getHandlerReportedCount(IItemHandler handler, int slot, ItemStack stack) {
        return sanitizeCount(RtsAe2Compat.getReportedCount(handler, slot, stack));
    }

    static void mergeCount(Map<String, Long> counts, String key, long amount) {
        if (counts == null || key == null || key.isBlank()) {
            return;
        }
        long sanitized = sanitizeCount(amount);
        if (sanitized <= 0L) {
            return;
        }
        counts.merge(key, sanitized, RtsCountUtil::saturatedAdd);
    }

    public static long saturatedAdd(long a, long b) {
        return RtsCountUtil.saturatedAdd(a, b);
    }

    public static long sanitizeCount(long value) {
        return RtsCountUtil.sanitizeCount(value);
    }

    // ---- entry aggregation ----------------------------------------------------

    public static void accumulatePlayerMainInventoryCounts(ServerPlayer player, Map<String, Long> counts,
            Map<String, Long> namespaceTotals) {
        if (player == null || counts == null || namespaceTotals == null) {
            return;
        }
        int start = RtsPageSharedHelpers.getPlayerMainInventoryStart(player);
        int end = RtsPageSharedHelpers.getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) {
                continue;
            }
            mergeCount(counts, id.toString(), stack.getCount());
            mergeCount(namespaceTotals, id.getNamespace(), stack.getCount());
        }
    }

    static void accumulatePlayerMainInventoryEntries(ServerPlayer player, List<Entry> exactEntries) {
        if (player == null || exactEntries == null) {
            return;
        }
        int start = RtsPageSharedHelpers.getPlayerMainInventoryStart(player);
        int end = RtsPageSharedHelpers.getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            mergeExactEntry(exactEntries, stack, stack.getCount());
        }
    }

    static void mergeExactEntry(List<Entry> entries, ItemStack stack, long count) {
        if (entries == null || stack == null || stack.isEmpty() || count <= 0L) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        ItemStack prototype = stack.copy();
        prototype.setCount(1);
        for (int i = 0; i < entries.size(); i++) {
            Entry existing = entries.get(i);
            if (!ItemStack.isSameItemSameComponents(existing.stack(), prototype)) {
                continue;
            }
            entries.set(i, new Entry(
                    existing.stack(), existing.itemId(), existing.namespace(),
                    existing.path(), existing.label(),
                    saturatedAdd(existing.count(), count)));
            return;
        }
        entries.add(new Entry(prototype, id.toString(), id.getNamespace(), id.getPath(),
                prototype.getHoverName().getString(), count));
    }

    // ---- internal fluid check -------------------------------------------------

    private static boolean hasPositiveInternalFluid(RtsStorageSession session) {
        if (session == null) {
            return false;
        }
        for (Long amount : session.sessionFlags.internalFluidMb.values()) {
            if (amount != null && amount > 0L) {
                return true;
            }
        }
        return false;
    }
}
