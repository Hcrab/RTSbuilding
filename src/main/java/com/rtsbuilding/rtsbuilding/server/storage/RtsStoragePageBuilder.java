package com.rtsbuilding.rtsbuilding.server.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.RtsStorageUiPayloads;
import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Builds the read-only storage browser page from a session and linked storage snapshot.
 *
 * <p>This helper owns the storage grid/search/index boundary: it turns
 * {@link RtsStorageSession} state, resolved item/fluid handlers, internal fluid
 * counts, recent entries, quick slots, and GUI binding memory into the ordered
 * data carried by {@link S2CRtsStoragePagePayload}. It does not resolve linked
 * storage refs, mutate inventories or fluids, craft, mine, place blocks, write
 * NBT, or send packets. Those responsibilities stay in {@link RtsStorageManager}
 * and the linked resolver.
 *
 * <p>Payload ordering is player-facing behavior. Storage item grids, fluid
 * grids, total count lists, recent entries, quick slots, and GUI binding slots
 * must keep the same order and empty-string padding as the manager emitted
 * before this extraction.
 */
public final class RtsStoragePageBuilder {
    public static final int DEFAULT_PAGE_SIZE = 90;
    private static final int MAX_PAGE_SIZE = 180;
    private static final int PLAYER_MAIN_INVENTORY_END_EXCLUSIVE = 36;
    private static final String CATEGORY_ALL = "all";
    private static final String CATEGORY_MOD_PREFIX = "mod|";
    private static final String CATEGORY_TAB_PREFIX = "tab|";
    private static final Map<String, Set<String>> ITEM_CREATIVE_TAB_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> BROKEN_CREATIVE_TAB_CACHE = ConcurrentHashMap.newKeySet();
    private static volatile boolean creativeTabCacheWarmNormal;
    private static volatile boolean creativeTabCacheWarmOperator;

