package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.RtsStorageUiPayloads;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.storage.*;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.*;

/**
 * Builds the read-only storage browser page from a session and linked storage snapshot.
 */
public final class RtsPageCore {

    private RtsPageCore() {
    }

    // ======================================================================
    //  Page cache: avoids O(n log n) sort + filter rebuild on pure pagination
    // ======================================================================

    /** Per-player page build cache, keyed by (params + data version). */
    private static final java.util.Map<java.util.UUID, CachedPage> pageCache = new java.util.HashMap<>();

    /**
     * Removes a player's cached page data so the GC can reclaim memory
     * when they disable RTS or log out.
     */
    public static void clearCache(java.util.UUID playerUuid) {
        if (playerUuid != null) {
            pageCache.remove(playerUuid);
        }
    }

    /** Key that determines cache validity. */
    private record CachedPageKey(
            String search, RtsStorageSort sort, String category, boolean ascending,
            int pageSize, boolean pinyinSearchEnabled, boolean includePlayerInventory
    ) {}

    /** Cached result of the expensive sort + filter + categories build phase. */
    private record CachedPage(
            CachedPageKey key,
            long dataVersion,
            List<Entry> sortedEntries,
            List<FluidEntry> sortedFluidEntries,
            Map<String, Long> counts,
            Map<String, Long> namespaceTotals,
            List<String> categories
    ) {}

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
        LinkedRefPayload linkedRefs = buildLinkedRefPayload(player, session);
        List<Long> linkedPackedPositions = linkedRefs.positions();
        if (session.linkedStorages.isEmpty()
                && itemHandlers.isEmpty()
                && fluidHandlers.isEmpty()
                && !hasPositiveInternalFluid(session)
                && !includePlayerMainInventory) {
            return new PageResult(buildEmptyPayload(player, session), 0);
        }

        // ── Page cache check: avoid O(n log n) sort + filter rebuild on pure pagination ──
        CachedPageKey cacheKey = new CachedPageKey(
                session.browser.search, session.browser.sort, session.browser.category, session.browser.ascending,
                requestedPageSize, session.browser.pinyinSearchEnabled, includePlayerMainInventory);
        CachedPage cached = pageCache.get(player.getUUID());

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
            // ── Full build: counts ??exactEntries ??fluid ??categories ??sort ??filter ──
            Map<String, Long> localCounts = new HashMap<>();
            List<Entry> exactEntries = new ArrayList<>();
            Map<String, Long> localNamespaceTotals = new HashMap<>();

