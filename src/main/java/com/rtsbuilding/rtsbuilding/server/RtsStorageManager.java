package com.rtsbuilding.rtsbuilding.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Field;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.rtsbuilding.rtsbuilding.client.BuilderMode;
import com.rtsbuilding.rtsbuilding.compat.ftb.RtsFtbCompat;
import com.rtsbuilding.rtsbuilding.network.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsStoreFluidPayload;
import com.rtsbuilding.rtsbuilding.network.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsStoragePagePayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.FluidTags;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;

public final class RtsStorageManager {
    private static final int PAGE_SIZE = 90;
    private static final int FLUID_TRANSFER_MB = FluidType.BUCKET_VOLUME;
    private static final long INTERNAL_FLUID_CAPACITY_MB = 100L * FluidType.BUCKET_VOLUME;
    private static final double REMOTE_POV_BLOCK_REACH = 4.5D;
    private static final double REMOTE_POV_EPSILON = 0.1D;
    private static final double FUNNEL_RADIUS = 2.0D;
    private static final int FUNNEL_MAX_ENTITIES_PER_TICK = 24;
    private static final int FUNNEL_MAX_ITEMS_PER_TICK = 48;
    private static final int FUNNEL_BUFFER_MAX_STACKS = 16;
    private static final int FUNNEL_TICK_INTERVAL = 2;
    private static final int SHIFT_IMPORT_MAX_CRAFT_ITERATIONS = 64;
    private static final long QUEST_DETECT_COOLDOWN_TICKS = 60L;
    private static final int PLAYER_HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_MAIN_INVENTORY_END_EXCLUSIVE = 36;
    private static final String CATEGORY_ALL = "all";
    private static final String CATEGORY_MOD_PREFIX = "mod|";
    private static final String CATEGORY_TAB_PREFIX = "tab|";

    private static final String NBT_ROOT = "rtsbuilding_storage_session";
    private static final String NBT_LINKED_POSITIONS = "linked_positions";
    private static final String NBT_LINKED_DIMENSION = "linked_dimension";
    private static final String NBT_INTERNAL_FLUIDS = "internal_fluids";
    private static final String NBT_FLUID_ID = "id";
    private static final String NBT_FLUID_AMOUNT = "amount";

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> ITEM_CREATIVE_TAB_CACHE = new ConcurrentHashMap<>();

    private RtsStorageManager() {
    }

    public static void onRtsEnabled(ServerPlayer player) {
        Session session = getOrCreateSession(player);
        sanitizeSessionDimension(player, session);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void onRtsDisabled(ServerPlayer player) {
        // Keep linked-storage state across RTS toggles, but stop active mining.
        Session session = getOrCreateSession(player);
        stopActiveMining(player, session);
        disableFunnelAndFlushBuffer(player, session);
        saveSessionToPlayerNbt(player, session);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            disableFunnelAndFlushBuffer(player, session);
            saveSessionToPlayerNbt(player, session);
        }
        SESSIONS.remove(player.getUUID());
    }

    private static Session getOrCreateSession(ServerPlayer player) {
        Session existing = SESSIONS.get(player.getUUID());
        if (existing != null) {
            return existing;
        }
        Session created = new Session();
        loadSessionFromPlayerNbt(player, created);
        SESSIONS.put(player.getUUID(), created);
        return created;
    }