    private RtsStoragePageBuilder() {
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
        boolean includePlayerMainInventory = shouldIncludePlayerMainInventoryInStorageView(player, session);
        LinkedRefPayload linkedRefs = buildLinkedRefPayload(player, session);
        List<Long> linkedPackedPositions = linkedRefs.positions();
        if (session.linkedStorages.isEmpty()
                && itemHandlers.isEmpty()
                && fluidHandlers.isEmpty()
                && !hasPositiveInternalFluid(session)
                && !includePlayerMainInventory) {
            return new PageResult(buildEmptyPayload(player, session), 0);
        }

        Map<String, Long> counts = new HashMap<>();
        List<Entry> exactEntries = new ArrayList<>();
        Map<String, Long> namespaceTotals = new HashMap<>();
        for (LinkedHandler linked : itemHandlers) {
            IItemHandler handler = linked.handler();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id == null) {
                    continue;
                }
                long reportedCount = getHandlerReportedCount(handler, i, stack);
                mergeCount(counts, id.toString(), reportedCount);
                mergeExactEntry(exactEntries, stack, reportedCount);
                mergeCount(namespaceTotals, id.getNamespace(), reportedCount);
            }
        }
        if (includePlayerMainInventory) {
            accumulatePlayerMainInventoryCounts(player, counts, namespaceTotals);
            accumulatePlayerMainInventoryEntries(player, exactEntries);
        }

        Map<String, Long> fluidAmounts = new HashMap<>();
        Map<String, Long> fluidCapacities = new HashMap<>();

        for (var entry : session.internalFluidMb.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            mergeCount(fluidAmounts, entry.getKey(), entry.getValue());
        }

        for (LinkedFluidHandler linked : fluidHandlers) {
            IFluidHandler handler = linked.handler();
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack fluid = handler.getFluidInTank(tank);
                if (fluid.isEmpty()) {
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
                if (id == null) {
                    continue;
                }
                String fluidId = id.toString();
                mergeCount(fluidAmounts, fluidId, fluid.getAmount());
                mergeCount(fluidCapacities, fluidId, Math.max(0, handler.getTankCapacity(tank)));
            }
        }

        long internalFluidCapacityMb = internalFluidCapacityMb(player);
        for (String fluidId : fluidAmounts.keySet()) {
            mergeCount(fluidCapacities, fluidId, internalFluidCapacityMb);
            ResourceLocation rl = ResourceLocation.tryParse(fluidId);
            if (rl != null) {
                mergeCount(namespaceTotals, rl.getNamespace(), fluidAmounts.getOrDefault(fluidId, 0L));
            }
        }

        Map<String, Set<String>> itemTabKeys = new HashMap<>();
        Map<String, Set<String>> modTabKeys = new HashMap<>();
        if (!counts.isEmpty()) {
            boolean operatorTabs = player.canUseGameMasterBlocks();
            if (ensureCreativeTabContents(player)) {
                for (String itemId : counts.keySet()) {
                    ResourceLocation rl = ResourceLocation.tryParse(itemId);
                    if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
                        continue;
                    }
                    Item item = BuiltInRegistries.ITEM.get(rl);
                    Set<String> tabs = resolveCreativeTabKeys(itemId, item, operatorTabs);
                    if (tabs.isEmpty()) {
                        continue;
                    }
                    Set<String> copied = new HashSet<>(tabs);
                    itemTabKeys.put(itemId, copied);
                    modTabKeys.computeIfAbsent(rl.getNamespace(), ignored -> new HashSet<>()).addAll(copied);
                }
            }
        }

        List<String> namespaces = new ArrayList<>(namespaceTotals.keySet());
        namespaces.sort(RtsStoragePageBuilder::compareNamespace);

        List<String> categories = new ArrayList<>();
        categories.add(CATEGORY_ALL);
        for (String namespace : namespaces) {
            categories.add(encodeModCategory(namespace));
            List<String> tabs = new ArrayList<>(modTabKeys.getOrDefault(namespace, Set.of()));
            tabs.sort(RtsStoragePageBuilder::compareTabKey);
            for (String tabKey : tabs) {
                categories.add(encodeTabCategory(namespace, tabKey));
            }
        }

        CategorySelection selectedCategory = parseCategorySelection(session.category);
        if (!isValidCategorySelection(selectedCategory, categories)) {
            session.category = CATEGORY_ALL;
            selectedCategory = CategorySelection.all();
        }

        String query = session.search.toLowerCase(Locale.ROOT).trim();
        List<Entry> entries = new ArrayList<>();
        for (Entry exactEntry : exactEntries) {
            String id = exactEntry.itemId();
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (!matchesSearchQuery(
                    rl,
                    id,
                    exactEntry.label(),
                    query,
                    session.pinyinSearchEnabled,
                    session.localizedSearchMatches)) {
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
            if (!matchesSearchQuery(rl, id, null, query, session.pinyinSearchEnabled, session.localizedSearchMatches)) {
                continue;
            }
            String namespace = rl == null ? "unknown" : rl.getNamespace();
            String path = rl == null ? id : rl.getPath();
            if (selectedCategory.isCreativeTab() || !selectedCategory.matches(namespace, Set.of())) {
                continue;
            }
            long amount = Math.max(0L, e.getValue());
            long capacity = Math.max(amount, fluidCapacities.getOrDefault(id, internalFluidCapacityMb));
            fluidEntries.add(new FluidEntry(id, namespace, path, amount, capacity));
        }

        Comparator<Entry> comparator = switch (session.sort) {
            case MOD -> Comparator.comparing(Entry::namespace, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::path, String.CASE_INSENSITIVE_ORDER);
            case NAME -> Comparator.comparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::namespace, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::path, String.CASE_INSENSITIVE_ORDER);
            case QUANTITY -> Comparator.comparingLong(Entry::count)
                    .thenComparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::path, String.CASE_INSENSITIVE_ORDER);
        };
        if (session.sort == RtsStorageSort.QUANTITY && !session.ascending) {
            comparator = comparator.reversed();
        } else if (session.sort != RtsStorageSort.QUANTITY && !session.ascending) {
            comparator = comparator.reversed();
        }
        entries.sort(comparator);

        Comparator<FluidEntry> fluidComparator = switch (session.sort) {
            case MOD -> Comparator.comparing(FluidEntry::namespace).thenComparing(FluidEntry::path);
            case NAME -> Comparator.comparing(FluidEntry::path).thenComparing(FluidEntry::namespace);
            case QUANTITY -> Comparator.comparingLong(FluidEntry::amount).thenComparing(FluidEntry::path);
        };
        if (!session.ascending) {
            fluidComparator = fluidComparator.reversed();
        }
        fluidEntries.sort(fluidComparator);

        int pageSize = sanitizePageSize(requestedPageSize);
        int totalEntries = entries.size();
        int totalPages = Math.max(1, (totalEntries + pageSize - 1) / pageSize);
        int safePage = Math.max(0, Math.min(requestedPage, totalPages - 1));
        int from = safePage * pageSize;
        int to = Math.min(from + pageSize, totalEntries);

        List<ItemStack> itemStacks = new ArrayList<>();
        List<Long> itemCounts = new ArrayList<>();
        for (int i = from; i < to; i++) {
            Entry e = entries.get(i);
            itemStacks.add(e.stack().copy());
            itemCounts.add(e.count());
        }

        List<String> totalItemIds = new ArrayList<>(counts.size());
        List<Long> totalItemCounts = new ArrayList<>(counts.size());
        for (var entry : counts.entrySet()) {
            totalItemIds.add(entry.getKey());
            totalItemCounts.add(entry.getValue());
        }

        List<String> fluidIds = new ArrayList<>(fluidEntries.size());
        List<Long> fluidAmountList = new ArrayList<>(fluidEntries.size());
        List<Long> fluidCapacityList = new ArrayList<>(fluidEntries.size());
        for (FluidEntry entry : fluidEntries) {
            fluidIds.add(entry.fluidId());
            fluidAmountList.add(entry.amount());
            fluidCapacityList.add(entry.capacity());
        }

        List<String> recentIds = new ArrayList<>(session.recentEntries.size());
        List<Long> recentAmounts = new ArrayList<>(session.recentEntries.size());
        List<Long> recentCapacities = new ArrayList<>(session.recentEntries.size());
        List<Byte> recentKinds = new ArrayList<>(session.recentEntries.size());
        for (RecentEntry recent : session.recentEntries) {
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
                linkedRefs.names(),
                linkedRefs.modes(),
                linkedRefs.priorities(),
                linkedRefs.iconItemIds(),
                safePage,
                totalPages,
                totalEntries,
                session.search,
                session.category,
                (byte) session.sort.ordinal(),
                session.ascending,
                session.autoStoreMinedDrops,
                session.useBdNetwork,
                categories,
                itemStacks,
                itemCounts,
                totalItemIds,
                totalItemCounts,
                fluidIds,
                fluidAmountList,
                fluidCapacityList,
                recentIds,
                recentAmounts,
                recentCapacities,
                recentKinds,
                RtsStorageUiPayloads.buildQuickSlotPayload(session, RtsStorageManager.QUICK_SLOT_COUNT),
                RtsStorageUiPayloads.buildGuiBindingLabelPayload(session, RtsStorageManager.GUI_BINDING_SLOT_COUNT),
                RtsStorageUiPayloads.buildGuiBindingItemIdPayload(session, RtsStorageManager.GUI_BINDING_SLOT_COUNT),
                session.funnelEnabled,
                funnelBufferItemIds,
                funnelBufferCounts), safePage);
    }

    public static int sanitizePageSize(int pageSize) {
        return Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
    }

    private static S2CRtsStoragePagePayload buildEmptyPayload(ServerPlayer player, RtsStorageSession session) {
        LinkedRefPayload linkedRefs = buildLinkedRefPayload(player, session);
        return new S2CRtsStoragePagePayload(
                RtsLinkedStorageResolver.hasAnyStorage(player, session),
                RtsLinkedStorageResolver.buildAnyStorageSummary(player, session),
                linkedRefs.positions(),
                linkedRefs.names(),
                linkedRefs.modes(),
                linkedRefs.priorities(),
                linkedRefs.iconItemIds(),
                0,
                1,
                0,
                session.search,
                session.category,
                (byte) session.sort.ordinal(),
                session.ascending,
                session.autoStoreMinedDrops,
                session.useBdNetwork,
                List.of(CATEGORY_ALL),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                RtsStorageUiPayloads.buildQuickSlotPayload(session, RtsStorageManager.QUICK_SLOT_COUNT),
                RtsStorageUiPayloads.buildGuiBindingLabelPayload(session, RtsStorageManager.GUI_BINDING_SLOT_COUNT),
                RtsStorageUiPayloads.buildGuiBindingItemIdPayload(session, RtsStorageManager.GUI_BINDING_SLOT_COUNT),
                session.funnelEnabled,
                List.of(),
                List.of());
    }

    /**
     * Localized search ids are sanitized here so storage and craft search share
     * the same registry gate before matching client-side translated names.
     */
    public static Set<String> sanitizeLocalizedSearchMatches(List<String> localizedSearchMatches) {
        if (localizedSearchMatches == null || localizedSearchMatches.isEmpty()) {
            return Set.of();
        }
        Set<String> sanitized = new HashSet<>();
        for (String itemId : localizedSearchMatches) {
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                continue;
            }
            sanitized.add(key.toString());
            if (sanitized.size() >= 8192) {
                break;
            }
        }
        return sanitized;
    }

    public static String normalizeCategory(String category) {
        if (category == null) {
            return CATEGORY_ALL;
        }
        String value = category.toLowerCase(Locale.ROOT).trim();
        if (value.isEmpty() || CATEGORY_ALL.equals(value)) {
            return CATEGORY_ALL;
        }
        if (value.startsWith(CATEGORY_MOD_PREFIX) || value.startsWith(CATEGORY_TAB_PREFIX)) {
            return value;
        }
        // Backward compatibility for legacy category payloads that only sent namespace.
        return encodeModCategory(value);
    }

    /**
     * Creative-tab indexing belongs to page building because it only affects
     * storage browser category chips, not actual inventory contents.
     */
    public static void warmCreativeTabCacheMode(ServerLevel level, boolean operatorTabs) {
        if (isCreativeTabCacheWarm(operatorTabs)) {
            return;
        }
        rebuildCreativeTabContentsSafely(level, operatorTabs);
        setCreativeTabCacheWarm(operatorTabs);
    }

    public static void clearCreativeTabCacheState() {
        ITEM_CREATIVE_TAB_CACHE.clear();
        BROKEN_CREATIVE_TAB_CACHE.clear();
        creativeTabCacheWarmNormal = false;
        creativeTabCacheWarmOperator = false;
    }

    /**
     * Count normalization lives with page building so AE2 reported counts and
     * "effectively infinite" values are shown consistently wherever the manager
     * summarizes visible storage.
     */
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

    static long saturatedAdd(long a, long b) {
        return RtsCountUtil.saturatedAdd(a, b);
    }

    static long sanitizeCount(long value) {
        return RtsCountUtil.sanitizeCount(value);
    }

    public static long internalFluidCapacityMb(ServerPlayer player) {
        return RtsStorageFluids.internalFluidCapacityMb(player);
    }

    private static boolean matchesSearchQuery(ResourceLocation id, String rawId, String label, String query,
            boolean pinyinSearchEnabled, Set<String> localizedSearchMatches) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String normalizedId = rawId == null ? "" : rawId.toLowerCase(Locale.ROOT);
        if (localizedSearchMatches != null && localizedSearchMatches.contains(normalizedId)) {
            return true;
        }
        if (query.startsWith("@")) {
            String modQuery = query.substring(1).trim();
            if (modQuery.isEmpty()) {
                return true;
            }
            String namespace = id == null ? "" : id.getNamespace().toLowerCase(Locale.ROOT);
            return namespace.contains(modQuery);
        }
        if (normalizedId.contains(query)) {
            return true;
        }
        String normalizedLabel = label == null ? "" : label.toLowerCase(Locale.ROOT);
        return normalizedLabel.contains(query) || (pinyinSearchEnabled && RtsPinyinSearch.contains(label, query));
    }

    private static int compareNamespace(String a, String b) {
        if ("minecraft".equals(a)) {
            return "minecraft".equals(b) ? 0 : -1;
        }
        if ("minecraft".equals(b)) {
            return 1;
        }
        return a.compareToIgnoreCase(b);
    }

    private static int compareTabKey(String a, String b) {
        ResourceLocation aId = ResourceLocation.tryParse(a);
        ResourceLocation bId = ResourceLocation.tryParse(b);
        String aName = aId == null ? a : aId.getPath();
        String bName = bId == null ? b : bId.getPath();
        int byName = aName.compareToIgnoreCase(bName);
        return byName != 0 ? byName : a.compareToIgnoreCase(b);
    }

    private static String encodeModCategory(String namespace) {
        return CATEGORY_MOD_PREFIX + namespace;
    }

    private static String encodeTabCategory(String namespace, String tabKey) {
        return CATEGORY_TAB_PREFIX + namespace + "|" + tabKey;
    }

    private static Set<String> resolveCreativeTabKeys(String itemId, Item item, boolean operatorTabs) {
        Set<String> tabKeys = ITEM_CREATIVE_TAB_CACHE.get(creativeTabItemCacheKey(itemId, operatorTabs));
        return tabKeys == null ? Set.of() : tabKeys;
    }

    private static boolean ensureCreativeTabContents(ServerPlayer player) {
        boolean operatorTabs = player.canUseGameMasterBlocks();
        if (isCreativeTabCacheWarm(operatorTabs)) {
            return true;
        }
        synchronized (RtsStoragePageBuilder.class) {
            if (isCreativeTabCacheWarm(operatorTabs)) {
                return true;
            }
            warmCreativeTabCacheMode(player.serverLevel(), operatorTabs);
            return true;
        }
    }

    private static void rebuildCreativeTabContentsSafely(ServerLevel level, boolean operatorTabs) {
        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                level.enabledFeatures(),
                operatorTabs,
                level.registryAccess());
        rebuildCreativeTabContentsSafely(parameters, operatorTabs, true);
        rebuildCreativeTabContentsSafely(parameters, operatorTabs, false);
    }

    private static void rebuildCreativeTabContentsSafely(
            CreativeModeTab.ItemDisplayParameters parameters,
            boolean operatorTabs,
            boolean categoryTabs) {
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab == null) {
                continue;
            }
            boolean category = tab.getType() == CreativeModeTab.Type.CATEGORY;
            if (category != categoryTabs) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (isBrokenCreativeTab(key, operatorTabs)) {
                continue;
            }
            try {
                tab.buildContents(parameters);
                if (category) {
                    indexCreativeTabContents(tab, key, operatorTabs);
                }
            } catch (RuntimeException | LinkageError ex) {
                markBrokenCreativeTab(key, operatorTabs, ex);
            }
        }
    }

    private static void indexCreativeTabContents(CreativeModeTab tab, ResourceLocation key, boolean operatorTabs) {
        if (key == null || !tab.shouldDisplay()) {
            return;
        }
        String tabKey = key.toString();
        for (ItemStack stack : tab.getDisplayItems()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null) {
                continue;
            }
            ITEM_CREATIVE_TAB_CACHE.compute(creativeTabItemCacheKey(itemId.toString(), operatorTabs), (ignored, existing) -> {
                Set<String> tabs = existing == null ? ConcurrentHashMap.newKeySet() : existing;
                tabs.add(tabKey);
                return tabs;
            });
        }
    }

    private static boolean isBrokenCreativeTab(ResourceLocation key, boolean operatorTabs) {
        return BROKEN_CREATIVE_TAB_CACHE.contains(creativeTabModeKey(key, operatorTabs));
    }

    private static void markBrokenCreativeTab(ResourceLocation key, boolean operatorTabs, Throwable ex) {
        String tabKey = key == null ? "unknown" : key.toString();
        if (!BROKEN_CREATIVE_TAB_CACHE.add(creativeTabModeKey(tabKey, operatorTabs))) {
            return;
        }
        RtsbuildingMod.LOGGER.warn(
                "Skipping RTS creative tab {} for {} cache because it failed to build. "
                        + "The RTS storage browser will continue without this tab.",
                tabKey,
                operatorTabs ? "operator" : "normal",
                ex);
    }

    private static String creativeTabModeKey(ResourceLocation key, boolean operatorTabs) {
        return creativeTabModeKey(key == null ? "unknown" : key.toString(), operatorTabs);
    }

    private static String creativeTabModeKey(String key, boolean operatorTabs) {
        return (operatorTabs ? "op|" : "normal|") + key;
    }

    private static String creativeTabItemCacheKey(String itemId, boolean operatorTabs) {
        return (operatorTabs ? "op|" : "normal|") + itemId;
    }

    private static boolean isCreativeTabCacheWarm(boolean operatorTabs) {
        return operatorTabs ? creativeTabCacheWarmOperator : creativeTabCacheWarmNormal;
    }

    private static void setCreativeTabCacheWarm(boolean operatorTabs) {
        if (operatorTabs) {
            creativeTabCacheWarmOperator = true;
        } else {
            creativeTabCacheWarmNormal = true;
        }
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

    /**
     * Player inventory fallback is part of the storage browser view contract:
     * other manager paths reuse it so picking from the visible grid matches what
     * the player can actually extract.
     */
    public static boolean shouldIncludePlayerMainInventoryInStorageView(ServerPlayer player, RtsStorageSession session) {
        if (player == null || player.containerMenu instanceof RtsCraftTerminalMenu) {
            return false;
        }
        if (session != null && session.linkedStorages.isEmpty() && !RtsBdCompat.hasPrimaryNetwork(player)) {
            return true;
        }
        return player.containerMenu == player.inventoryMenu;
    }

    private static CategorySelection parseCategorySelection(String normalizedCategory) {
        if (normalizedCategory == null || CATEGORY_ALL.equals(normalizedCategory)) {
            return CategorySelection.all();
        }
        if (normalizedCategory.startsWith(CATEGORY_MOD_PREFIX)) {
            String namespace = normalizedCategory.substring(CATEGORY_MOD_PREFIX.length());
            if (namespace.isBlank()) {
                return CategorySelection.all();
            }
            return CategorySelection.mod(namespace);
        }
        if (normalizedCategory.startsWith(CATEGORY_TAB_PREFIX)) {
            String payload = normalizedCategory.substring(CATEGORY_TAB_PREFIX.length());
            int split = payload.indexOf('|');
            if (split <= 0 || split >= payload.length() - 1) {
                return CategorySelection.all();
            }
            String namespace = payload.substring(0, split);
            String tabKey = payload.substring(split + 1);
            if (namespace.isBlank() || tabKey.isBlank()) {
                return CategorySelection.all();
            }
            return CategorySelection.tab(namespace, tabKey);
        }
        return CategorySelection.all();
    }

    private static boolean isValidCategorySelection(CategorySelection selection, List<String> categories) {
        if (selection == null || selection.type() == CategorySelection.Type.ALL) {
            return true;
        }
        String token = switch (selection.type()) {
            case MOD -> encodeModCategory(selection.namespace());
            case TAB -> encodeTabCategory(selection.namespace(), selection.tabKey());
            case ALL -> CATEGORY_ALL;
        };
        return categories.contains(token);
    }

    /**
     * Shared main-inventory slot bounds keep extraction fallback aligned with
     * the rows included by the storage browser page.
     */
    public static int getPlayerMainInventoryStart(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return 0;
    }

    public static int getPlayerMainInventoryEndExclusive(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return Math.min(PLAYER_MAIN_INVENTORY_END_EXCLUSIVE, player.getInventory().getContainerSize());
    }

    /**
     * Craft availability reuses these totals so the fallback player-inventory
     * rows visible in the browser match craft-panel availability checks.
     */
    public static void accumulatePlayerMainInventoryCounts(ServerPlayer player, Map<String, Long> counts,
            Map<String, Long> namespaceTotals) {
        if (player == null || counts == null || namespaceTotals == null) {
            return;
        }
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
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

    private static void accumulatePlayerMainInventoryEntries(ServerPlayer player, List<Entry> exactEntries) {
        if (player == null || exactEntries == null) {
            return;
        }
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
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
            if (!ItemStack.isSameItemSameComponents(existing.stack(), prototype)) {
                continue;
            }
            entries.set(i, new Entry(
                    existing.stack(),
                    existing.itemId(),
                    existing.namespace(),
                    existing.path(),
                    existing.label(),
                    saturatedAdd(existing.count(), count)));
            return;
        }
        entries.add(new Entry(
                prototype,
                id.toString(),
                id.getNamespace(),
                id.getPath(),
                prototype.getHoverName().getString(),
                count));
    }

    private static List<Long> toPackedPositions(ServerPlayer player, List<LinkedStorageRef> refs) {
        ResourceKey<Level> currentDimension = player.serverLevel().dimension();
        List<Long> packed = new ArrayList<>(refs.size());
        for (LinkedStorageRef ref : refs) {
            if (ref == null || ref.pos() == null || !currentDimension.equals(ref.dimension())) {
                continue;
            }
            packed.add(ref.pos().asLong());
        }
        return packed;
    }

    /**
     * Builds the small per-binding detail payload for the storage-link window.
     *
     * <p>The existing storage page already carried packed positions for top-bar
     * status text. Issue #41 needs enough detail for a management list, so the
     * server adds names, modes, and icon item ids here while keeping the client
     * panel as a renderer only. The list is limited to the player's current
     * dimension because current RTS storage browsing is dimension-local;
     * cross-dimension refs remain persisted until a separate cross-dimension UI
     * design exists.
     */
    private static LinkedRefPayload buildLinkedRefPayload(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null || session.linkedStorages.isEmpty()) {
            return new LinkedRefPayload(List.of(), List.of(), List.of(), List.of(), List.of());
        }
        ResourceKey<Level> currentDimension = player.serverLevel().dimension();
        ServerLevel level = player.serverLevel();
        List<Long> positions = new ArrayList<>(session.linkedStorages.size());
        List<String> names = new ArrayList<>(session.linkedStorages.size());
        List<Byte> modes = new ArrayList<>(session.linkedStorages.size());
        List<Integer> priorities = new ArrayList<>(session.linkedStorages.size());
        List<String> iconItemIds = new ArrayList<>(session.linkedStorages.size());
        for (LinkedStorageRef ref : session.linkedStorages) {
            if (ref == null || ref.pos() == null || !currentDimension.equals(ref.dimension())) {
                continue;
            }
            BlockPos pos = ref.pos();
            positions.add(pos.asLong());
            names.add(resolveLinkedRefName(level, session, ref));
            modes.add(session.linkedModes.getOrDefault(ref, C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL));
            priorities.add(RtsStorageManager.sanitizeLinkedStoragePriority(
                    session.linkedPriorities.getOrDefault(ref, 0)));
            iconItemIds.add(resolveLinkedRefIconItemId(level, pos));
        }
        return new LinkedRefPayload(positions, names, modes, priorities, iconItemIds);
    }

    private static String resolveLinkedRefName(ServerLevel level, RtsStorageSession session, LinkedStorageRef ref) {
        if (level != null && ref != null && ref.pos() != null && level.hasChunkAt(ref.pos())) {
            return RtsLinkedStorageResolver.resolveDisplayName(level, ref.pos());
        }
        String cached = session == null || ref == null ? "" : session.linkedNames.get(ref);
        return cached == null || cached.isBlank() ? "Linked Storage" : cached;
    }

    private static String resolveLinkedRefIconItemId(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return "";
        }
        Item item = level.getBlockState(pos).getBlock().asItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "" : id.toString();
    }

    private static Map<String, Long> summarizeFunnelBuffer(RtsStorageSession session) {
        Map<String, Long> counts = new HashMap<>();
        for (ItemStack stack : session.funnelBuffer) {
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

    public record PageResult(S2CRtsStoragePagePayload payload, int safePage) {
    }

    private record LinkedRefPayload(List<Long> positions, List<String> names, List<Byte> modes,
            List<Integer> priorities, List<String> iconItemIds) {
    }

    private record Entry(ItemStack stack, String itemId, String namespace, String path, String label, long count) {
    }

    private record FluidEntry(String fluidId, String namespace, String path, long amount, long capacity) {
    }

    private record CategorySelection(Type type, String namespace, String tabKey) {
        private static CategorySelection all() {
            return new CategorySelection(Type.ALL, "", "");
        }

        private static CategorySelection mod(String namespace) {
            return new CategorySelection(Type.MOD, namespace, "");
        }

        private static CategorySelection tab(String namespace, String tabKey) {
            return new CategorySelection(Type.TAB, namespace, tabKey);
        }

        private boolean isCreativeTab() {
            return this.type == Type.TAB;
        }

        private boolean matches(String namespace, Set<String> tabs) {
            return switch (this.type) {
                case ALL -> true;
                case MOD -> this.namespace.equals(namespace);
                case TAB -> this.namespace.equals(namespace) && tabs.contains(this.tabKey);
            };
        }

        private enum Type {
            ALL,
            MOD,
            TAB
        }
    }
}