            // Fast path: build from slot cache
            RtsAggregateStorage aggregate = RtsStorageTickService.INSTANCE.getStorage(player);
            boolean usedCache = false;
            if (aggregate != null && !aggregate.isEmpty()) {
                aggregate.getAvailableItems(localCounts);
                if (!localCounts.isEmpty()) {
                    for (var entry : localCounts.entrySet()) {
                        String itemId = entry.getKey();
                        long count = entry.getValue();
                        ResourceLocation id = ResourceLocation.tryParse(itemId);
                        if (id == null) continue;
                        ItemStack prototype = aggregate.getPrototype(itemId);
                        if (prototype.isEmpty()) {
                            var item = BuiltInRegistries.ITEM.get(id);
                            prototype = new ItemStack(item);
                        }
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
            for (var entry : session.internalFluidMb.entrySet()) {
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
            pageCache.put(player.getUUID(), new CachedPage(
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

        boolean totalCountsSnapshot = shouldSendTotalCountsSnapshot(session.browser.search);
        List<String> totalItemIds = totalCountsSnapshot ? new ArrayList<>(counts.size()) : List.of();
        List<Long> totalItemCounts = totalCountsSnapshot ? new ArrayList<>(counts.size()) : List.of();
        if (totalCountsSnapshot) {
            for (var entry : counts.entrySet()) {
                totalItemIds.add(entry.getKey());
                totalItemCounts.add(entry.getValue());
            }
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

        List<String> recentIds = new ArrayList<>(session.recentEntries.size());
        List<Long> recentAmounts = new ArrayList<>(session.recentEntries.size());
        List<Long> recentCapacities = new ArrayList<>(session.recentEntries.size());
        List<Byte> recentKinds = new ArrayList<>(session.recentEntries.size());
        for (var recent : session.recentEntries) {
            recentIds.add(recent.id());
            recentAmounts.add(recent.amount());
            recentCapacities.add(recent.capacity());
            recentKinds.add(recent.kind());
        }

        Map<String, Long> funnelBufferSummary = summarizeFunnelBuffer(session);
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
                session.autoStoreMinedDrops, session.useBdNetwork,
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

    static boolean shouldSendTotalCountsSnapshot(String search) {
        return search == null || search.isBlank();
    }

    public static long getHandlerReportedCount(IItemHandler handler, int slot, ItemStack stack) {
        if (handler instanceof ReportedCountItemHandler reported) {
            return sanitizeCount(reported.getReportedCount(slot));
        }
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

    // ---- empty payload ---------------------------------------------------------

    private static S2CRtsStoragePagePayload buildEmptyPayload(ServerPlayer player, RtsStorageSession session) {
        LinkedRefPayload linkedRefs = buildLinkedRefPayload(player, session);
        int qSlotCount = RtsStorageBindings.QUICK_SLOT_COUNT;
        int gbSlotCount = RtsStorageBindings.GUI_BINDING_SLOT_COUNT;
        return new S2CRtsStoragePagePayload(
                RtsLinkedStorageResolver.hasAnyStorage(player, session),
                RtsLinkedStorageResolver.buildAnyStorageSummary(player, session),
                linkedRefs.positions(), linkedRefs.names(), linkedRefs.modes(),
                linkedRefs.priorities(), linkedRefs.iconItemIds(), linkedRefs.worldAvailable(),
                0, 1, 0,
                session.browser.search, session.browser.category,
                (byte) session.browser.sort.ordinal(), session.browser.ascending,
                session.autoStoreMinedDrops, session.useBdNetwork,
                List.of(RtsPageSharedHelpers.CATEGORY_ALL),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.<Byte>of(),
                RtsStorageUiPayloads.buildQuickSlotPayload(session, qSlotCount),
                RtsStorageUiPayloads.buildQuickSlotPreviewPayload(session, qSlotCount),
                RtsStorageUiPayloads.buildGuiBindingLabelPayload(session, gbSlotCount),
                RtsStorageUiPayloads.buildGuiBindingItemIdPayload(session, gbSlotCount),
                session.funnel.funnelEnabled, List.of(), List.of());
    }

    // ---- linked ref payload ----------------------------------------------------

    private static LinkedRefPayload buildLinkedRefPayload(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null || session.linkedStorages.isEmpty()) {
            return new LinkedRefPayload(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        ResourceKey<Level> currentDimension = player.serverLevel().dimension();
        ServerLevel level = player.serverLevel();
        List<Long> positions = new ArrayList<>(session.linkedStorages.size());
        List<String> names = new ArrayList<>(session.linkedStorages.size());
        List<Byte> modes = new ArrayList<>(session.linkedStorages.size());
        List<Integer> priorities = new ArrayList<>(session.linkedStorages.size());
        List<String> iconItemIds = new ArrayList<>(session.linkedStorages.size());
        List<Boolean> worldAvailable = new ArrayList<>(session.linkedStorages.size());
        for (LinkedStorageRef ref : session.linkedStorages) {
            boolean backpackLink = ref != null && session.linkedBackpackUuids.containsKey(ref);
            if (ref == null || ref.pos() == null || (!backpackLink && !currentDimension.equals(ref.dimension()))) {
                continue;
            }
            BlockPos pos = ref.pos();
            boolean visible = RtsLinkedStorageResolver.isLinkedRefWorldVisible(player, session, ref);
            positions.add(pos.asLong());
            names.add(resolveLinkedRefName(level, session, ref, visible));
            modes.add(session.linkedModes.getOrDefault(ref, C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL));
            priorities.add(RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(
                    session.linkedPriorities.getOrDefault(ref, 0)));
            iconItemIds.add(resolveLinkedRefIconItemId(level, session, ref, visible));
            worldAvailable.add(visible);
        }
        return new LinkedRefPayload(positions, names, modes, priorities, iconItemIds, worldAvailable);
    }

    private static String resolveLinkedRefName(ServerLevel level, RtsStorageSession session, LinkedStorageRef ref,
            boolean worldVisible) {
        if (worldVisible && level != null && ref != null && ref.pos() != null && level.hasChunkAt(ref.pos())) {
            return RtsLinkedStorageResolver.resolveDisplayName(level, ref.pos());
        }
        String cached = session == null || ref == null ? "" : session.linkedNames.get(ref);
        return cached == null || cached.isBlank() ? "Linked Storage" : cached;
    }

    private static String resolveLinkedRefIconItemId(ServerLevel level, RtsStorageSession session, LinkedStorageRef ref,
            boolean worldVisible) {
        if (!worldVisible) {
            String backpackItemId = session == null || ref == null ? "" : session.linkedBackpackItemIds.get(ref);
            return backpackItemId == null ? "" : backpackItemId;
        }
        BlockPos pos = ref.pos();
        Item item = level.getBlockState(pos).getBlock().asItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "" : id.toString();
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

    private static void mergeExactEntry(List<Entry> entries, ItemStack stack, long count) {
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
            if (!ItemStack.isSameItemSameTags(existing.stack(), prototype)) {
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

    private static boolean hasPositiveInternalFluid(RtsStorageSession session) {
        if (session == null) {
            return false;
        }
        for (Long amount : session.internalFluidMb.values()) {
            if (amount != null && amount > 0L) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Long> summarizeFunnelBuffer(RtsStorageSession session) {
        Map<String, Long> counts = new HashMap<>();
        for (ItemStack stack : session.funnel.funnelBuffer) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) {
                continue;
            }
            mergeCount(counts, id.toString(), stack.getCount());
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.comparingByKey());
        Map<String, Long> ordered = new LinkedHashMap<>();
        for (var entry : sorted) {
            ordered.put(entry.getKey(), entry.getValue());
        }
        return ordered;
    }
}