    private static void loadSessionFromPlayerNbt(ServerPlayer player, Session session) {
        CompoundTag root = player.getPersistentData().getCompound(NBT_ROOT);
        if (root.isEmpty()) {
            return;
        }

        session.linkedPositions.clear();
        session.linkedNames.clear();
        for (long packedPos : root.getLongArray(NBT_LINKED_POSITIONS)) {
            BlockPos pos = BlockPos.of(packedPos).immutable();
            if (!session.linkedPositions.contains(pos)) {
                session.linkedPositions.add(pos);
            }
        }

        String dimensionId = root.getString(NBT_LINKED_DIMENSION);
        if (!dimensionId.isBlank()) {
            ResourceLocation key = ResourceLocation.tryParse(dimensionId);
            if (key != null) {
                session.linkedDimension = ResourceKey.create(Registries.DIMENSION, key);
            }
        }

        session.internalFluidMb.clear();
        ListTag fluidEntries = root.getList(NBT_INTERNAL_FLUIDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < fluidEntries.size(); i++) {
            CompoundTag fluidTag = fluidEntries.getCompound(i);
            String fluidId = fluidTag.getString(NBT_FLUID_ID);
            long amount = fluidTag.getLong(NBT_FLUID_AMOUNT);
            if (fluidId == null || fluidId.isBlank() || amount <= 0L) {
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(fluidId);
            if (key == null || !BuiltInRegistries.FLUID.containsKey(key)) {
                continue;
            }
            session.internalFluidMb.put(fluidId, Math.min(amount, INTERNAL_FLUID_CAPACITY_MB));
        }
    }

    private static void saveSessionToPlayerNbt(ServerPlayer player, Session session) {
        CompoundTag root = new CompoundTag();

        long[] linkedPacked = new long[session.linkedPositions.size()];
        for (int i = 0; i < session.linkedPositions.size(); i++) {
            linkedPacked[i] = session.linkedPositions.get(i).asLong();
        }
        root.putLongArray(NBT_LINKED_POSITIONS, linkedPacked);

        if (session.linkedDimension != null) {
            root.putString(NBT_LINKED_DIMENSION, session.linkedDimension.location().toString());
        }

        ListTag fluidEntries = new ListTag();
        for (var entry : session.internalFluidMb.entrySet()) {
            String fluidId = entry.getKey();
            long amount = entry.getValue() == null ? 0L : entry.getValue();
            if (fluidId == null || fluidId.isBlank() || amount <= 0L) {
                continue;
            }
            CompoundTag fluidTag = new CompoundTag();
            fluidTag.putString(NBT_FLUID_ID, fluidId);
            fluidTag.putLong(NBT_FLUID_AMOUNT, amount);
            fluidEntries.add(fluidTag);
        }
        root.put(NBT_INTERNAL_FLUIDS, fluidEntries);

        player.getPersistentData().put(NBT_ROOT, root);
    }

    public static void tickMining(MinecraftServer server) {
        for (var entry : SESSIONS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            Session session = entry.getValue();
            tickActiveMining(player, session);
            tickFunnel(player, session);
        }
    }

    public static void setMode(ServerPlayer player, BuilderMode mode) {
        Session session = getOrCreateSession(player);
        session.mode = mode;
        if (mode != BuilderMode.FUNNEL && session.funnelEnabled) {
            disableFunnelAndFlushBuffer(player, session);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        }
    }

    public static void setFunnelEnabled(ServerPlayer player, boolean enabled) {
        Session session = getOrCreateSession(player);
        if (session.funnelEnabled == enabled) {
            return;
        }
        if (enabled) {
            session.funnelEnabled = true;
            session.funnelTickCooldown = 0;
        } else {
            disableFunnelAndFlushBuffer(player, session);
        }
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void updateFunnelTarget(ServerPlayer player, BlockPos target) {
        Session session = getOrCreateSession(player);
        if (!session.funnelEnabled || target == null) {
            return;
        }
        session.funnelTarget = target.immutable();
    }

    public static void setAutoStoreMinedDrops(ServerPlayer player, boolean enabled) {
        Session session = getOrCreateSession(player);
        session.autoStoreMinedDrops = enabled;
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static BuilderMode getMode(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? BuilderMode.INTERACT : session.mode;
    }

    public static void linkStorage(ServerPlayer player, BlockPos pos) {
        if (!canAccessWorldTarget(player, pos)) {
            return;
        }

        Session session = getOrCreateSession(player);
        sanitizeSessionDimension(player, session);

        IItemHandler itemHandler = findHandler(player, pos);
        IFluidHandler fluidHandler = findFluidHandler(player, pos);
        if (itemHandler == null && fluidHandler == null) {
            requestPage(player, 0, session.search, session.category, session.sort, session.ascending);
            return;
        }

        BlockPos immutable = pos.immutable();
        if (session.linkedPositions.contains(immutable)) {
            session.linkedPositions.remove(immutable);
            session.linkedNames.remove(immutable);
            if (session.linkedPositions.isEmpty()) {
                session.linkedDimension = null;
            }
        } else {
            if (session.linkedDimension == null) {
                session.linkedDimension = player.serverLevel().dimension();
            }
            session.linkedPositions.add(immutable);
            session.linkedNames.put(immutable, resolveDisplayName(player, immutable));
        }
        saveSessionToPlayerNbt(player, session);
        requestPage(player, 0, session.search, session.category, session.sort, session.ascending);
    }

    public static void openCraftTerminal(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (session.linkedPositions.isEmpty()) {
            player.displayClientMessage(Component.literal("Link at least one storage first."), true);
            return;
        }

        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new RtsCraftTerminalMenu(
                        containerId,
                        inventory,
                        new ContainerLevelAccess() {
                            @Override
                            public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> evaluator) {
                                return Optional.ofNullable(evaluator.apply(player.serverLevel(), player.blockPosition()));
                            }

                            @Override
                            public void execute(BiConsumer<Level, BlockPos> consumer) {
                                consumer.accept(player.serverLevel(), player.blockPosition());
                            }
                        }),
                Component.literal("RTS Craft Terminal")));
        relaxOpenedMenuValidation(player.containerMenu);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void detectQuests(ServerPlayer player, byte mode) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !RtsCameraManager.isActive(player)) {
            return;
        }
        runQuestDetect(player, session, true);
    }

    public static long countLinkedItemsMatching(ServerPlayer player, Predicate<ItemStack> predicate) {
        if (player == null || predicate == null) {
            return 0L;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return 0L;
        }
        sanitizeSessionDimension(player, session);
        if (session.linkedPositions.isEmpty()) {
            return 0L;
        }

        long total = 0L;
        List<LinkedHandler> linked = resolveLinkedHandlers(player, session);
        for (LinkedHandler linkedHandler : linked) {
            IItemHandler handler = linkedHandler.handler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!predicate.test(stack)) {
                    continue;
                }
                total += stack.getCount();
            }
        }
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!predicate.test(stack)) {
                continue;
            }
            total += stack.getCount();
        }
        return total;
    }

    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending) {
        Session session = getOrCreateSession(player);
        session.search = search == null ? "" : search;
        session.category = normalizeCategory(category);
        session.sort = sort;
        session.ascending = ascending;

        sanitizeSessionDimension(player, session);

        List<LinkedHandler> activeHandlers = resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = resolveLinkedFluidHandlers(player, session);
        List<Long> linkedPackedPositions = toPackedPositions(session.linkedPositions);

        Map<String, Long> counts = new HashMap<>();
        Map<String, Long> namespaceTotals = new HashMap<>();
        for (LinkedHandler linked : activeHandlers) {
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
                counts.merge(id.toString(), (long) stack.getCount(), Long::sum);
                namespaceTotals.merge(id.getNamespace(), (long) stack.getCount(), Long::sum);
            }
        }
        boolean includePlayerMainInventory = !session.linkedPositions.isEmpty()
                && !(player.containerMenu instanceof RtsCraftTerminalMenu);
        if (includePlayerMainInventory) {
            accumulatePlayerMainInventoryCounts(player, counts, namespaceTotals);
        }

        Map<String, Long> fluidAmounts = new HashMap<>();
        Map<String, Long> fluidCapacities = new HashMap<>();

        for (var entry : session.internalFluidMb.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            fluidAmounts.merge(entry.getKey(), entry.getValue(), Long::sum);
        }

        for (LinkedFluidHandler linked : activeFluidHandlers) {
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
                fluidAmounts.merge(fluidId, (long) fluid.getAmount(), Long::sum);
                fluidCapacities.merge(fluidId, (long) Math.max(0, handler.getTankCapacity(tank)), Long::sum);
            }
        }

        for (String fluidId : fluidAmounts.keySet()) {
            fluidCapacities.merge(fluidId, INTERNAL_FLUID_CAPACITY_MB, Long::sum);
            ResourceLocation rl = ResourceLocation.tryParse(fluidId);
            if (rl != null) {
                namespaceTotals.merge(rl.getNamespace(), fluidAmounts.getOrDefault(fluidId, 0L), Long::sum);
            }
        }

        // Creative tab contents are client-oriented and may be uninitialized on dedicated/server runtime.
        // Rebuild on demand so tab.contains(...) is meaningful for category filtering.
        CreativeModeTabs.tryRebuildTabContents(
                player.serverLevel().enabledFeatures(),
                player.canUseGameMasterBlocks(),
                player.serverLevel().registryAccess());

        Map<String, Set<String>> itemTabKeys = new HashMap<>();
        Map<String, Set<String>> modTabKeys = new HashMap<>();
        for (String itemId : counts.keySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(rl);
            Set<String> tabs = resolveCreativeTabKeys(itemId, item);
            if (tabs.isEmpty()) {
                continue;
            }
            Set<String> copied = new HashSet<>(tabs);
            itemTabKeys.put(itemId, copied);
            modTabKeys.computeIfAbsent(rl.getNamespace(), ignored -> new HashSet<>()).addAll(copied);
        }

        List<String> namespaces = new ArrayList<>(namespaceTotals.keySet());
        namespaces.sort(RtsStorageManager::compareNamespace);

        List<String> categories = new ArrayList<>();
        categories.add(CATEGORY_ALL);
        for (String namespace : namespaces) {
            categories.add(encodeModCategory(namespace));
            List<String> tabs = new ArrayList<>(modTabKeys.getOrDefault(namespace, Set.of()));
            tabs.sort(RtsStorageManager::compareTabKey);
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
        for (var e : counts.entrySet()) {
            String id = e.getKey();
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (!matchesSearchQuery(rl, id, query)) {
                continue;
            }
            String namespace = rl == null ? "unknown" : rl.getNamespace();
            String path = rl == null ? id : rl.getPath();
            Set<String> tabs = itemTabKeys.getOrDefault(id, Set.of());
            if (!selectedCategory.matches(namespace, tabs)) {
                continue;
            }
            entries.add(new Entry(id, namespace, path, e.getValue()));
        }

        List<FluidEntry> fluidEntries = new ArrayList<>();
        for (var e : fluidAmounts.entrySet()) {
            String id = e.getKey();
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (!matchesSearchQuery(rl, id, query)) {
                continue;
            }
            String namespace = rl == null ? "unknown" : rl.getNamespace();
            String path = rl == null ? id : rl.getPath();
            if (selectedCategory.isCreativeTab() || !selectedCategory.matches(namespace, Set.of())) {
                continue;
            }
            long amount = Math.max(0L, e.getValue());
            long capacity = Math.max(amount, fluidCapacities.getOrDefault(id, INTERNAL_FLUID_CAPACITY_MB));
            fluidEntries.add(new FluidEntry(id, namespace, path, amount, capacity));
        }

        Comparator<Entry> comparator = switch (sort) {
            case MOD -> Comparator.comparing(Entry::namespace).thenComparing(Entry::path);
            case NAME -> Comparator.comparing(Entry::path).thenComparing(Entry::namespace);
            case QUANTITY -> Comparator.comparingLong(Entry::count).thenComparing(Entry::path);
        };
        if (sort == RtsStorageSort.QUANTITY && !ascending) {
            comparator = comparator.reversed();
        } else if (sort != RtsStorageSort.QUANTITY && !ascending) {
            comparator = comparator.reversed();
        }
        entries.sort(comparator);

        Comparator<FluidEntry> fluidComparator = switch (sort) {
            case MOD -> Comparator.comparing(FluidEntry::namespace).thenComparing(FluidEntry::path);
            case NAME -> Comparator.comparing(FluidEntry::path).thenComparing(FluidEntry::namespace);
            case QUANTITY -> Comparator.comparingLong(FluidEntry::amount).thenComparing(FluidEntry::path);
        };
        if (!ascending) {
            fluidComparator = fluidComparator.reversed();
        }
        fluidEntries.sort(fluidComparator);

        int totalEntries = entries.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalEntries / (double) PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = safePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, totalEntries);

        List<String> itemIds = new ArrayList<>();
        List<Long> itemCounts = new ArrayList<>();
        for (int i = from; i < to; i++) {
            Entry e = entries.get(i);
            itemIds.add(e.itemId());
            itemCounts.add(e.count());
        }

        List<String> fluidIds = new ArrayList<>(fluidEntries.size());
        List<Long> fluidAmountList = new ArrayList<>(fluidEntries.size());
        List<Long> fluidCapacityList = new ArrayList<>(fluidEntries.size());
        for (FluidEntry entry : fluidEntries) {
            fluidIds.add(entry.fluidId());
            fluidAmountList.add(entry.amount());
            fluidCapacityList.add(entry.capacity());
        }

        Map<String, Long> funnelBufferSummary = summarizeFunnelBuffer(session);
        List<String> funnelBufferItemIds = new ArrayList<>(funnelBufferSummary.size());
        List<Long> funnelBufferCounts = new ArrayList<>(funnelBufferSummary.size());
        for (var entry : funnelBufferSummary.entrySet()) {
            funnelBufferItemIds.add(entry.getKey());
            funnelBufferCounts.add(entry.getValue());
        }

        PacketDistributor.sendToPlayer(player, new S2CRtsStoragePagePayload(
                !session.linkedPositions.isEmpty(),
                buildLinkedSummary(session),
                linkedPackedPositions,
                safePage,
                totalPages,
                totalEntries,
                session.search,
                session.category,
                (byte) session.sort.ordinal(),
                session.ascending,
                session.autoStoreMinedDrops,
                categories,
                itemIds,
                itemCounts,
                fluidIds,
                fluidAmountList,
                fluidCapacityList,
                session.funnelEnabled,
                funnelBufferItemIds,
                funnelBufferCounts));

        session.page = safePage;
    }

    private static void sendEmptyPage(ServerPlayer player, Session session) {
        PacketDistributor.sendToPlayer(player, new S2CRtsStoragePagePayload(
                !session.linkedPositions.isEmpty(),
                buildLinkedSummary(session),
                toPackedPositions(session.linkedPositions),
                0,
                1,
                0,
                session.search,
                session.category,
                (byte) session.sort.ordinal(),
                session.ascending,
                session.autoStoreMinedDrops,
                List.of(CATEGORY_ALL),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                session.funnelEnabled,
                List.of(),
                List.of()));
    }

    public static void requestCraftables(ServerPlayer player, String search, boolean showUnavailable) {
        Session session = getOrCreateSession(player);
        session.craftSearch = search == null ? "" : search;
        session.craftShowUnavailable = showUnavailable;

        sanitizeSessionDimension(player, session);
        if (session.linkedPositions.isEmpty()) {
            sendCraftables(player, session, List.of());
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            sendCraftables(player, session, List.of());
            return;
        }

        Map<String, Long> availableCounts = summarizeAvailableCraftItems(player, session, activeLinked);
        Map<String, CraftableCandidate> byResultItem = new LinkedHashMap<>();
        for (RecipeHolder<CraftingRecipe> holder : player.serverLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!supportsWorkbenchCraftPanelRecipe(holder.value())) {
                continue;
            }
            CraftableCandidate candidate = buildCraftableCandidate(player, holder, availableCounts, session.craftSearch);
            if (candidate == null) {
                continue;
            }
            CraftableCandidate existing = byResultItem.get(candidate.resultItemId());
            if (existing == null || candidate.isPreferredOver(existing)) {
                byResultItem.put(candidate.resultItemId(), candidate);
            }
        }

        List<CraftableCandidate> candidates = new ArrayList<>(byResultItem.values());
        candidates.removeIf(candidate -> !session.craftShowUnavailable && !candidate.craftable());
        candidates.sort(CraftableCandidate::compareForPanel);
        sendCraftables(player, session, candidates);
    }

    public static void craftRecipeToLinked(ServerPlayer player, String recipeId) {
        Session session = getOrCreateSession(player);
        sanitizeSessionDimension(player, session);
        if (session.linkedPositions.isEmpty()) {
            requestCraftables(player, session.craftSearch, session.craftShowUnavailable);
            return;
        }
        if (recipeId == null || recipeId.isBlank()) {
            requestCraftables(player, session.craftSearch, session.craftShowUnavailable);
            return;
        }

        ResourceLocation key = ResourceLocation.tryParse(recipeId);
        if (key == null) {
            requestCraftables(player, session.craftSearch, session.craftShowUnavailable);
            return;
        }

        RecipeHolder<?> raw = player.serverLevel().getRecipeManager().byKey(key).orElse(null);
        if (raw == null || !(raw.value() instanceof CraftingRecipe craftingRecipe)) {
            requestCraftables(player, session.craftSearch, session.craftShowUnavailable);
            return;
        }
        if (!supportsWorkbenchCraftPanelRecipe(craftingRecipe)) {
            requestCraftables(player, session.craftSearch, session.craftShowUnavailable);
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            requestCraftables(player, session.craftSearch, session.craftShowUnavailable);
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        boolean includePlayerFallback = !session.linkedPositions.isEmpty()
                && !(player.containerMenu instanceof RtsCraftTerminalMenu);
        CraftExecutionResult result = craftSingleRecipeToLinked(player, handlers, craftingRecipe, includePlayerFallback);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        requestCraftables(player, session.craftSearch, session.craftShowUnavailable);
        if (!result.success()) {
            if (result.storageFull()) {
                player.displayClientMessage(Component.literal("Craft: linked storage is full."), true);
            }
            return;
        }

        PacketDistributor.sendToPlayer(player, new S2CRtsCraftFeedbackPayload(result.resultItemId(), result.resultCount()));
        runQuestDetect(player, session, false);
    }

    private static void sendCraftables(ServerPlayer player, Session session, List<CraftableCandidate> candidates) {
        List<String> recipeIds = new ArrayList<>(candidates.size());
        List<String> resultItemIds = new ArrayList<>(candidates.size());
        List<Integer> resultCounts = new ArrayList<>(candidates.size());
        List<Boolean> craftable = new ArrayList<>(candidates.size());
        List<String> missingSummaries = new ArrayList<>(candidates.size());
        for (CraftableCandidate candidate : candidates) {
            recipeIds.add(candidate.recipeId());
            resultItemIds.add(candidate.resultItemId());
            resultCounts.add(candidate.resultCount());
            craftable.add(candidate.craftable());
            missingSummaries.add(candidate.missingSummary());
        }

        PacketDistributor.sendToPlayer(player, new S2CRtsCraftablesPayload(
                session.craftSearch,
                session.craftShowUnavailable,
                recipeIds,
                resultItemIds,
                resultCounts,
                craftable,
                missingSummaries));
    }

    private static Map<String, Long> summarizeAvailableCraftItems(ServerPlayer player, Session session, List<LinkedHandler> activeLinked) {
        Map<String, Long> counts = new HashMap<>();
        for (LinkedHandler linked : activeLinked) {
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
                counts.merge(id.toString(), (long) stack.getCount(), Long::sum);
            }
        }

        boolean includePlayerMainInventory = !session.linkedPositions.isEmpty()
                && !(player.containerMenu instanceof RtsCraftTerminalMenu);
        if (includePlayerMainInventory) {
            accumulatePlayerMainInventoryCounts(player, counts, new HashMap<>());
        }
        return counts;
    }

    private static CraftableCandidate buildCraftableCandidate(ServerPlayer player, RecipeHolder<CraftingRecipe> holder,
            Map<String, Long> availableCounts, String search) {
        if (player == null || holder == null || holder.value() == null) {
            return null;
        }

        ItemStack result = resolveCraftablePreviewResult(holder.value(), player);
        if (result.isEmpty()) {
            return null;
        }

        ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
        if (resultId == null) {
            return null;
        }
        String resultLabel = result.getHoverName().getString();
        if (!matchesCraftablesSearch(resultId, resultLabel, search)) {
            return null;
        }

        RecipeAvailability availability = evaluateRecipeAvailability(holder.value(), availableCounts);
        return new CraftableCandidate(
                holder.id().toString(),
                resultId.toString(),
                Math.max(1, result.getCount()),
                resultLabel,
                availability.craftable(),
                availability.missingSummary(),
                availability.missingTotal());
    }

    private static boolean supportsWorkbenchCraftPanelRecipe(CraftingRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            return false;
        }

        if (recipe instanceof ShapedRecipe shaped) {
            if (shaped.getWidth() < 1 || shaped.getWidth() > 3 || shaped.getHeight() < 1 || shaped.getHeight() > 3) {
                return false;
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            if (shapeless.getIngredients().isEmpty() || shapeless.getIngredients().size() > 9) {
                return false;
            }
        } else {
            return false;
        }

        boolean anyNonEmpty = false;
        for (Ingredient ingredient : mapCraftingIngredients(recipe)) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            anyNonEmpty = true;
        }
        return anyNonEmpty;
    }

    private static ItemStack resolveCraftablePreviewResult(CraftingRecipe recipe, ServerPlayer player) {
        if (recipe == null || player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack result = recipe.getResultItem(player.registryAccess());
        if (!result.isEmpty()) {
            return result.copy();
        }

        Ingredient[] mapped = mapCraftingIngredients(recipe);
        List<ItemStack> previewStacks = new ArrayList<>(9);
        for (Ingredient ingredient : mapped) {
            if (ingredient == null || ingredient.isEmpty()) {
                previewStacks.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack[] options = ingredient.getItems();
            if (options.length <= 0 || options[0].isEmpty()) {
                return ItemStack.EMPTY;
            }
            previewStacks.add(options[0].copyWithCount(1));
        }

        ItemStack assembled = recipe.assemble(CraftingInput.of(3, 3, previewStacks), player.registryAccess());
        return assembled.isEmpty() ? ItemStack.EMPTY : assembled.copy();
    }

    private static RecipeAvailability evaluateRecipeAvailability(CraftingRecipe recipe, Map<String, Long> availableCounts) {
        Ingredient[] required = mapCraftingIngredients(recipe);
        Map<String, Long> remaining = new HashMap<>(availableCounts);
        Map<String, ItemStack> testStackCache = new HashMap<>();
        Map<String, Integer> missing = new LinkedHashMap<>();
        int missingTotal = 0;
        for (Ingredient ingredient : required) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            String matchedId = selectBestIngredientMatch(ingredient, remaining, testStackCache);
            if (matchedId != null) {
                remaining.computeIfPresent(matchedId, (id, count) -> count > 1L ? count - 1L : null);
                continue;
            }
            missing.merge(resolveIngredientLabel(ingredient), 1, Integer::sum);
            missingTotal++;
        }

        if (missingTotal <= 0) {
            return new RecipeAvailability(true, "", 0);
        }
        return new RecipeAvailability(false, buildMissingSummary(missing), missingTotal);
    }

    private static boolean matchesCraftablesSearch(ResourceLocation resultId, String resultLabel, String search) {
        String query = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) {
            return true;
        }
        if (query.startsWith("@")) {
            String modQuery = query.substring(1).trim();
            if (modQuery.isEmpty()) {
                return true;
            }
            return resultId.getNamespace().toLowerCase(Locale.ROOT).contains(modQuery);
        }

        String rawId = resultId.toString().toLowerCase(Locale.ROOT);
        String label = resultLabel == null ? "" : resultLabel.toLowerCase(Locale.ROOT);
        return rawId.contains(query) || label.contains(query);
    }

    private static String selectBestIngredientMatch(Ingredient ingredient, Map<String, Long> remaining, Map<String, ItemStack> testStackCache) {
        String bestId = null;
        long bestCount = 0L;
        for (var entry : remaining.entrySet()) {
            long available = entry.getValue() == null ? 0L : entry.getValue();
            if (available <= 0L) {
                continue;
            }
            ItemStack probe = resolveIngredientProbeStack(entry.getKey(), testStackCache);
            if (probe.isEmpty() || !ingredient.test(probe)) {
                continue;
            }
            if (bestId == null || available > bestCount) {
                bestId = entry.getKey();
                bestCount = available;
            }
        }
        return bestId;
    }

    private static ItemStack resolveIngredientProbeStack(String itemId, Map<String, ItemStack> testStackCache) {
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        ItemStack cached = testStackCache.get(itemId);
        if (cached != null) {
            return cached;
        }

        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            testStackCache.put(itemId, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }

        ItemStack resolved = new ItemStack(BuiltInRegistries.ITEM.get(key));
        testStackCache.put(itemId, resolved);
        return resolved;
    }

    private static String resolveIngredientLabel(Ingredient ingredient) {
        for (ItemStack option : ingredient.getItems()) {
            if (!option.isEmpty()) {
                return option.getHoverName().getString();
            }
        }
        return "Ingredient";
    }

    private static String buildMissingSummary(Map<String, Integer> missing) {
        if (missing.isEmpty()) {
            return "";
        }
        StringBuilder summary = new StringBuilder("Missing: ");
        int index = 0;
        int total = missing.size();
        for (var entry : missing.entrySet()) {
            if (index > 0) {
                summary.append(", ");
            }
            summary.append(entry.getKey()).append(" x").append(entry.getValue());
            index++;
            if (index >= 3 && total > index) {
                summary.append("...");
                break;
            }
        }
        return summary.toString();
    }

    private static String normalizeCategory(String category) {
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

    private static boolean matchesSearchQuery(ResourceLocation id, String rawId, String query) {
        if (query == null || query.isEmpty()) {
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
        String normalizedId = rawId == null ? "" : rawId.toLowerCase(Locale.ROOT);
        return normalizedId.contains(query);
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

    private static Set<String> resolveCreativeTabKeys(String itemId, Item item) {
        return ITEM_CREATIVE_TAB_CACHE.computeIfAbsent(itemId, ignored -> {
            ItemStack probe = new ItemStack(item);
            Set<String> tabKeys = new HashSet<>();
            for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
                if (tab == null || tab.getType() != CreativeModeTab.Type.CATEGORY || !tab.shouldDisplay()) {
                    continue;
                }
                if (!tab.contains(probe)) {
                    continue;
                }
                ResourceLocation key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
                if (key != null) {
                    tabKeys.add(key.toString());
                }
            }
            return tabKeys;
        });
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

    public static void placeSelected(ServerPlayer player, BlockPos clickedPos, Direction face, double hitX, double hitY,
            double hitZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied, String itemId,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !canAccessWorldTarget(player, clickedPos)) {
            return;
        }
        sanitizeSessionDimension(player, session);
        boolean useSelectedStorageItem = itemId != null && !itemId.isBlank();

        ServerLevel level = player.serverLevel();
        Vec3 hitLocation = new Vec3(hitX, hitY, hitZ);
        BlockHitResult hit = new BlockHitResult(hitLocation, face, clickedPos, false);
        Vec3 interactionPos = resolveInteractionPosition(null, hit, hitLocation);
        RayContext rayContext = parseRayContext(
                rayOriginX, rayOriginY, rayOriginZ,
                rayDirX, rayDirY, rayDirZ);

        if (!useSelectedStorageItem) {
            if (skipIfOccupied && player.getMainHandItem().getItem() instanceof BlockItem) {
                if (!level.hasChunkAt(clickedPos) || !level.getBlockState(clickedPos).canBeReplaced()) {
                    requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
                    return;
                }
            }

            BlockState beforeClicked = level.getBlockState(clickedPos);
            BlockPos adjacentPos = clickedPos.relative(face);
            BlockState beforeAdjacent = level.hasChunkAt(adjacentPos) ? level.getBlockState(adjacentPos) : null;

            AbstractContainerMenu menuBeforeMainHandUse = player.containerMenu;
            InteractionResult mainHandUse = withTemporaryUseItemContext(
                    player,
                    interactionPos,
                    hitLocation,
                    rayContext,
                    REMOTE_POV_BLOCK_REACH,
                    () -> withTemporaryShiftKey(player, forcePlace, () -> player.gameMode.useItemOn(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND,
                            hit)));
            AbstractContainerMenu menuAfterMainHandUse = player.containerMenu;
            if (menuAfterMainHandUse != menuBeforeMainHandUse) {
                relaxOpenedMenuValidation(menuAfterMainHandUse);
                return;
            }

            if (mainHandUse.consumesAction()) {
                BlockPos placedPos = detectPlacedPos(level, clickedPos, beforeClicked, adjacentPos, beforeAdjacent);
                if (placedPos != null) {
                    PlacedBlockTrackerData.get(level).mark(placedPos);
                }
                return;
            }

            // Some items (e.g. bucket) work via "use in air" fallback instead of use-on-block.
            AbstractContainerMenu menuBeforeUseFallback = player.containerMenu;
            InteractionResult mainHandUseFallback = withTemporaryUseItemContext(
                    player,
                    interactionPos,
                    hitLocation,
                    rayContext,
                    REMOTE_POV_BLOCK_REACH,
                    () -> withTemporaryShiftKey(player, forcePlace, () -> player.gameMode.useItem(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND)));
            AbstractContainerMenu menuAfterUseFallback = player.containerMenu;
            if (menuAfterUseFallback != menuBeforeUseFallback) {
                relaxOpenedMenuValidation(menuAfterUseFallback);
                return;
            }
            if (mainHandUseFallback.consumesAction()) {
                return;
            }

            return;
        }

        if (session.linkedPositions.isEmpty()) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }

        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return;
        }

        Item item = BuiltInRegistries.ITEM.get(id);
        if (skipIfOccupied && item instanceof BlockItem) {
            if (!level.hasChunkAt(clickedPos) || !level.getBlockState(clickedPos).canBeReplaced()) {
                requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
                return;
            }
        }
        ItemStack extracted = extractOneFromNetwork(handlers, player, item);
        if (extracted.isEmpty()) {
            return;
        }

        BlockState beforeClicked = level.getBlockState(clickedPos);
        BlockPos adjacentPos = clickedPos.relative(face);
        BlockState beforeAdjacent = level.hasChunkAt(adjacentPos) ? level.getBlockState(adjacentPos) : null;

        AbstractContainerMenu menuBeforeSelectedUse = player.containerMenu;
        UseOnOutcome selectedOutcome = withTemporaryUseItemContext(
                player,
                interactionPos,
                hitLocation,
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> useItemOnWithMainHand(player, level, extracted, hit, forcePlace));
        AbstractContainerMenu menuAfterSelectedUse = player.containerMenu;
        if (menuAfterSelectedUse != menuBeforeSelectedUse) {
            relaxOpenedMenuValidation(menuAfterSelectedUse);
        }

        UseOnOutcome finalOutcome = selectedOutcome;
        if (!selectedOutcome.result().consumesAction()) {
            ItemStack fallbackStack = selectedOutcome.remainder().isEmpty() ? extracted.copy() : selectedOutcome.remainder().copy();
            finalOutcome = withTemporaryUseItemContext(
                    player,
                    interactionPos,
                    hitLocation,
                    rayContext,
                    REMOTE_POV_BLOCK_REACH,
                    () -> useItemWithMainHand(player, level, fallbackStack, forcePlace));
        }
        if (!finalOutcome.remainder().isEmpty()) {
            refundToLinked(handlers, player, finalOutcome.remainder());
        }

        if (!finalOutcome.result().consumesAction()) {
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        BlockPos placedPos = detectPlacedPos(level, clickedPos, beforeClicked, adjacentPos, beforeAdjacent);
        if (placedPos != null) {
            rotatePlacedBlock(level, placedPos, rotateSteps);
            PlacedBlockTrackerData.get(level).mark(placedPos);
        }

        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    private static BlockPos resolvePlacementTargetPos(ServerLevel level, BlockPos clickedPos, Direction face) {
        if (level == null || clickedPos == null || face == null) {
            return null;
        }
        if (!level.hasChunkAt(clickedPos)) {
            return clickedPos;
        }
        return level.getBlockState(clickedPos).canBeReplaced() ? clickedPos : clickedPos.relative(face);
    }

    public static void storeFluidFromContainer(ServerPlayer player, byte sourceType, byte toolSlot, String itemId) {
        Session session = getOrCreateSession(player);
        if (!RtsCameraManager.isActive(player)) {
            return;
        }
        sanitizeSessionDimension(player, session);

        List<LinkedHandler> activeItemHandlers = resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = resolveLinkedFluidHandlers(player, session);
        List<IItemHandler> itemHandlers = new ArrayList<>(activeItemHandlers.size());
        for (LinkedHandler linked : activeItemHandlers) {
            itemHandlers.add(linked.handler());
        }

        boolean changed = switch (sourceType) {
            case C2SRtsStoreFluidPayload.SOURCE_STORAGE_ITEM, C2SRtsStoreFluidPayload.SOURCE_PIN_ITEM ->
                storeFluidFromLinkedItem(player, session, itemHandlers, activeFluidHandlers, itemId);
            case C2SRtsStoreFluidPayload.SOURCE_TOOL_SLOT ->
                storeFluidFromToolSlot(player, session, activeFluidHandlers, clampHotbarSlot(toolSlot));
            default -> false;
        };
        if (changed) {
            saveSessionToPlayerNbt(player, session);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        }
    }

    public static void placeFluid(ServerPlayer player, BlockPos clickedPos, Direction face, double hitX, double hitY,
            double hitZ, boolean forcePlace, String fluidId,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !canAccessFluidPlacementTarget(player, clickedPos)) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (fluidId == null || fluidId.isBlank()) {
            return;
        }

        ResourceLocation fluidKey = ResourceLocation.tryParse(fluidId);
        if (fluidKey == null || !BuiltInRegistries.FLUID.containsKey(fluidKey)) {
            return;
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidKey);
        if (fluid == null) {
            return;
        }

        List<LinkedFluidHandler> activeFluidHandlers = resolveLinkedFluidHandlers(player, session);
        if (extractFluidFromNetwork(session, activeFluidHandlers, fluid, FLUID_TRANSFER_MB, false) < FLUID_TRANSFER_MB) {
            return;
        }

        ServerLevel level = player.serverLevel();
        FluidStack transfer = new FluidStack(fluid, FLUID_TRANSFER_MB);

        int filledIntoBlock = fillFluidHandlerAtTarget(level, clickedPos, face, transfer);
        if (filledIntoBlock > 0) {
            int consumed = extractFluidFromNetwork(session, activeFluidHandlers, fluid, filledIntoBlock, true);
            if (consumed > 0) {
                saveSessionToPlayerNbt(player, session);
                requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            }
            return;
        }

        BlockHitResult hit = new BlockHitResult(new Vec3(hitX, hitY, hitZ), face, clickedPos, false);
        BlockPos placePos = resolveFluidPlacementPos(level, player, hit, transfer);
        if (placePos == null) {
            return;
        }

        if (!placeFluidBlock(level, player, placePos, transfer)) {
            return;
        }

        int extracted = extractFluidFromNetwork(session, activeFluidHandlers, fluid, FLUID_TRANSFER_MB, true);
        if (extracted > 0) {
            saveSessionToPlayerNbt(player, session);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        }
    }

    public static void interactTarget(ServerPlayer player, int entityId, BlockPos clickedPos, Direction face, double hitX,
            double hitY, double hitZ, byte sourceType, byte toolSlot, String itemId,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !RtsCameraManager.isActive(player)) {
            return;
        }
        sanitizeSessionDimension(player, session);
        RayContext rayContext = parseRayContext(
                rayOriginX, rayOriginY, rayOriginZ,
                rayDirX, rayDirY, rayDirZ);

        ServerLevel level = player.serverLevel();
        Entity targetEntity = null;
        BlockHitResult blockHit = null;
        BlockPos effectiveBlockPos = null;
        BlockState beforeClicked = null;
        BlockPos adjacentPos = null;
        BlockState beforeAdjacent = null;

        if (entityId >= 0) {
            targetEntity = level.getEntity(entityId);
            if (targetEntity == null || !targetEntity.isAlive()) {
                return;
            }
            effectiveBlockPos = targetEntity.blockPosition();
            if (!level.hasChunkAt(effectiveBlockPos) || !level.mayInteract(player, effectiveBlockPos)) {
                return;
            }
        } else {
            if (clickedPos == null || !canAccessWorldTarget(player, clickedPos)) {
                return;
            }
            effectiveBlockPos = clickedPos.immutable();
            blockHit = new BlockHitResult(new Vec3(hitX, hitY, hitZ), face, effectiveBlockPos, false);
            beforeClicked = level.getBlockState(effectiveBlockPos);
            adjacentPos = effectiveBlockPos.relative(face);
            beforeAdjacent = level.hasChunkAt(adjacentPos) ? level.getBlockState(adjacentPos) : null;
        }

        InteractionResult result = InteractionResult.PASS;
        Vec3 hit = new Vec3(hitX, hitY, hitZ);
        AbstractContainerMenu menuBeforeInteract = player.containerMenu;
        if (sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT) {
            result = interactWithToolSlot(player, level, targetEntity, blockHit, hit, toolSlot, rayContext);
        } else if (sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM) {
            result = interactWithLinkedItem(player, level, session, targetEntity, blockHit, hit, itemId, rayContext);
        }
        AbstractContainerMenu menuAfterInteract = player.containerMenu;
        if (menuAfterInteract != menuBeforeInteract) {
            relaxOpenedMenuValidation(menuAfterInteract);
        }

        if (result.consumesAction() && blockHit != null && beforeClicked != null) {
            BlockPos placedPos = detectPlacedPos(level, effectiveBlockPos, beforeClicked, adjacentPos, beforeAdjacent);
            if (placedPos != null) {
                PlacedBlockTrackerData.get(level).mark(placedPos);
            }
        }

        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void returnCarriedToLinked(ServerPlayer player, String itemId, int amount) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (session.linkedPositions.isEmpty()) {
            return;
        }
        if (itemId == null || itemId.isBlank() || amount <= 0) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return;
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            return;
        }

        ResourceLocation carriedId = BuiltInRegistries.ITEM.getKey(carried.getItem());
        if (carriedId == null || !itemId.equals(carriedId.toString())) {
            return;
        }

        int returned = Math.min(amount, carried.getCount());
        if (returned <= 0) {
            return;
        }

        ItemStack toStore = carried.split(returned);
        player.containerMenu.setCarried(carried);
        OverflowOutcome overflow = storeToLinkedWithFallbackPreferExisting(handlers, player, toStore);
        if (overflow.hasOverflow()) {
            sendStorageOverflowHint(player, "Import", overflow);
        }
        player.containerMenu.broadcastChanges();
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        runQuestDetect(player, session, false);
    }

    public static void quickDropLinkedItem(ServerPlayer player, String itemId, byte amount, double dropX, double dropY,
            double dropZ) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !RtsCameraManager.isActive(player)) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        if (!Double.isFinite(dropX) || !Double.isFinite(dropY) || !Double.isFinite(dropZ)) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        int wanted = Math.max(1, Math.min(64, amount));
        ItemStack extracted = extractMatchingFromQuickDropSources(handlers, player, item, wanted);
        if (extracted.isEmpty()) {
            return;
        }

        Vec3 dropPos = new Vec3(dropX, dropY, dropZ);
        BlockPos dropBlock = BlockPos.containing(dropPos);
        if (!player.serverLevel().hasChunkAt(dropBlock) || !RtsCameraManager.isWithinActionRadius(player, dropBlock)) {
            refundToLinked(handlers, player, extracted);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        ItemEntity dropped = new ItemEntity(player.serverLevel(), dropPos.x, dropPos.y, dropPos.z, extracted);
        dropped.setPickUpDelay(10);
        player.serverLevel().addFreshEntity(dropped);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void importMenuSlotToLinked(ServerPlayer player, int menuSlot) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (session.linkedPositions.isEmpty()) {
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menuSlot < 0 || menuSlot >= menu.slots.size()) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        Slot slot = menu.slots.get(menuSlot);
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) {
            return;
        }

        OverflowOutcome overflow = OverflowOutcome.EMPTY;
        if (menu instanceof CraftingMenu && menuSlot == 0) {
            CraftingMenu craftingMenu = (CraftingMenu) menu;
            ItemStack[] craftBlueprint = snapshotCraftGridBlueprint(craftingMenu);
            ItemStack resultSnapshot = slot.getItem().copy();
            if (resultSnapshot.isEmpty()) {
                return;
            }
            ItemStack resultPrototype = resultSnapshot.copyWithCount(1);
            boolean craftedAny = false;
            for (int guard = 0; guard < SHIFT_IMPORT_MAX_CRAFT_ITERATIONS; guard++) {
                Slot resultSlot = craftingMenu.getSlot(0);
                ItemStack currentResult = resultSlot.getItem();
                if (currentResult.isEmpty() || !ItemStack.isSameItemSameComponents(currentResult, resultPrototype)) {
                    // Try one refill step so shift-craft can continue seamlessly in the same click.
                    refillCraftGridFromBlueprint(craftingMenu, handlers, player, craftBlueprint, false, false);
                    currentResult = resultSlot.getItem();
                    if (currentResult.isEmpty() || !ItemStack.isSameItemSameComponents(currentResult, resultPrototype)) {
                        break;
                    }
                }

                int[] before = snapshotPlayerMatchingCounts(player, resultPrototype);
                ItemStack moved = craftingMenu.quickMoveStack(player, menuSlot);
                if (moved.isEmpty()) {
                    break;
                }

                ItemStack gained = drainPlayerInventoryDelta(player, resultPrototype, before);
                if (gained.isEmpty()) {
                    break;
                }

                overflow = overflow.merge(storeToLinkedWithFallbackPreferExisting(handlers, player, gained));
                craftedAny = true;

                // Keep refilling from linked storage only so shift-craft is continuous and AE-like.
                refillCraftGridFromBlueprint(craftingMenu, handlers, player, craftBlueprint, false, false);
            }

            if (!craftedAny) {
                return;
            }
            refillCraftGridFromBlueprint(craftingMenu, handlers, player, craftBlueprint, true, false);
        } else {
            ItemStack inSlot = slot.getItem();
            ItemStack moved = slot.safeTake(inSlot.getCount(), inSlot.getCount(), player);
            if (moved.isEmpty()) {
                return;
            }
            overflow = storeToLinkedWithFallbackPreferExisting(handlers, player, moved);
        }

        if (overflow.hasOverflow()) {
            sendStorageOverflowHint(player, "Import", overflow);
        }
        menu.broadcastChanges();
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        runQuestDetect(player, session, false);
    }

    public static void refillCraftGridFromLinked(ServerPlayer player, CraftingMenu craftingMenu, ItemStack[] blueprint) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || craftingMenu == null || blueprint == null || blueprint.length != 9) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (session.linkedPositions.isEmpty()) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        refillCraftGridFromBlueprint(craftingMenu, handlers, player, blueprint, false, false);
        craftingMenu.broadcastChanges();
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void refillCurrentCraftGridFromBlueprintIds(ServerPlayer player, List<String> blueprintIds) {
        if (player == null || blueprintIds == null || blueprintIds.size() != 9) {
            return;
        }
        if (!(player.containerMenu instanceof CraftingMenu craftingMenu)) {
            return;
        }

        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < blueprint.length; i++) {
            String itemId = blueprintIds.get(i);
            if (itemId == null || itemId.isBlank()) {
                blueprint[i] = ItemStack.EMPTY;
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                blueprint[i] = ItemStack.EMPTY;
                continue;
            }
            blueprint[i] = new ItemStack(BuiltInRegistries.ITEM.get(key));
        }
        refillCraftGridFromLinked(player, craftingMenu, blueprint);
    }

    public static void applyJeiTransfer(ServerPlayer player, String recipeId, boolean maxTransfer, boolean clearGridFirst) {
        Session session = getOrCreateSession(player);
        sanitizeSessionDimension(player, session);
        if (!(player.containerMenu instanceof CraftingMenu craftingMenu)) {
            return;
        }
        if (recipeId == null || recipeId.isBlank()) {
            return;
        }

        ResourceLocation key = ResourceLocation.tryParse(recipeId);
        if (key == null) {
            return;
        }
        RecipeHolder<?> raw = player.serverLevel().getRecipeManager().byKey(key).orElse(null);
        if (raw == null || !(raw.value() instanceof CraftingRecipe craftingRecipe)) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        Ingredient[] required = mapCraftingIngredients(craftingRecipe);
        if (required.length != 9) {
            return;
        }

        List<ItemStack> cleared = new ArrayList<>(9);
        if (clearGridFirst) {
            for (int i = 0; i < 9; i++) {
                Slot grid = craftingMenu.getSlot(1 + i);
                ItemStack existing = grid.getItem();
                if (existing.isEmpty()) {
                    cleared.add(ItemStack.EMPTY);
                    continue;
                }
                ItemStack copy = existing.copy();
                grid.set(ItemStack.EMPTY);
                grid.setChanged();
                cleared.add(copy);
            }
        } else {
            for (int i = 0; i < 9; i++) {
                cleared.add(ItemStack.EMPTY);
            }
        }

        boolean anyInserted = false;
        int maxPasses = maxTransfer ? 64 : 1;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean passInsertedAny = false;
            for (int i = 0; i < 9; i++) {
                Ingredient ingredient = required[i];
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                Slot grid = craftingMenu.getSlot(1 + i);
                ItemStack existing = grid.getItem();
                if (!existing.isEmpty()) {
                    if (!ingredient.test(existing)) {
                        continue;
                    }
                    if (existing.getCount() >= existing.getMaxStackSize()) {
                        continue;
                    }
                    ItemStack extracted = extractOneMatchingIngredientCombined(handlers, player, ingredient, existing);
                    if (extracted.isEmpty()) {
                        continue;
                    }
                    existing.grow(1);
                    grid.setChanged();
                    passInsertedAny = true;
                    anyInserted = true;
                    continue;
                }

                ItemStack extracted = extractOneMatchingIngredientCombined(handlers, player, ingredient, ItemStack.EMPTY);
                if (extracted.isEmpty()) {
                    continue;
                }
                extracted.setCount(1);
                grid.set(extracted);
                grid.setChanged();
                passInsertedAny = true;
                anyInserted = true;
            }

            if (!passInsertedAny) {
                break;
            }
            if (!maxTransfer) {
                break;
            }
        }

        for (ItemStack stack : cleared) {
            if (stack.isEmpty()) {
                continue;
            }
            storeToLinkedWithFallbackPreferExisting(handlers, player, stack);
        }
        refreshCraftingResult(craftingMenu);
        craftingMenu.broadcastChanges();
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        if (anyInserted) {
            runQuestDetect(player, session, false);
        }
    }

    private static CraftExecutionResult craftSingleRecipeToLinked(ServerPlayer player, List<IItemHandler> handlers,
            CraftingRecipe recipe, boolean includePlayerFallback) {
        Ingredient[] required = mapCraftingIngredients(recipe);
        if (required.length != 9) {
            return CraftExecutionResult.failure(false);
        }

        ExtractedIngredient[] extracted = new ExtractedIngredient[9];
        List<ItemStack> inputStacks = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            Ingredient ingredient = required[i];
            if (ingredient == null || ingredient.isEmpty()) {
                inputStacks.add(ItemStack.EMPTY);
                continue;
            }

            ExtractedIngredient taken = takeIngredientForCraft(handlers, player, ingredient, includePlayerFallback);
            if (taken == null || taken.stack().isEmpty()) {
                rollbackCraftIngredients(handlers, player, extracted);
                return CraftExecutionResult.failure(false);
            }
            extracted[i] = taken;
            inputStacks.add(taken.stack().copyWithCount(1));
        }

        CraftingInput input = CraftingInput.of(3, 3, inputStacks);
        if (!recipe.matches(input, player.serverLevel())) {
            rollbackCraftIngredients(handlers, player, extracted);
            return CraftExecutionResult.failure(false);
        }

        ItemStack result = recipe.assemble(input, player.registryAccess());
        if (result.isEmpty()) {
            rollbackCraftIngredients(handlers, player, extracted);
            return CraftExecutionResult.failure(false);
        }

        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(result.copy());
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(input);
        for (ItemStack remain : remaining) {
            if (!remain.isEmpty()) {
                outputs.add(remain.copy());
            }
        }

        if (!canStoreStacksToLinkedOnly(handlers, outputs)) {
            rollbackCraftIngredients(handlers, player, extracted);
            return CraftExecutionResult.failure(true);
        }

        for (ItemStack stack : outputs) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, stack);
            if (!remain.isEmpty()) {
                moveToPlayerInventoryOnly(player, remain);
            }
        }

        ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
        return new CraftExecutionResult(
                true,
                false,
                resultId == null ? "" : resultId.toString(),
                Math.max(1, result.getCount()));
    }

    private static ExtractedIngredient takeIngredientForCraft(List<IItemHandler> handlers, ServerPlayer player,
            Ingredient ingredient, boolean includePlayerFallback) {
        ItemStack fromLinked = extractOneMatchingIngredient(handlers, ingredient, ItemStack.EMPTY);
        if (!fromLinked.isEmpty()) {
            return new ExtractedIngredient(fromLinked, false);
        }
        if (!includePlayerFallback) {
            return null;
        }
        ItemStack fromPlayer = extractOneMatchingIngredientFromPlayer(player, ingredient, ItemStack.EMPTY);
        if (!fromPlayer.isEmpty()) {
            return new ExtractedIngredient(fromPlayer, true);
        }
        return null;
    }

    private static void rollbackCraftIngredients(List<IItemHandler> handlers, ServerPlayer player, ExtractedIngredient[] extracted) {
        for (int i = extracted.length - 1; i >= 0; i--) {
            ExtractedIngredient ingredient = extracted[i];
            if (ingredient == null || ingredient.stack().isEmpty()) {
                continue;
            }
            if (ingredient.fromPlayer()) {
                moveToPlayerInventoryOnly(player, ingredient.stack());
                continue;
            }
            ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, ingredient.stack());
            if (!remain.isEmpty()) {
                moveToPlayerInventoryOnly(player, remain);
            }
        }
    }

    private static boolean canStoreStacksToLinkedOnly(List<IItemHandler> handlers, List<ItemStack> stacks) {
        List<List<ItemStack>> virtual = new ArrayList<>(handlers.size());
        for (IItemHandler handler : handlers) {
            List<ItemStack> slots = new ArrayList<>(handler.getSlots());
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                slots.add(handler.getStackInSlot(slot).copy());
            }
            virtual.add(slots);
        }

        for (ItemStack original : stacks) {
            if (original == null || original.isEmpty()) {
                continue;
            }
            ItemStack remain = original.copy();

            for (int h = 0; h < handlers.size() && !remain.isEmpty(); h++) {
                IItemHandler handler = handlers.get(h);
                List<ItemStack> slots = virtual.get(h);
                for (int slot = 0; slot < slots.size() && !remain.isEmpty(); slot++) {
                    ItemStack current = slots.get(slot);
                    if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, remain)) {
                        continue;
                    }
                    int slotLimit = Math.min(handler.getSlotLimit(slot), current.getMaxStackSize());
                    int free = Math.max(0, slotLimit - current.getCount());
                    if (free <= 0) {
                        continue;
                    }
                    int move = Math.min(free, remain.getCount());
                    current.grow(move);
                    remain.shrink(move);
                }
            }

            for (int h = 0; h < handlers.size() && !remain.isEmpty(); h++) {
                IItemHandler handler = handlers.get(h);
                List<ItemStack> slots = virtual.get(h);
                for (int slot = 0; slot < slots.size() && !remain.isEmpty(); slot++) {
                    ItemStack current = slots.get(slot);
                    if (!current.isEmpty()) {
                        continue;
                    }
                    int slotLimit = Math.min(handler.getSlotLimit(slot), remain.getMaxStackSize());
                    if (slotLimit <= 0) {
                        continue;
                    }
                    ItemStack probe = remain.copy();
                    probe.setCount(slotLimit);
                    ItemStack rejected = handler.insertItem(slot, probe, true);
                    int accepted = slotLimit - rejected.getCount();
                    if (accepted <= 0) {
                        continue;
                    }
                    int move = Math.min(accepted, remain.getCount());
                    ItemStack filled = remain.copy();
                    filled.setCount(move);
                    slots.set(slot, filled);
                    remain.shrink(move);
                }
            }

            if (!remain.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static void breakPlaced(ServerPlayer player, BlockPos pos, Direction face, boolean allowAdjacentFallback) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !canAccessWorldTarget(player, pos)) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (session.linkedPositions.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(level);
        BlockPos targetPos = pos.immutable();
        if (!tracker.isPlaced(targetPos)) {
            if (!allowAdjacentFallback) {
                return;
            }
            Direction resolvedFace = face == null ? Direction.UP : face;
            BlockPos adjacent = targetPos.relative(resolvedFace);
            if (!canAccessWorldTarget(player, adjacent) || !tracker.isPlaced(adjacent)) {
                return;
            }
            targetPos = adjacent;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            // Never route recovered items into the container being broken.
            if (linked.pos().equals(targetPos)) {
                continue;
            }
            handlers.add(linked.handler());
        }

        BlockState state = level.getBlockState(targetPos);
        if (state.isAir()) {
            tracker.clear(targetPos);
            return;
        }

        Set<UUID> dropIdsBeforeBreak = snapshotNearbyDropIds(level, targetPos);
        boolean removed = breakPlacedWithSimulatedSilkTool(player, level, targetPos);
        if (!removed || !level.getBlockState(targetPos).isAir()) {
            return;
        }

        tracker.clear(targetPos);
        OverflowOutcome overflow = OverflowOutcome.EMPTY;
        List<ItemEntity> droppedEntities = collectNewNearbyDrops(level, targetPos, dropIdsBeforeBreak);
        for (ItemEntity droppedEntity : droppedEntities) {
            ItemStack droppedStack = droppedEntity.getItem();
            if (droppedStack.isEmpty()) {
                continue;
            }
            ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, droppedStack);
            if (remain.isEmpty()) {
                droppedEntity.discard();
                continue;
            }

            overflow = overflow.merge(storeToLinkedWithFallback(handlers, player, remain));
            droppedEntity.discard();
        }
        if (overflow.hasOverflow()) {
            sendStorageOverflowHint(player, "Absorb", overflow);
        }

        // If a linked storage block itself is broken, unlink it immediately.
        if (session.linkedPositions.remove(targetPos)) {
            session.linkedNames.remove(targetPos);
            if (session.linkedPositions.isEmpty()) {
                session.linkedDimension = null;
            }
            saveSessionToPlayerNbt(player, session);
        }

        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        if (!droppedEntities.isEmpty()) {
            runQuestDetect(player, session, false);
        }
    }

    private static boolean breakPlacedWithSimulatedSilkTool(ServerPlayer player, ServerLevel level, BlockPos targetPos) {
        ItemStack simulatedTool = createSimulatedSilkNetheritePick(level);
        return withTemporaryOnGround(player, true, () -> withTemporaryMainHandItem(player, simulatedTool, () -> player.gameMode.destroyBlock(targetPos)));
    }

    private static ItemStack createSimulatedSilkNetheritePick(ServerLevel level) {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        return tool;
    }

    private static Set<UUID> snapshotNearbyDropIds(ServerLevel level, BlockPos pos) {
        Set<UUID> ids = new HashSet<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5D))) {
            if (entity == null || !entity.isAlive() || entity.getItem().isEmpty()) {
                continue;
            }
            ids.add(entity.getUUID());
        }
        return ids;
    }

    private static List<ItemEntity> collectNewNearbyDrops(ServerLevel level, BlockPos pos, Set<UUID> beforeIds) {
        List<ItemEntity> out = new ArrayList<>();
        Set<UUID> known = beforeIds == null ? Set.of() : beforeIds;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5D))) {
            if (entity == null || !entity.isAlive() || entity.getItem().isEmpty()) {
                continue;
            }
            if (known.contains(entity.getUUID())) {
                continue;
            }
            out.add(entity);
        }
        return out;
    }

    public static void pickupLinkedToCarried(ServerPlayer player, String itemId, int amount) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (session.linkedPositions.isEmpty()) {
            return;
        }
        if (itemId == null || itemId.isBlank() || amount <= 0) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(id);

        ItemStack carried = player.containerMenu.getCarried();
        int maxStack = item.getDefaultMaxStackSize();
        int wanted = Math.min(amount, maxStack);
        if (!carried.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(carried, new ItemStack(item))) {
                return;
            }
            wanted = Math.min(wanted, carried.getMaxStackSize() - carried.getCount());
            if (wanted <= 0) {
                return;
            }
        }

        ItemStack extracted = extractMatchingFromNetwork(handlers, player, item, wanted);
        if (extracted.isEmpty()) {
            return;
        }

        if (carried.isEmpty()) {
            player.containerMenu.setCarried(extracted);
        } else {
            carried.grow(extracted.getCount());
            player.containerMenu.setCarried(carried);
        }
        player.containerMenu.broadcastChanges();
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void mine(ServerPlayer player, BlockPos pos, Direction face, boolean start, byte toolSlot) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);

        int slot = clampHotbarSlot(toolSlot);

        if (start) {
            if (!canAccessWorldTarget(player, pos)) {
                stopActiveMining(player, session);
                return;
            }

            if (PlacedBlockTrackerData.get(player.serverLevel()).isPlaced(pos) && !session.linkedPositions.isEmpty()) {
                BlockState before = player.serverLevel().getBlockState(pos);
                breakPlaced(player, pos, face, false);
                BlockState after = player.serverLevel().getBlockState(pos);
                if (!before.equals(after)) {
                    stopActiveMining(player, session);
                    return;
                }
            }
            beginRemoteMining(player, session, pos, face, slot);
            return;
        }

        stopActiveMining(player, session);
    }

    private static void beginRemoteMining(ServerPlayer player, Session session, BlockPos pos, Direction face, int toolSlot) {
        if (session.miningPos != null && !session.miningPos.equals(pos)) {
            player.serverLevel().destroyBlockProgress(player.getId(), session.miningPos, -1);
            sendMineProgress(player, session.miningPos, -1);
        }
        session.miningPos = pos.immutable();
        session.miningFace = face == null ? Direction.DOWN : face;
        session.miningToolSlot = clampHotbarSlot(toolSlot);
        session.miningProgress = 0.0F;
        session.miningStage = -1;
    }

    private static void tickActiveMining(ServerPlayer player, Session session) {
        if (session.miningPos == null) {
            return;
        }
        if (!canAccessWorldTarget(player, session.miningPos)) {
            stopActiveMining(player, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = session.miningPos;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
            stopActiveMining(player, session);
            return;
        }

        float step = computeRemoteDestroyStep(player, state, pos, session.miningToolSlot);
        if (step <= 0.0F) {
            return;
        }

        session.miningProgress += step;
        int stage = Math.min(9, (int) (session.miningProgress * 10.0F));
        if (stage != session.miningStage) {
            level.destroyBlockProgress(player.getId(), pos, stage);
            sendMineProgress(player, pos, stage);
            session.miningStage = stage;
        }

        if (session.miningProgress < 1.0F) {
            return;
        }

        boolean broken = destroyMinedBlock(player, pos, session.miningToolSlot);
        level.destroyBlockProgress(player.getId(), pos, -1);
        sendMineProgress(player, pos, -1);
        resetMiningState(session);

        if (broken && session.autoStoreMinedDrops) {
            boolean absorbed = absorbNearbyDropsIntoLinked(player, pos, session);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            if (absorbed) {
                runQuestDetect(player, session, false);
            }
        }
    }

    private static void stopActiveMining(ServerPlayer player, Session session) {
        if (session.miningPos != null) {
            player.serverLevel().destroyBlockProgress(player.getId(), session.miningPos, -1);
            sendMineProgress(player, session.miningPos, -1);
        }
        resetMiningState(session);
    }

    private static void resetMiningState(Session session) {
        session.miningPos = null;
        session.miningFace = Direction.DOWN;
        session.miningProgress = 0.0F;
        session.miningStage = -1;
    }

    private static float computeRemoteDestroyStep(ServerPlayer player, BlockState state, BlockPos pos, int toolSlot) {
        return withTemporaryOnGround(player, true, () -> withTemporarySelectedSlot(
                player,
                toolSlot,
                () -> removeMiningSpeedPenalty(player, state.getDestroyProgress(player, player.serverLevel(), pos))));
    }

    private static boolean destroyMinedBlock(ServerPlayer player, BlockPos pos, int toolSlot) {
        return withTemporarySelectedSlot(player, toolSlot, () -> player.gameMode.destroyBlock(pos));
    }

    private static float removeMiningSpeedPenalty(ServerPlayer player, float destroyStep) {
        if (destroyStep <= 0.0F) {
            return destroyStep;
        }
        float adjusted = destroyStep;
        if (player.isEyeInFluid(FluidTags.WATER)) {
            // 1.21.1 dig speed applies SUBMERGED_MINING_SPEED while underwater.
            // Cancel only the penalty portion (< 1.0) so enchant/mod buffs are preserved.
            double submergedMiningSpeed = player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            if (submergedMiningSpeed > 0.0D && submergedMiningSpeed < 1.0D) {
                adjusted *= (float) (1.0D / submergedMiningSpeed);
            }
        }
        return adjusted;
    }

    private static <T> T withTemporarySelectedSlot(ServerPlayer player, int toolSlot, Supplier<T> action) {
        int slot = clampHotbarSlot(toolSlot);
        int prevSelected = player.getInventory().selected;

        player.getInventory().selected = slot;
        try {
            return action.get();
        } finally {
            player.getInventory().selected = prevSelected;
        }
    }

    private static <T> T withTemporaryOnGround(ServerPlayer player, boolean onGround, Supplier<T> action) {
        boolean previous = player.onGround();
        player.setOnGround(onGround);
        try {
            return action.get();
        } finally {
            player.setOnGround(previous);
        }
    }

    private static <T> T withTemporaryMainHandItem(ServerPlayer player, ItemStack stack, Supplier<T> action) {
        ItemStack previousMainHand = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        try {
            return action.get();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
    }

    private static void sendMineProgress(ServerPlayer player, BlockPos pos, int stage) {
        PacketDistributor.sendToPlayer(player, new S2CRtsMineProgressPayload(pos, (byte) stage));
    }

    private static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    private static boolean storeFluidFromLinkedItem(ServerPlayer player, Session session, List<IItemHandler> itemHandlers,
            List<LinkedFluidHandler> fluidHandlers, String itemId) {
        if (itemId == null || itemId.isBlank() || itemHandlers.isEmpty()) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return false;
        }

        Item item = BuiltInRegistries.ITEM.get(id);
        ItemStack extracted = extractOneFromNetwork(itemHandlers, player, item);
        if (extracted.isEmpty()) {
            return false;
        }

        ContainerDrainOutcome simulated = drainContainer(extracted, FLUID_TRANSFER_MB, false);
        if (simulated.isEmpty() || simulated.fluid().getAmount() < FLUID_TRANSFER_MB) {
            refundToLinked(itemHandlers, player, extracted);
            return false;
        }
        FluidStack targetFluid = simulated.fluid().copy();
        targetFluid.setAmount(FLUID_TRANSFER_MB);
        if (insertFluidIntoNetwork(session, fluidHandlers, targetFluid, false) < FLUID_TRANSFER_MB) {
            refundToLinked(itemHandlers, player, extracted);
            return false;
        }

        ContainerDrainOutcome executed = drainContainer(extracted, FLUID_TRANSFER_MB, true);
        if (executed.isEmpty() || executed.fluid().getAmount() < FLUID_TRANSFER_MB) {
            refundToLinked(itemHandlers, player, extracted);
            return false;
        }
        FluidStack insertFluid = executed.fluid().copy();
        insertFluid.setAmount(FLUID_TRANSFER_MB);
        int inserted = insertFluidIntoNetwork(session, fluidHandlers, insertFluid, true);
        if (inserted < FLUID_TRANSFER_MB) {
            refundToLinked(itemHandlers, player, extracted);
            return false;
        }

        if (!executed.remainder().isEmpty()) {
            refundToLinked(itemHandlers, player, executed.remainder());
        }
        return true;
    }

    private static boolean storeFluidFromToolSlot(ServerPlayer player, Session session, List<LinkedFluidHandler> fluidHandlers,
            int toolSlot) {
        int slot = clampHotbarSlot(toolSlot);
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot.isEmpty()) {
            return false;
        }

        ItemStack single = inSlot.copyWithCount(1);
        ContainerDrainOutcome simulated = drainContainer(single, FLUID_TRANSFER_MB, false);
        if (simulated.isEmpty() || simulated.fluid().getAmount() < FLUID_TRANSFER_MB) {
            return false;
        }
        FluidStack targetFluid = simulated.fluid().copy();
        targetFluid.setAmount(FLUID_TRANSFER_MB);
        if (insertFluidIntoNetwork(session, fluidHandlers, targetFluid, false) < FLUID_TRANSFER_MB) {
            return false;
        }

        ContainerDrainOutcome executed = drainContainer(single, FLUID_TRANSFER_MB, true);
        if (executed.isEmpty() || executed.fluid().getAmount() < FLUID_TRANSFER_MB) {
            return false;
        }
        FluidStack insertFluid = executed.fluid().copy();
        insertFluid.setAmount(FLUID_TRANSFER_MB);
        int inserted = insertFluidIntoNetwork(session, fluidHandlers, insertFluid, true);
        if (inserted < FLUID_TRANSFER_MB) {
            return false;
        }

        ItemStack remainingInSlot = inSlot.copy();
        remainingInSlot.shrink(1);
        if (remainingInSlot.isEmpty()) {
            player.getInventory().setItem(slot, executed.remainder());
        } else {
            player.getInventory().setItem(slot, remainingInSlot);
            pushToPlayerInventoryOrDrop(player, executed.remainder());
        }
        player.containerMenu.broadcastChanges();
        return true;
    }

    private static void pushToPlayerInventoryOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack remainder = stack.copy();
        player.getInventory().add(remainder);
        if (!remainder.isEmpty()) {
            player.drop(remainder, false);
        }
    }

    private static ContainerDrainOutcome drainContainer(ItemStack container, int amount, boolean execute) {
        if (container.isEmpty() || amount <= 0) {
            return ContainerDrainOutcome.EMPTY;
        }
        ItemStack single = container.copyWithCount(1);
        Optional<IFluidHandlerItem> optHandler = FluidUtil.getFluidHandler(single);
        if (optHandler.isEmpty()) {
            return ContainerDrainOutcome.EMPTY;
        }

        IFluidHandlerItem handler = optHandler.get();
        FluidStack simulated = handler.drain(amount, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty()) {
            return ContainerDrainOutcome.EMPTY;
        }
        if (!execute) {
            return new ContainerDrainOutcome(simulated.copy(), handler.getContainer().copy());
        }

        FluidStack drained = handler.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return ContainerDrainOutcome.EMPTY;
        }
        return new ContainerDrainOutcome(drained.copy(), handler.getContainer().copy());
    }

    private static int insertFluidIntoNetwork(Session session, List<LinkedFluidHandler> fluidHandlers, FluidStack fluidStack,
            boolean execute) {
        if (fluidStack.isEmpty() || fluidStack.getAmount() <= 0) {
            return 0;
        }
        int remaining = fluidStack.getAmount();

        for (LinkedFluidHandler linked : fluidHandlers) {
            if (remaining <= 0) {
                break;
            }
            FluidStack candidate = fluidStack.copy();
            candidate.setAmount(remaining);
            int filled = linked.handler().fill(candidate,
                    execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE);
            if (filled > 0) {
                remaining -= filled;
            }
        }

        if (remaining <= 0) {
            return fluidStack.getAmount();
        }

        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluidStack.getFluid());
        if (id == null) {
            return fluidStack.getAmount() - remaining;
        }
        String fluidId = id.toString();
        long stored = session.internalFluidMb.getOrDefault(fluidId, 0L);
        long space = Math.max(0L, INTERNAL_FLUID_CAPACITY_MB - stored);
        int toInternal = (int) Math.min((long) remaining, space);
        if (toInternal > 0) {
            if (execute) {
                session.internalFluidMb.put(fluidId, stored + toInternal);
            }
            remaining -= toInternal;
        }
        return fluidStack.getAmount() - remaining;
    }

    private static int extractFluidFromNetwork(Session session, List<LinkedFluidHandler> fluidHandlers, Fluid fluid, int amount,
            boolean execute) {
        if (fluid == null || amount <= 0) {
            return 0;
        }

        int remaining = amount;
        for (LinkedFluidHandler linked : fluidHandlers) {
            if (remaining <= 0) {
                break;
            }
            FluidStack request = new FluidStack(fluid, remaining);
            FluidStack drained = linked.handler().drain(
                    request,
                    execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE);
            if (!drained.isEmpty()) {
                remaining -= drained.getAmount();
            }
        }

        if (remaining > 0) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            if (id != null) {
                String fluidId = id.toString();
                long internal = session.internalFluidMb.getOrDefault(fluidId, 0L);
                int drainedInternal = (int) Math.min((long) remaining, Math.max(0L, internal));
                if (drainedInternal > 0) {
                    if (execute) {
                        long left = internal - drainedInternal;
                        if (left > 0L) {
                            session.internalFluidMb.put(fluidId, left);
                        } else {
                            session.internalFluidMb.remove(fluidId);
                        }
                    }
                    remaining -= drainedInternal;
                }
            }
        }

        return amount - remaining;
    }

    private static int fillFluidHandlerAtTarget(ServerLevel level, BlockPos clickedPos, Direction face, FluidStack fluidStack) {
        if (fluidStack.isEmpty() || !level.hasChunkAt(clickedPos)) {
            return 0;
        }
        List<IFluidHandler> candidates = new ArrayList<>();
        addFluidHandlerCandidate(level, clickedPos, face, candidates);
        addFluidHandlerCandidate(level, clickedPos, null, candidates);
        for (Direction direction : Direction.values()) {
            addFluidHandlerCandidate(level, clickedPos, direction, candidates);
        }

        BlockPos adjacent = clickedPos.relative(face);
        if (level.hasChunkAt(adjacent)) {
            addFluidHandlerCandidate(level, adjacent, face.getOpposite(), candidates);
            addFluidHandlerCandidate(level, adjacent, null, candidates);
            for (Direction direction : Direction.values()) {
                addFluidHandlerCandidate(level, adjacent, direction, candidates);
            }
        }

        for (IFluidHandler handler : candidates) {
            FluidStack candidate = fluidStack.copy();
            int simulated = handler.fill(candidate, IFluidHandler.FluidAction.SIMULATE);
            if (simulated <= 0) {
                continue;
            }
            candidate.setAmount(simulated);
            return handler.fill(candidate, IFluidHandler.FluidAction.EXECUTE);
        }
        return 0;
    }

    private static void addFluidHandlerCandidate(ServerLevel level, BlockPos pos, Direction side, List<IFluidHandler> out) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        if (handler != null && !out.contains(handler)) {
            out.add(handler);
        }
    }

    private static BlockPos resolveFluidPlacementPos(ServerLevel level, ServerPlayer player, BlockHitResult hit,
            FluidStack fluidStack) {
        BlockPos clicked = hit.getBlockPos();
        if (canPlaceFluidAt(level, player, clicked, fluidStack)) {
            return clicked;
        }

        // In RTS top-down view, side-face hits are common; prefer top cell when valid.
        BlockPos above = clicked.above();
        if (hit.getDirection().getAxis() != Direction.Axis.Y
                && level.hasChunkAt(above)
                && canPlaceFluidAt(level, player, above, fluidStack)) {
            return above;
        }

        BlockPos adjacent = clicked.relative(hit.getDirection());
        if (!level.hasChunkAt(adjacent)) {
            return level.hasChunkAt(above) && canPlaceFluidAt(level, player, above, fluidStack) ? above : null;
        }
        if (canPlaceFluidAt(level, player, adjacent, fluidStack)) {
            return adjacent;
        }
        if (level.hasChunkAt(above) && canPlaceFluidAt(level, player, above, fluidStack)) {
            return above;
        }
        return null;
    }

    private static boolean canPlaceFluidAt(ServerLevel level, ServerPlayer player, BlockPos pos, FluidStack fluidStack) {
        if (fluidStack.isEmpty() || !level.hasChunkAt(pos)) {
            return false;
        }
        Fluid fluid = fluidStack.getFluid();
        if (!fluid.getFluidType().canBePlacedInLevel(level, pos, fluidStack)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        BlockPlaceContext context = new BlockPlaceContext(
                level,
                player,
                InteractionHand.MAIN_HAND,
                ItemStack.EMPTY,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        boolean canContain = state.getBlock() instanceof LiquidBlockContainer liquidContainer
                && liquidContainer.canPlaceLiquid(player, level, pos, state, fluid);
        boolean isDestNonSolid = !state.isSolid();
        boolean isDestReplaceable = state.canBeReplaced(context);
        return level.isEmptyBlock(pos) || isDestNonSolid || isDestReplaceable || canContain;
    }

    private static boolean placeFluidBlock(ServerLevel level, ServerPlayer player, BlockPos pos, FluidStack fluidStack) {
        if (!canPlaceFluidAt(level, player, pos, fluidStack)) {
            return false;
        }

        Fluid fluid = fluidStack.getFluid();
        BlockState state = level.getBlockState(pos);
        if (fluid.getFluidType().isVaporizedOnPlacement(level, pos, fluidStack)) {
            fluid.getFluidType().onVaporize(player, level, pos, fluidStack);
            return true;
        }

        if (state.getBlock() instanceof LiquidBlockContainer liquidContainer
                && liquidContainer.canPlaceLiquid(player, level, pos, state, fluid)) {
            return liquidContainer.placeLiquid(level, pos, state, fluid.defaultFluidState());
        }

        BlockState placeState = fluid.getFluidType().getBlockForFluidState(
                level,
                pos,
                fluid.getFluidType().getStateForPlacement(level, pos, fluidStack));
        if (placeState.isAir()) {
            return false;
        }

        BlockPlaceContext context = new BlockPlaceContext(
                level,
                player,
                InteractionHand.MAIN_HAND,
                ItemStack.EMPTY,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        boolean isDestNonSolid = !state.isSolid();
        boolean isDestReplaceable = state.canBeReplaced(context);
        if ((isDestNonSolid || isDestReplaceable) && !state.liquid()) {
            level.destroyBlock(pos, true);
        }
        return level.setBlock(pos, placeState, 11);
    }

    private static int getPlayerMainInventoryStart(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return Math.min(PLAYER_HOTBAR_SLOT_COUNT, player.getInventory().getContainerSize());
    }

    private static int getPlayerMainInventoryEndExclusive(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return Math.min(PLAYER_MAIN_INVENTORY_END_EXCLUSIVE, player.getInventory().getContainerSize());
    }

    private static void accumulatePlayerMainInventoryCounts(ServerPlayer player, Map<String, Long> counts,
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
            counts.merge(id.toString(), (long) stack.getCount(), Long::sum);
            namespaceTotals.merge(id.getNamespace(), (long) stack.getCount(), Long::sum);
        }
    }

    private static ItemStack extractOne(IItemHandler handler, Item targetItem) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getItem() != targetItem) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, 1, false);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractMatching(IItemHandler handler, Item targetItem, int limit) {
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = ItemStack.EMPTY;
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getItem() != targetItem) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, remaining, false);
            if (extracted.isEmpty()) {
                continue;
            }
            if (out.isEmpty()) {
                out = extracted;
            } else if (ItemStack.isSameItemSameComponents(out, extracted)) {
                out.grow(extracted.getCount());
            }
            remaining -= extracted.getCount();
        }
        return out;
    }

    private static ItemStack extractOneFromLinked(List<IItemHandler> handlers, Item targetItem) {
        for (IItemHandler handler : handlers) {
            ItemStack extracted = extractOne(handler, targetItem);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractOneFromPlayerMainInventory(ServerPlayer player, Item targetItem) {
        if (player == null || targetItem == null) {
            return ItemStack.EMPTY;
        }
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current.isEmpty() || current.getItem() != targetItem) {
                continue;
            }
            ItemStack extracted = current.split(1);
            if (current.isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(slot, current);
            }
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractOneFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem) {
        ItemStack extracted = extractOneFromLinked(handlers, targetItem);
        if (!extracted.isEmpty()) {
            return extracted;
        }
        return extractOneFromPlayerMainInventory(player, targetItem);
    }

    private static ItemStack extractOneMatchingIngredient(List<IItemHandler> handlers, Ingredient ingredient) {
        return extractOneMatchingIngredient(handlers, ingredient, ItemStack.EMPTY);
    }

    private static ItemStack extractOneMatchingIngredient(List<IItemHandler> handlers, Ingredient ingredient, ItemStack preferred) {
        if (ingredient == null || ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!preferred.isEmpty() && ingredient.test(preferred)) {
            ItemStack preferredMatch = extractOneMatchingIngredientFromHandlers(handlers, ingredient, preferred);
            if (!preferredMatch.isEmpty()) {
                return preferredMatch;
            }
        }
        return extractOneMatchingIngredientFromHandlers(handlers, ingredient, ItemStack.EMPTY);
    }

    private static ItemStack extractOneMatchingIngredientFromHandlers(List<IItemHandler> handlers, Ingredient ingredient,
            ItemStack preferred) {
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || !ingredient.test(stack)) {
                    continue;
                }
                if (!preferred.isEmpty() && !ItemStack.isSameItemSameComponents(stack, preferred)) {
                    continue;
                }
                ItemStack extracted = handler.extractItem(slot, 1, false);
                if (extracted.isEmpty()) {
                    continue;
                }
                if (ingredient.test(extracted)) {
                    return extracted;
                }
                ItemStack remainder = insertToHandlerPreferExisting(handler, extracted);
                if (!remainder.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractOneMatchingIngredientCombined(List<IItemHandler> handlers, ServerPlayer player,
            Ingredient ingredient, ItemStack preferred) {
        ItemStack fromLinked = extractOneMatchingIngredient(handlers, ingredient, preferred);
        if (!fromLinked.isEmpty()) {
            return fromLinked;
        }
        return extractOneMatchingIngredientFromPlayer(player, ingredient, preferred);
    }

    private static ItemStack extractOneMatchingIngredientFromPlayer(ServerPlayer player, Ingredient ingredient, ItemStack preferred) {
        if (player == null || ingredient == null || ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!preferred.isEmpty() && ingredient.test(preferred)) {
            ItemStack preferredMatch = extractOneMatchingIngredientFromPlayer(player, ingredient, preferred, true);
            if (!preferredMatch.isEmpty()) {
                return preferredMatch;
            }
        }
        return extractOneMatchingIngredientFromPlayer(player, ingredient, ItemStack.EMPTY, false);
    }

    private static ItemStack extractOneMatchingIngredientFromPlayer(ServerPlayer player, Ingredient ingredient, ItemStack preferred,
            boolean requirePreferredMatch) {
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int i = start; i < end; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (current.isEmpty() || !ingredient.test(current)) {
                continue;
            }
            if (requirePreferredMatch && !ItemStack.isSameItemSameComponents(current, preferred)) {
                continue;
            }
            ItemStack extracted = current.split(1);
            if (current.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(i, current);
            }
            if (!extracted.isEmpty() && ingredient.test(extracted)) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void rollbackGridInserts(CraftingMenu craftingMenu, List<IItemHandler> handlers, ServerPlayer player,
            List<GridInsert> insertedThisPass) {
        for (int i = insertedThisPass.size() - 1; i >= 0; i--) {
            GridInsert insert = insertedThisPass.get(i);
            Slot grid = craftingMenu.getSlot(1 + insert.slotIndex());
            ItemStack current = grid.getItem();
            if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, insert.stack()) && current.getCount() > 0) {
                current.shrink(1);
                if (current.isEmpty()) {
                    grid.set(ItemStack.EMPTY);
                } else {
                    grid.set(current);
                }
                grid.setChanged();
            }
            storeToLinkedWithFallbackPreferExisting(handlers, player, insert.stack());
        }
    }

    private static Ingredient[] mapCraftingIngredients(CraftingRecipe recipe) {
        Ingredient[] mapped = new Ingredient[9];
        for (int i = 0; i < mapped.length; i++) {
            mapped[i] = Ingredient.EMPTY;
        }
        List<Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof ShapedRecipe shaped) {
            int width = Math.max(1, Math.min(3, shaped.getWidth()));
            int height = Math.max(1, Math.min(3, shaped.getHeight()));
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int src = y * width + x;
                    if (src < 0 || src >= ingredients.size()) {
                        continue;
                    }
                    mapped[y * 3 + x] = ingredients.get(src);
                }
            }
        } else {
            int count = Math.min(9, ingredients.size());
            for (int i = 0; i < count; i++) {
                mapped[i] = ingredients.get(i);
            }
        }
        return mapped;
    }

    private static ItemStack extractMatchingFromLinked(List<IItemHandler> handlers, Item targetItem, int limit) {
        int remaining = Math.max(0, limit);
        ItemStack out = ItemStack.EMPTY;
        for (IItemHandler handler : handlers) {
            if (remaining <= 0) {
                break;
            }
            ItemStack part = extractMatching(handler, targetItem, remaining);
            if (part.isEmpty()) {
                continue;
            }
            if (out.isEmpty()) {
                out = part;
            } else if (ItemStack.isSameItemSameComponents(out, part)) {
                out.grow(part.getCount());
            }
            remaining -= part.getCount();
        }
        return out;
    }

    private static ItemStack extractMatchingFromPlayerMainInventory(ServerPlayer player, Item targetItem, int limit) {
        if (player == null || targetItem == null) {
            return ItemStack.EMPTY;
        }
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = ItemStack.EMPTY;
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end && remaining > 0; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current.isEmpty() || current.getItem() != targetItem) {
                continue;
            }
            int take = Math.min(remaining, current.getCount());
            ItemStack extracted = current.split(take);
            if (current.isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(slot, current);
            }
            if (extracted.isEmpty()) {
                continue;
            }
            if (out.isEmpty()) {
                out = extracted;
            } else if (ItemStack.isSameItemSameComponents(out, extracted)) {
                out.grow(extracted.getCount());
            }
            remaining -= extracted.getCount();
        }
        return out;
    }

    private static ItemStack extractMatchingFromPlayerHotbarForQuickDrop(ServerPlayer player, Item targetItem, int limit) {
        if (player == null || targetItem == null) {
            return ItemStack.EMPTY;
        }
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = ItemStack.EMPTY;
        int selected = clampHotbarSlot(player.getInventory().selected);
        ItemStack selectedPart = extractMatchingFromPlayerSlot(player, targetItem, selected, remaining);
        out = mergeExtractedStacks(out, selectedPart);
        remaining -= selectedPart.getCount();

        for (int slot = 0; slot < PLAYER_HOTBAR_SLOT_COUNT && remaining > 0; slot++) {
            if (slot == selected) {
                continue;
            }
            ItemStack part = extractMatchingFromPlayerSlot(player, targetItem, slot, remaining);
            out = mergeExtractedStacks(out, part);
            remaining -= part.getCount();
        }
        return out;
    }

    private static ItemStack extractMatchingFromPlayerSlot(ServerPlayer player, Item targetItem, int slot, int limit) {
        if (player == null || targetItem == null || slot < 0 || limit <= 0) {
            return ItemStack.EMPTY;
        }
        if (slot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }

        ItemStack current = player.getInventory().getItem(slot);
        if (current.isEmpty() || current.getItem() != targetItem) {
            return ItemStack.EMPTY;
        }
        int take = Math.min(limit, current.getCount());
        ItemStack extracted = current.split(take);
        if (current.isEmpty()) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        } else {
            player.getInventory().setItem(slot, current);
        }
        return extracted.isEmpty() ? ItemStack.EMPTY : extracted;
    }

    private static ItemStack mergeExtractedStacks(ItemStack into, ItemStack addition) {
        if (addition == null || addition.isEmpty()) {
            return into;
        }
        if (into == null || into.isEmpty()) {
            return addition;
        }
        if (ItemStack.isSameItemSameComponents(into, addition)) {
            into.grow(addition.getCount());
        }
        return into;
    }

    private static ItemStack extractMatchingFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem,
            int limit) {
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = extractMatchingFromLinked(handlers, targetItem, remaining);
        remaining -= out.getCount();
        if (remaining <= 0) {
            return out;
        }

        ItemStack fromPlayer = extractMatchingFromPlayerMainInventory(player, targetItem, remaining);
        if (fromPlayer.isEmpty()) {
            return out;
        }
        if (out.isEmpty()) {
            return fromPlayer;
        }
        if (ItemStack.isSameItemSameComponents(out, fromPlayer)) {
            out.grow(fromPlayer.getCount());
        }
        return out;
    }

    private static ItemStack extractMatchingFromQuickDropSources(List<IItemHandler> handlers, ServerPlayer player, Item targetItem,
            int limit) {
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = extractMatchingFromLinked(handlers, targetItem, remaining);
        remaining -= out.getCount();
        if (remaining <= 0) {
            return out;
        }

        ItemStack fromHotbar = extractMatchingFromPlayerHotbarForQuickDrop(player, targetItem, remaining);
        out = mergeExtractedStacks(out, fromHotbar);
        remaining -= fromHotbar.getCount();
        if (remaining <= 0) {
            return out;
        }

        ItemStack fromMainInventory = extractMatchingFromPlayerMainInventory(player, targetItem, remaining);
        out = mergeExtractedStacks(out, fromMainInventory);
        return out;
    }

    private static void refundToLinked(List<IItemHandler> handlers, ServerPlayer player, ItemStack stack) {
        storeToLinkedWithFallback(handlers, player, stack);
    }

    private static ItemStack insertToHandler(IItemHandler handler, ItemStack stack) {
        ItemStack remain = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remain.isEmpty(); slot++) {
            remain = handler.insertItem(slot, remain, false);
        }
        return remain;
    }

    private static ItemStack storeToLinkedOnly(List<IItemHandler> handlers, ItemStack stack) {
        ItemStack remain = stack.copy();
        for (IItemHandler handler : handlers) {
            if (remain.isEmpty()) {
                break;
            }
            remain = insertToHandler(handler, remain);
        }
        return remain;
    }

    private static OverflowOutcome storeToLinkedWithFallback(List<IItemHandler> handlers, ServerPlayer player, ItemStack stack) {
        ItemStack remain = stack.copy();
        for (IItemHandler handler : handlers) {
            if (remain.isEmpty()) {
                break;
            }
            remain = insertToHandler(handler, remain);
        }

        int movedToInventory = 0;
        if (!remain.isEmpty()) {
            ItemStack invStack = remain.copy();
            int before = invStack.getCount();
            player.getInventory().add(invStack);
            movedToInventory = before - invStack.getCount();
            remain = invStack;
        }

        int dropped = 0;
        if (!remain.isEmpty()) {
            dropped = remain.getCount();
            player.drop(remain, false);
        }

        return new OverflowOutcome(movedToInventory, dropped);
    }

    private static OverflowOutcome storeToLinkedWithFallbackPreferExisting(List<IItemHandler> handlers, ServerPlayer player,
            ItemStack stack) {
        ItemStack remain = stack.copy();
        for (IItemHandler handler : handlers) {
            if (remain.isEmpty()) {
                break;
            }
            remain = insertToHandlerPreferExisting(handler, remain);
        }

        int movedToInventory = 0;
        if (!remain.isEmpty()) {
            ItemStack invStack = remain.copy();
            int before = invStack.getCount();
            player.getInventory().add(invStack);
            movedToInventory = before - invStack.getCount();
            remain = invStack;
        }

        int dropped = 0;
        if (!remain.isEmpty()) {
            dropped = remain.getCount();
            player.drop(remain, false);
        }

        return new OverflowOutcome(movedToInventory, dropped);
    }

    private static ItemStack moveToPlayerInventoryOnly(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remain = stack.copy();
        player.getInventory().add(remain);
        return remain;
    }

    private static int[] snapshotPlayerMatchingCounts(ServerPlayer player, ItemStack prototype) {
        int size = player.getInventory().getContainerSize();
        int[] counts = new int[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, prototype)) {
                counts[i] = stack.getCount();
            }
        }
        return counts;
    }

    private static ItemStack[] snapshotCraftGridBlueprint(CraftingMenu menu) {
        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            Slot grid = menu.getSlot(1 + i);
            ItemStack stack = grid.getItem();
            blueprint[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
        return blueprint;
    }

    private static void refillCraftGridFromBlueprint(CraftingMenu menu, List<IItemHandler> handlers, ServerPlayer player,
            ItemStack[] blueprint, boolean fillAll, boolean includePlayerFallback) {
        if (blueprint == null || blueprint.length != 9) {
            return;
        }

        int maxPasses = fillAll ? 64 : 1;
        boolean changed = false;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean inserted = false;
            for (int i = 0; i < 9; i++) {
                ItemStack blueprintStack = blueprint[i];
                if (blueprintStack == null || blueprintStack.isEmpty()) {
                    continue;
                }
                Slot grid = menu.getSlot(1 + i);
                ItemStack current = grid.getItem();
                if (!current.isEmpty()) {
                    if (!ItemStack.isSameItemSameComponents(current, blueprintStack)) {
                        continue;
                    }
                    if (current.getCount() >= current.getMaxStackSize()) {
                        continue;
                    }
                    ItemStack extracted = includePlayerFallback
                            ? extractOneMatchingPrototypeCombined(handlers, player, blueprintStack)
                            : extractOneMatchingPrototypeFromLinked(handlers, blueprintStack);
                    if (extracted.isEmpty()) {
                        continue;
                    }
                    current.grow(1);
                    grid.setChanged();
                    inserted = true;
                    changed = true;
                    continue;
                }

                ItemStack extracted = includePlayerFallback
                        ? extractOneMatchingPrototypeCombined(handlers, player, blueprintStack)
                        : extractOneMatchingPrototypeFromLinked(handlers, blueprintStack);
                if (extracted.isEmpty()) {
                    continue;
                }
                extracted.setCount(1);
                grid.set(extracted);
                grid.setChanged();
                inserted = true;
                changed = true;
            }
            if (!inserted) {
                break;
            }
            if (!fillAll) {
                break;
            }
        }
        if (changed) {
            refreshCraftingResult(menu);
        }
    }

    private static void refreshCraftingResult(CraftingMenu menu) {
        if (menu == null) {
            return;
        }
        CraftingContainer craftSlots = resolveCraftingContainer(menu);
        if (craftSlots != null) {
            menu.slotsChanged(craftSlots);
        }
    }

    private static CraftingContainer resolveCraftingContainer(CraftingMenu menu) {
        Class<?> type = menu.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!CraftingContainer.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object current = field.get(menu);
                    if (current instanceof CraftingContainer craftSlots) {
                        return craftSlots;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Fall back to the menu's default sync path if reflective access is blocked.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static ItemStack extractOneMatchingPrototypeCombined(List<IItemHandler> handlers, ServerPlayer player, ItemStack prototype) {
        ItemStack fromLinked = extractOneMatchingPrototypeFromLinked(handlers, prototype);
        if (!fromLinked.isEmpty()) {
            return fromLinked;
        }
        return extractOneMatchingPrototypeFromPlayer(player, prototype);
    }

    private static ItemStack extractOneMatchingPrototypeFromLinked(List<IItemHandler> handlers, ItemStack prototype) {
        if (prototype == null || prototype.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, prototype)) {
                    continue;
                }
                ItemStack extracted = handler.extractItem(slot, 1, false);
                if (!extracted.isEmpty() && ItemStack.isSameItemSameComponents(extracted, prototype)) {
                    return extracted;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractOneMatchingPrototypeFromPlayer(ServerPlayer player, ItemStack prototype) {
        if (player == null || prototype == null || prototype.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int i = start; i < end; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, prototype)) {
                continue;
            }
            ItemStack extracted = current.split(1);
            if (current.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(i, current);
            }
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack drainPlayerInventoryDelta(ServerPlayer player, ItemStack prototype, int[] before) {
        ItemStack out = ItemStack.EMPTY;
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (!ItemStack.isSameItemSameComponents(current, prototype)) {
                continue;
            }
            int previous = (before != null && i < before.length) ? before[i] : 0;
            int gained = Math.max(0, current.getCount() - previous);
            if (gained <= 0) {
                continue;
            }
            int take = Math.min(gained, current.getCount());
            ItemStack part = current.split(take);
            if (current.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(i, current);
            }
            if (out.isEmpty()) {
                out = part;
            } else if (ItemStack.isSameItemSameComponents(out, part)) {
                out.grow(part.getCount());
            } else {
                // Should not happen for one prototype, but keep behavior safe.
                player.getInventory().add(part);
            }
        }
        return out;
    }

    private static ItemStack insertToHandlerPreferExisting(IItemHandler handler, ItemStack stack) {
        ItemStack remain = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remain.isEmpty(); slot++) {
            ItemStack slotStack = handler.getStackInSlot(slot);
            if (slotStack.isEmpty() || !ItemStack.isSameItemSameComponents(slotStack, remain)) {
                continue;
            }
            remain = handler.insertItem(slot, remain, false);
        }
        for (int slot = 0; slot < handler.getSlots() && !remain.isEmpty(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                continue;
            }
            remain = handler.insertItem(slot, remain, false);
        }
        return remain;
    }

    private static ItemStack storeToLinkedOnlyPreferExisting(List<IItemHandler> handlers, ItemStack stack) {
        ItemStack remain = stack.copy();
        for (IItemHandler handler : handlers) {
            if (remain.isEmpty()) {
                break;
            }
            remain = insertToHandlerPreferExisting(handler, remain);
        }
        return remain;
    }

    private static ItemStack addToFunnelBuffer(Session session, ItemStack stack) {
        ItemStack remain = stack.copy();
        if (remain.isEmpty()) {
            return ItemStack.EMPTY;
        }

        for (ItemStack buffered : session.funnelBuffer) {
            if (remain.isEmpty()) {
                break;
            }
            if (buffered.isEmpty() || !ItemStack.isSameItemSameComponents(buffered, remain)) {
                continue;
            }
            int free = Math.max(0, buffered.getMaxStackSize() - buffered.getCount());
            if (free <= 0) {
                continue;
            }
            int move = Math.min(free, remain.getCount());
            buffered.grow(move);
            remain.shrink(move);
        }

        while (!remain.isEmpty() && session.funnelBuffer.size() < FUNNEL_BUFFER_MAX_STACKS) {
            int move = Math.min(remain.getCount(), remain.getMaxStackSize());
            ItemStack chunk = remain.copy();
            chunk.setCount(move);
            session.funnelBuffer.add(chunk);
            remain.shrink(move);
        }
        return remain;
    }

    private static boolean flushFunnelBufferToDestinations(List<IItemHandler> handlers, ServerPlayer player, Session session) {
        if (session.funnelBuffer.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < session.funnelBuffer.size(); i++) {
            ItemStack buffered = session.funnelBuffer.get(i);
            if (buffered.isEmpty()) {
                session.funnelBuffer.remove(i);
                i--;
                changed = true;
                continue;
            }
            ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, buffered);
            if (!remain.isEmpty()) {
                remain = moveToPlayerInventoryOnly(player, remain);
            }
            if (remain.isEmpty()) {
                session.funnelBuffer.remove(i);
                i--;
                changed = true;
            } else if (remain.getCount() != buffered.getCount()) {
                session.funnelBuffer.set(i, remain);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean absorbDropsForFunnel(ServerPlayer player, BlockPos target, List<IItemHandler> handlers, Session session) {
        AABB box = new AABB(target).inflate(FUNNEL_RADIUS);
        List<ItemEntity> drops = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                box,
                entity -> entity != null && entity.isAlive() && !entity.getItem().isEmpty());

        int processedEntities = 0;
        int processedItems = 0;
        boolean changed = false;
        for (ItemEntity drop : drops) {
            if (processedEntities >= FUNNEL_MAX_ENTITIES_PER_TICK || processedItems >= FUNNEL_MAX_ITEMS_PER_TICK) {
                break;
            }
            processedEntities++;

            ItemStack worldStack = drop.getItem();
            if (worldStack.isEmpty()) {
                continue;
            }
            int remainingBudget = FUNNEL_MAX_ITEMS_PER_TICK - processedItems;
            int iterations = Math.min(worldStack.getCount(), remainingBudget);
            for (int i = 0; i < iterations; i++) {
                ItemStack one = worldStack.copy();
                one.setCount(1);
                ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, one);
                if (!remain.isEmpty()) {
                    remain = moveToPlayerInventoryOnly(player, remain);
                }
                if (!remain.isEmpty()) {
                    remain = addToFunnelBuffer(session, remain);
                }
                if (!remain.isEmpty()) {
                    break;
                }
                worldStack.shrink(1);
                processedItems++;
                changed = true;
                if (worldStack.isEmpty()) {
                    break;
                }
            }
            if (worldStack.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(worldStack);
            }
        }
        return changed;
    }

    private static void disableFunnelAndFlushBuffer(ServerPlayer player, Session session) {
        session.funnelEnabled = false;
        session.funnelTarget = null;
        session.funnelTickCooldown = 0;
        if (session.funnelBuffer.isEmpty()) {
            return;
        }
        sanitizeSessionDimension(player, session);
        List<LinkedHandler> linked = resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(linked.size());
        for (LinkedHandler itemHandler : linked) {
            handlers.add(itemHandler.handler());
        }

        for (ItemStack buffered : session.funnelBuffer) {
            if (buffered.isEmpty()) {
                continue;
            }
            ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, buffered);
            if (!remain.isEmpty()) {
                storeToLinkedWithFallback(handlers, player, remain);
            }
        }
        session.funnelBuffer.clear();
    }

    private static void tickFunnel(ServerPlayer player, Session session) {
        if (!session.funnelEnabled || session.mode != BuilderMode.FUNNEL) {
            return;
        }
        if (session.funnelTickCooldown > 0) {
            session.funnelTickCooldown--;
            return;
        }
        session.funnelTickCooldown = FUNNEL_TICK_INTERVAL - 1;

        sanitizeSessionDimension(player, session);
        if (session.funnelTarget == null) {
            return;
        }
        if (!canAccessWorldTarget(player, session.funnelTarget)) {
            return;
        }
        if (!RtsCameraManager.isWithinActionRadius(player, session.funnelTarget)) {
            return;
        }

        List<LinkedHandler> linked = resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(linked.size());
        for (LinkedHandler linkedHandler : linked) {
            handlers.add(linkedHandler.handler());
        }

        boolean changed = flushFunnelBufferToDestinations(handlers, player, session);
        changed |= absorbDropsForFunnel(player, session.funnelTarget, handlers, session);
        if (changed) {
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            runQuestDetect(player, session, false);
        }
    }

    private static Map<String, Long> summarizeFunnelBuffer(Session session) {
        Map<String, Long> counts = new HashMap<>();
        for (ItemStack stack : session.funnelBuffer) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) {
                continue;
            }
            counts.merge(id.toString(), (long) stack.getCount(), Long::sum);
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.comparingByKey());
        Map<String, Long> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : sorted) {
            ordered.put(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    private static boolean absorbNearbyDropsIntoLinked(ServerPlayer player, BlockPos pos, Session session) {
        if (session.linkedPositions.isEmpty()) {
            return false;
        }
        List<LinkedHandler> linked = resolveLinkedHandlers(player, session);
        if (linked.isEmpty()) {
            return false;
        }
        List<IItemHandler> handlers = new ArrayList<>(linked.size());
        for (LinkedHandler handler : linked) {
            handlers.add(handler.handler());
        }

        AABB box = new AABB(pos).inflate(1.25D);
        List<ItemEntity> drops = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                box,
                entity -> entity != null && entity.isAlive() && !entity.getItem().isEmpty());
        boolean changed = false;
        for (ItemEntity drop : drops) {
            ItemStack original = drop.getItem();
            if (original.isEmpty()) {
                continue;
            }
            ItemStack remain = storeToLinkedOnly(handlers, original);
            if (remain.getCount() != original.getCount()) {
                changed = true;
            }
            if (remain.isEmpty()) {
                drop.discard();
            } else if (remain.getCount() != original.getCount()) {
                drop.setItem(remain);
            }
        }
        return changed;
    }

    private static void sendStorageOverflowHint(ServerPlayer player, String context, OverflowOutcome overflow) {
        if (!overflow.hasOverflow()) {
            return;
        }
        String message;
        if (overflow.movedToInventory() > 0 && overflow.dropped() > 0) {
            message = context + ": linked storage full, moved " + overflow.movedToInventory()
                    + " to inventory, dropped " + overflow.dropped() + ".";
        } else if (overflow.movedToInventory() > 0) {
            message = context + ": linked storage full, moved " + overflow.movedToInventory() + " to inventory.";
        } else {
            message = context + ": linked+inventory full, dropped " + overflow.dropped() + ".";
        }
        player.displayClientMessage(Component.literal(message), true);
    }

    private static void runQuestDetect(ServerPlayer player, Session session, boolean force) {
        if (player == null || session == null || !RtsFtbCompat.isDetectAvailable()) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (!force && now < session.nextQuestDetectTick) {
            return;
        }
        session.nextQuestDetectTick = now + QUEST_DETECT_COOLDOWN_TICKS;
        RtsFtbCompat.detectNow(player);
    }

    private static InteractionResult interactWithToolSlot(ServerPlayer player, ServerLevel level, Entity targetEntity,
            BlockHitResult blockHit, Vec3 hit, int toolSlot, RayContext rayContext) {
        int slot = clampHotbarSlot(toolSlot);
        int previousSelected = player.getInventory().selected;
        Vec3 interactionPos = resolveInteractionPosition(targetEntity, blockHit, hit);
        return withTemporaryUseItemContext(
                player,
                interactionPos,
                hit,
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> {
            player.getInventory().selected = slot;
            try {
                if (targetEntity != null) {
                    return interactEntityWithMainHand(player, level, targetEntity, hit);
                }
                if (blockHit != null) {
                    // Alt interaction: prefer non-build interaction first.
                    InteractionResult primaryResult = withTemporaryShiftKey(player, false, () -> player.gameMode.useItemOn(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND,
                            blockHit));
                    if (primaryResult.consumesAction()) {
                        return primaryResult;
                    }
                    InteractionResult primaryUseResult = withTemporaryShiftKey(player, false, () -> player.gameMode.useItem(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND));
                    if (primaryUseResult.consumesAction()) {
                        return primaryUseResult;
                    }
                    InteractionResult secondaryResult = withTemporaryShiftKey(player, true, () -> player.gameMode.useItemOn(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND,
                            blockHit));
                    if (secondaryResult.consumesAction()) {
                        return secondaryResult;
                    }
                    return withTemporaryShiftKey(player, true, () -> player.gameMode.useItem(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND));
                }
                return InteractionResult.PASS;
            } finally {
                player.getInventory().selected = previousSelected;
            }
                });
    }

    private static InteractionResult interactWithLinkedItem(ServerPlayer player, ServerLevel level, Session session,
            Entity targetEntity, BlockHitResult blockHit, Vec3 hit, String itemId, RayContext rayContext) {
        if (itemId == null || itemId.isBlank() || session.linkedPositions.isEmpty()) {
            return InteractionResult.PASS;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return InteractionResult.PASS;
        }

        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return InteractionResult.PASS;
        }

        ItemStack extracted = extractOneFromNetwork(handlers, player, BuiltInRegistries.ITEM.get(id));
        if (extracted.isEmpty()) {
            return InteractionResult.PASS;
        }

        Vec3 interactionPos = resolveInteractionPosition(targetEntity, blockHit, hit);
        UseOnOutcome outcome = withTemporaryUseItemContext(
                player,
                interactionPos,
                hit,
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> {
            if (targetEntity != null) {
                return useItemOnEntityWithMainHand(player, level, extracted, targetEntity, hit);
            }
            // Alt interaction: normal item interaction first, build-like secondary interaction later.
            UseOnOutcome primaryOn = useItemOnWithMainHand(player, level, extracted, blockHit, false);
            if (primaryOn.result().consumesAction()) {
                return primaryOn;
            }
            ItemStack afterPrimaryOn = primaryOn.remainder().isEmpty() ? extracted.copy() : primaryOn.remainder().copy();

            UseOnOutcome primaryUse = useItemWithMainHand(player, level, afterPrimaryOn, false);
            if (primaryUse.result().consumesAction()) {
                return primaryUse;
            }
            ItemStack afterPrimaryUse = primaryUse.remainder().isEmpty() ? afterPrimaryOn : primaryUse.remainder().copy();

            UseOnOutcome secondaryOn = useItemOnWithMainHand(player, level, afterPrimaryUse, blockHit, true);
            if (secondaryOn.result().consumesAction()) {
                return secondaryOn;
            }
            ItemStack afterSecondaryOn = secondaryOn.remainder().isEmpty() ? afterPrimaryUse : secondaryOn.remainder().copy();
            return useItemWithMainHand(player, level, afterSecondaryOn, true);
                });
        if (!outcome.remainder().isEmpty()) {
            refundToLinked(handlers, player, outcome.remainder());
        }
        return outcome.result();
    }

    private static InteractionResult interactEntityWithMainHand(ServerPlayer player, ServerLevel level, Entity entity,
            Vec3 hit) {
        InteractionResult result = player.interactOn(entity, InteractionHand.MAIN_HAND);
        if (!result.consumesAction()) {
            Vec3 localHit = hit.subtract(entity.position());
            result = entity.interactAt(player, localHit, InteractionHand.MAIN_HAND);
        }
        if (!result.consumesAction()) {
            result = player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
        }
        return result;
    }

    private static Vec3 resolveInteractionPosition(Entity targetEntity, BlockHitResult blockHit, Vec3 hit) {
        if (targetEntity != null) {
            Vec3 center = targetEntity.getBoundingBox().getCenter();
            Vec3 delta = center.subtract(hit);
            if (delta.lengthSqr() < 1.0e-6D) {
                delta = new Vec3(0.0D, 0.0D, 1.0D);
            }
            Vec3 at = center.subtract(delta.normalize().scale(1.8D));
            return new Vec3(at.x, at.y + 0.2D, at.z);
        }
        if (blockHit != null) {
            Vec3 n = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
            Vec3 at = blockHit.getLocation().subtract(n.scale(2.2D));
            return new Vec3(at.x, at.y + 1.1D, at.z);
        }
        return hit;
    }

    private static float[] yawPitchTo(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double xz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, xz)));
        return new float[] { yaw, pitch };
    }

    private static RayContext parseRayContext(
            double originX, double originY, double originZ,
            double dirX, double dirY, double dirZ) {
        if (!Double.isFinite(originX) || !Double.isFinite(originY) || !Double.isFinite(originZ)
                || !Double.isFinite(dirX) || !Double.isFinite(dirY) || !Double.isFinite(dirZ)) {
            return null;
        }
        Vec3 dir = new Vec3(dirX, dirY, dirZ);
        if (dir.lengthSqr() < 1.0e-6D) {
            return null;
        }
        return new RayContext(new Vec3(originX, originY, originZ), dir.normalize());
    }

    private static <T> T withTemporaryUseItemContext(ServerPlayer player, Vec3 fallbackPos, Vec3 fallbackLookAt,
            RayContext rayContext, double reach, Supplier<T> action) {
        if (rayContext == null) {
            return withTemporaryInteractionPosition(player, fallbackPos, fallbackLookAt, action);
        }
        Vec3 rayDir = rayContext.dir();
        if (!Double.isFinite(rayDir.x) || !Double.isFinite(rayDir.y) || !Double.isFinite(rayDir.z)
                || rayDir.lengthSqr() < 1.0e-6D) {
            return withTemporaryInteractionPosition(player, fallbackPos, fallbackLookAt, action);
        }
        double clampedReach = Math.max(2.0D, Math.min(8.0D, reach));
        double offset = Math.max(0.5D, clampedReach - REMOTE_POV_EPSILON);
        Vec3 normalizedDir = rayDir.normalize();
        Vec3 virtualEye = fallbackLookAt.subtract(normalizedDir.scale(offset));
        double eyeHeight = player.getEyeHeight(player.getPose());
        Vec3 virtualFeet = new Vec3(virtualEye.x, virtualEye.y - eyeHeight, virtualEye.z);
        Vec3 lookAt = virtualEye.add(normalizedDir.scale(clampedReach));
        return withTemporaryInteractionPosition(player, virtualFeet, lookAt, action);
    }

    private static <T> T withTemporaryInteractionPosition(ServerPlayer player, Vec3 position, Vec3 lookAt, Supplier<T> action) {
        Vec3 prevPos = player.position();
        float prevYRot = player.getYRot();
        float prevXRot = player.getXRot();
        float prevYHeadRot = player.getYHeadRot();
        float prevYBodyRot = player.yBodyRot;

        player.setPos(position.x, position.y, position.z);
        double eyeHeight = player.getEyeHeight(player.getPose());
        Vec3 eyePos = new Vec3(position.x, position.y + eyeHeight, position.z);
        float[] look = yawPitchTo(eyePos, lookAt);
        player.setYRot(look[0]);
        player.setXRot(look[1]);
        player.setYHeadRot(look[0]);
        player.yBodyRot = look[0];
        try {
            return action.get();
        } finally {
            player.setPos(prevPos.x, prevPos.y, prevPos.z);
            player.setYRot(prevYRot);
            player.setXRot(prevXRot);
            player.setYHeadRot(prevYHeadRot);
            player.yBodyRot = prevYBodyRot;
        }
    }

    private static <T> T withTemporaryShiftKey(ServerPlayer player, boolean active, Supplier<T> action) {
        boolean previous = player.isShiftKeyDown();
        if (previous == active) {
            return action.get();
        }
        player.setShiftKeyDown(active);
        try {
            return action.get();
        } finally {
            player.setShiftKeyDown(previous);
        }
    }

    private static UseOnOutcome useItemOnWithMainHand(ServerPlayer player, ServerLevel level, ItemStack handStack,
            BlockHitResult hit, boolean forceSecondaryUse) {
        ItemStack previousMainHand = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        InteractionResult result;
        ItemStack remainder;
        try {
            result = withTemporaryShiftKey(player, forceSecondaryUse, () -> player.gameMode.useItemOn(
                    player,
                    level,
                    player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    hit));
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new UseOnOutcome(result, remainder);
    }

    private static UseOnOutcome useItemWithMainHand(ServerPlayer player, ServerLevel level, ItemStack handStack,
            boolean forceSecondaryUse) {
        ItemStack previousMainHand = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        InteractionResult result;
        ItemStack remainder;
        try {
            result = withTemporaryShiftKey(player, forceSecondaryUse, () -> player.gameMode.useItem(
                    player,
                    level,
                    player.getMainHandItem(),
                    InteractionHand.MAIN_HAND));
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new UseOnOutcome(result, remainder);
    }

    private static UseOnOutcome useItemOnEntityWithMainHand(ServerPlayer player, ServerLevel level, ItemStack handStack,
            Entity entity, Vec3 hit) {
        ItemStack previousMainHand = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        InteractionResult result;
        ItemStack remainder;
        try {
            result = interactEntityWithMainHand(player, level, entity, hit);
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new UseOnOutcome(result, remainder);
    }

    private static void relaxOpenedMenuValidation(AbstractContainerMenu menu) {
        if (menu == null) {
            return;
        }
        Class<?> type = menu.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Class<?> fieldType = field.getType();

                    if (ContainerLevelAccess.class.isAssignableFrom(fieldType)) {
                        Object current = field.get(menu);
                        if (current instanceof ContainerLevelAccess access
                                && !(access instanceof RelaxedContainerLevelAccess)) {
                            field.set(menu, new RelaxedContainerLevelAccess(access));
                        } else if (current == null) {
                            field.set(menu, ContainerLevelAccess.NULL);
                        }
                        continue;
                    }

                    if (fieldType == Container.class) {
                        Object current = field.get(menu);
                        if (current instanceof Container delegate && !(delegate instanceof AlwaysValidContainer)) {
                            field.set(menu, new AlwaysValidContainer(delegate));
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                    // If a field is inaccessible/final in this runtime, keep default validation for that field.
                }
            }
            type = type.getSuperclass();
        }
    }

    private static BlockPos detectPlacedPos(ServerLevel level, BlockPos clickedPos, BlockState beforeClicked, BlockPos adjacentPos,
            BlockState beforeAdjacent) {
        if (!level.hasChunkAt(clickedPos)) {
            return null;
        }
        BlockState afterClicked = level.getBlockState(clickedPos);
        if (!afterClicked.equals(beforeClicked) && !afterClicked.isAir()) {
            return clickedPos;
        }

        if (beforeAdjacent == null || !level.hasChunkAt(adjacentPos)) {
            return null;
        }
        BlockState afterAdjacent = level.getBlockState(adjacentPos);
        if (!afterAdjacent.equals(beforeAdjacent) && !afterAdjacent.isAir()) {
            return adjacentPos;
        }
        return null;
    }

    private static void rotatePlacedBlock(ServerLevel level, BlockPos pos, byte rotateSteps) {
        int turns = rotateSteps & 3;
        if (turns == 0 || !level.hasChunkAt(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockState rotated = state;
        for (int i = 0; i < turns; i++) {
            rotated = rotated.rotate(Rotation.CLOCKWISE_90);
        }
        if (rotated != state) {
            level.setBlock(pos, rotated, 3);
        }
    }

    private static void refundItem(IItemHandler handler, ServerPlayer player, ItemStack stack) {
        ItemStack remain = insertToHandler(handler, stack);
        if (!remain.isEmpty()) {
            player.drop(remain, false);
        }
    }

    private static IItemHandler findHandler(ServerPlayer player, BlockPos pos) {
        if (!player.serverLevel().hasChunkAt(pos)) {
            return null;
        }
        IItemHandler direct = player.serverLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (direct != null) {
            return direct;
        }
        for (Direction direction : Direction.values()) {
            IItemHandler sided = player.serverLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
            if (sided != null) {
                return sided;
            }
        }
        return null;
    }

    private static IFluidHandler findFluidHandler(ServerPlayer player, BlockPos pos) {
        if (!player.serverLevel().hasChunkAt(pos)) {
            return null;
        }
        IFluidHandler direct = player.serverLevel().getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (direct != null) {
            return direct;
        }
        for (Direction direction : Direction.values()) {
            IFluidHandler sided = player.serverLevel().getCapability(Capabilities.FluidHandler.BLOCK, pos, direction);
            if (sided != null) {
                return sided;
            }
        }
        return null;
    }

    private static String resolveDisplayName(ServerPlayer player, BlockPos pos) {
        return player.serverLevel().getBlockState(pos).getBlock().getName().getString();
    }

    private static List<LinkedHandler> resolveLinkedHandlers(ServerPlayer player, Session session) {
        sanitizeSessionDimension(player, session);
        if (session.linkedPositions.isEmpty()) {
            return List.of();
        }

        List<LinkedHandler> out = new ArrayList<>();
        for (BlockPos pos : session.linkedPositions) {
            if (!player.serverLevel().hasChunkAt(pos)) {
                continue;
            }
            IItemHandler handler = findHandler(player, pos);
            if (handler == null) {
                continue;
            }
            String name = session.linkedNames.computeIfAbsent(pos, p -> resolveDisplayName(player, p));
            out.add(new LinkedHandler(pos, name, handler));
        }
        return out;
    }

    private static List<LinkedFluidHandler> resolveLinkedFluidHandlers(ServerPlayer player, Session session) {
        sanitizeSessionDimension(player, session);
        if (session.linkedPositions.isEmpty()) {
            return List.of();
        }

        List<LinkedFluidHandler> out = new ArrayList<>();
        for (BlockPos pos : session.linkedPositions) {
            if (!player.serverLevel().hasChunkAt(pos)) {
                continue;
            }
            IFluidHandler handler = findFluidHandler(player, pos);
            if (handler == null) {
                continue;
            }
            String name = session.linkedNames.computeIfAbsent(pos, p -> resolveDisplayName(player, p));
            out.add(new LinkedFluidHandler(pos, name, handler));
        }
        return out;
    }

    private static boolean canAccessWorldTarget(ServerPlayer player, BlockPos pos) {
        if (!RtsCameraManager.isActive(player) || pos == null) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        return true;
    }

    private static boolean canAccessFluidPlacementTarget(ServerPlayer player, BlockPos pos) {
        if (!RtsCameraManager.isActive(player) || pos == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) {
            return false;
        }

        if (level.mayInteract(player, pos) || RtsCameraManager.isWithinActionRadius(player, pos)) {
            return true;
        }

        // Fluid placement can target air blocks; also validate supporting block below.
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        BlockPos below = pos.below();
        if (!level.hasChunkAt(below)) {
            return false;
        }
        return level.mayInteract(player, below) || RtsCameraManager.isWithinActionRadius(player, below);
    }

    private static void sanitizeSessionDimension(ServerPlayer player, Session session) {
        if (session.linkedDimension == null) {
            return;
        }
        ResourceKey<Level> currentDimension = player.serverLevel().dimension();
        if (session.linkedDimension.equals(currentDimension)) {
            return;
        }
        session.linkedPositions.clear();
        session.linkedNames.clear();
        session.linkedDimension = null;
        session.page = 0;
        saveSessionToPlayerNbt(player, session);
    }

    private static String buildLinkedSummary(Session session) {
        int count = session.linkedPositions.size();
        if (count <= 0) {
            return "No Storage";
        }
        if (count == 1) {
            BlockPos pos = session.linkedPositions.get(0);
            return session.linkedNames.getOrDefault(pos, "Linked Storage");
        }
        return count + " linked storages";
    }

    private static List<Long> toPackedPositions(List<BlockPos> positions) {
        List<Long> packed = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            packed.add(pos.asLong());
        }
        return packed;
    }

    private record Entry(String itemId, String namespace, String path, long count) {
    }

    private record FluidEntry(String fluidId, String namespace, String path, long amount, long capacity) {
    }

    private record RecipeAvailability(boolean craftable, String missingSummary, int missingTotal) {
    }

    private record CraftableCandidate(
            String recipeId,
            String resultItemId,
            int resultCount,
            String resultLabel,
            boolean craftable,
            String missingSummary,
            int missingTotal) {
        private boolean isPreferredOver(CraftableCandidate other) {
            if (other == null) {
                return true;
            }
            if (this.craftable != other.craftable) {
                return this.craftable;
            }
            if (this.missingTotal != other.missingTotal) {
                return this.missingTotal < other.missingTotal;
            }
            if (this.resultCount != other.resultCount) {
                return this.resultCount > other.resultCount;
            }
            return this.recipeId.compareToIgnoreCase(other.recipeId) < 0;
        }

        private static int compareForPanel(CraftableCandidate a, CraftableCandidate b) {
            if (a.craftable != b.craftable) {
                return a.craftable ? -1 : 1;
            }
            int byLabel = a.resultLabel.compareToIgnoreCase(b.resultLabel);
            if (byLabel != 0) {
                return byLabel;
            }
            return a.recipeId.compareToIgnoreCase(b.recipeId);
        }
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

    private record LinkedHandler(BlockPos pos, String name, IItemHandler handler) {
    }

    private record LinkedFluidHandler(BlockPos pos, String name, IFluidHandler handler) {
    }

    private record RayContext(Vec3 origin, Vec3 dir) {
    }

    private record UseOnOutcome(InteractionResult result, ItemStack remainder) {
    }

    private record ContainerDrainOutcome(FluidStack fluid, ItemStack remainder) {
        private static final ContainerDrainOutcome EMPTY = new ContainerDrainOutcome(FluidStack.EMPTY, ItemStack.EMPTY);

        private boolean isEmpty() {
            return this.fluid.isEmpty();
        }
    }

    private record OverflowOutcome(int movedToInventory, int dropped) {
        private static final OverflowOutcome EMPTY = new OverflowOutcome(0, 0);

        private OverflowOutcome merge(OverflowOutcome other) {
            return new OverflowOutcome(this.movedToInventory + other.movedToInventory, this.dropped + other.dropped);
        }

        private boolean hasOverflow() {
            return this.movedToInventory > 0 || this.dropped > 0;
        }
    }

    private record GridInsert(int slotIndex, ItemStack stack) {
    }

    private record ExtractedIngredient(ItemStack stack, boolean fromPlayer) {
    }

    private record CraftExecutionResult(boolean success, boolean storageFull, String resultItemId, int resultCount) {
        private static CraftExecutionResult failure(boolean storageFull) {
            return new CraftExecutionResult(false, storageFull, "", 0);
        }
    }

    private static final class AlwaysValidContainer implements Container {
        private final Container delegate;

        private AlwaysValidContainer(Container delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getContainerSize() {
            return this.delegate.getContainerSize();
        }

        @Override
        public boolean isEmpty() {
            return this.delegate.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return this.delegate.getItem(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return this.delegate.removeItem(slot, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return this.delegate.removeItemNoUpdate(slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            this.delegate.setItem(slot, stack);
        }

        @Override
        public int getMaxStackSize() {
            return this.delegate.getMaxStackSize();
        }

        @Override
        public void setChanged() {
            this.delegate.setChanged();
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return true;
        }

        @Override
        public void startOpen(net.minecraft.world.entity.player.Player player) {
            this.delegate.startOpen(player);
        }

        @Override
        public void stopOpen(net.minecraft.world.entity.player.Player player) {
            this.delegate.stopOpen(player);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return this.delegate.canPlaceItem(slot, stack);
        }

        @Override
        public void clearContent() {
            this.delegate.clearContent();
        }
    }

    private static final class RelaxedContainerLevelAccess implements ContainerLevelAccess {
        private final ContainerLevelAccess delegate;

        private RelaxedContainerLevelAccess(ContainerLevelAccess delegate) {
            this.delegate = delegate == null ? ContainerLevelAccess.NULL : delegate;
        }

        @Override
        public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> evaluator) {
            Optional<T> result = this.delegate.evaluate(evaluator);
            if (result.isPresent() && result.get() instanceof Boolean) {
                @SuppressWarnings("unchecked")
                T forcedTrue = (T) Boolean.TRUE;
                return Optional.of(forcedTrue);
            }
            return result;
        }

        @Override
        public void execute(BiConsumer<Level, BlockPos> consumer) {
            this.delegate.execute(consumer);
        }
    }

    private static final class Session {
        private BuilderMode mode = BuilderMode.INTERACT;
        private final List<BlockPos> linkedPositions = new ArrayList<>();
        private final Map<BlockPos, String> linkedNames = new HashMap<>();
        private ResourceKey<Level> linkedDimension;
        private int page;
        private String search = "";
        private String category = "all";
        private RtsStorageSort sort = RtsStorageSort.QUANTITY;
        private boolean ascending = false;
        private String craftSearch = "";
        private boolean craftShowUnavailable;
        private boolean autoStoreMinedDrops = true;
        private final Map<String, Long> internalFluidMb = new HashMap<>();
        private boolean funnelEnabled;
        private BlockPos funnelTarget;
        private int funnelTickCooldown;
        private final List<ItemStack> funnelBuffer = new ArrayList<>();
        private BlockPos miningPos;
        private Direction miningFace = Direction.DOWN;
        private int miningToolSlot;
        private float miningProgress;
        private int miningStage = -1;
        private long nextQuestDetectTick;
    }
}
