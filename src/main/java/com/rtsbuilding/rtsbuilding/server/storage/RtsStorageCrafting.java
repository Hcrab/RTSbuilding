package com.rtsbuilding.rtsbuilding.server.storage;


import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;


import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
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

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.ftb.RtsFtbCompat;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.compat.sophisticatedstorage.RtsSophisticatedStorageCompat;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsStoreFluidPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.FluidTags;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.data.RtsStorageSessionStore;

/**
 * Server-side crafting service for RTS linked storage.
 *
 * <p>This helper owns the crafting-specific state machine that used to live in
 * {@link RtsStorageManager}: craftable list requests, recipe availability scans,
 * recipe-to-linked execution, craft terminal refill/session behavior, JEI grid
 * transfer, and crafted-output recent entries.
 *
 * <p>It deliberately does not own storage page construction, raw item transfer
 * rules, fluid storage, remote mining, quick-build placement, packet payload
 * decoding, or persistent session format. Those responsibilities stay with
 * {@link RtsStoragePageBuilder}, {@link RtsStorageTransfers}, and
 * {@link RtsStorageManager}. Player behavior must remain unchanged: the same
 * buttons open the same terminal, craft the same recipes, refill the same grid,
 * and report the same missing/storage-full feedback in multiplayer.
 */
public final class RtsStorageCrafting {
    private static final PreviewCraftingMenu PREVIEW_CRAFTING_MENU = new PreviewCraftingMenu();

    private RtsStorageCrafting() {
    }

    private static Set<String> sanitizeLocalizedSearchMatches(List<String> localizedSearchMatches) {
        return RtsStoragePageBuilder.sanitizeLocalizedSearchMatches(localizedSearchMatches);
    }

    /**
     * Records an output stack that the player manually takes from the craft terminal.
     *
     * <p>This is separate from recipe-to-linked crafting because vanilla menu clicks
     * produce the carried output before the refill loop runs. It owns only the
     * crafted recent-entry update; it does not move the output stack, alter the
     * menu click behavior, or change the old session-save timing.
     */
    public static void recordCraftedOutput(ServerPlayer player, RtsStorageSession session, ItemStack crafted) {
        if (player == null || crafted == null || crafted.isEmpty()) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsStorageRecentEntries.recordCraftedOutput(session, crafted);
    }

    /**
     * Opens the remote crafting terminal from RTS mode.
     *
     * <p>This owns only the crafting terminal session setup: progression gate,
     * linked-storage prerequisite, vanilla crafting menu creation, relaxed menu
     * validation for remote access, and storage page refresh. It does not own
     * external storage GUI opening, mining, placement, fluids, or packet payload
     * decoding. The player-facing behavior must stay the same: pressing the
     * craft terminal button opens the same 3x3 crafting menu only after storage
     * is linked, then refreshes the RTS storage browser behind it.
     */
    public static void openCraftTerminal(ServerPlayer player, RtsStorageSession session) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
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
        RtsStorageManager.relaxOpenedMenuValidation(player.containerMenu);
        RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    /**
     * Scans the server recipe manager for recipes the linked RTS storage can craft.
     *
     * <p>This method owns the craftable-panel search state, availability scan,
     * grouped recipe options, and S2C craftable payload. It deliberately does
     * not assemble storage pages, move fluids, mutate block placement, or decode
     * network packets. The UI contract must remain unchanged: blank search still
     * returns an empty craftable panel, unavailable recipes obey the existing
     * toggle, and pagination appends with the same offsets.
     */
    public static void requestCraftables(ServerPlayer player, RtsStorageSession session, String search, boolean showUnavailable, int offset, int limit,
            boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        if (session == null) {
            return;
        }
        session.craftSearch = search == null ? "" : search.trim();
        session.craftShowUnavailable = showUnavailable;
        session.craftPinyinSearchEnabled = pinyinSearchEnabled;
        session.craftLocalizedSearchMatches.clear();
        session.craftLocalizedSearchMatches.addAll(sanitizeLocalizedSearchMatches(localizedSearchMatches));
        int batchOffset = Math.max(0, offset);
        int batchLimit = Math.max(1, limit);
        session.craftRequestedCount = Math.max(RtsStorageManager.CRAFTABLE_BATCH_SIZE, batchOffset + batchLimit);
        RtsStorageManager.saveSessionToPlayerNbt(player, session);

        if (session.craftSearch.isBlank()) {
            sendCraftables(player, session, List.of(), 0, false, false);
            return;
        }

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            sendCraftables(player, session, List.of(), 0, false, false);
            return;
        }

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            sendCraftables(player, session, List.of(), 0, false, false);
            return;
        }

        Map<String, Long> availableCounts = summarizeAvailableCraftItems(player, session, activeLinked);
        Map<String, List<CraftableCandidate>> byResultItem = new LinkedHashMap<>();
        var recipeManager = player.serverLevel().getRecipeManager();
        for (ResourceLocation id : recipeManager.getRecipeIds().toList()) {
            Recipe<?> raw = recipeManager.byKey(id).orElse(null);
            if (!(raw instanceof CraftingRecipe craftingRecipe)) {
                continue;
            }
            if (!supportsWorkbenchCraftPanelRecipe(craftingRecipe)) {
                continue;
            }
            CraftableCandidate candidate = buildCraftableCandidate(
                    player,
                    id,
                    craftingRecipe,
                    availableCounts,
                    session.craftSearch,
                    session.craftPinyinSearchEnabled,
                    session.craftLocalizedSearchMatches);
            if (candidate == null) {
                continue;
            }
            byResultItem.computeIfAbsent(candidate.resultItemId(), ignored -> new ArrayList<>()).add(candidate);
        }

        List<CraftableGroupEntry> groupedEntries = new ArrayList<>(byResultItem.size());
        for (List<CraftableCandidate> options : byResultItem.values()) {
            if (options == null || options.isEmpty()) {
                continue;
            }
            options.sort(CraftableCandidate::compareForRecipeSelection);
            boolean anyCraftable = options.stream().anyMatch(CraftableCandidate::craftable);
            if (!session.craftShowUnavailable && !anyCraftable) {
                continue;
            }
            groupedEntries.add(new CraftableGroupEntry(options.get(0), List.copyOf(options)));
        }

        groupedEntries.sort(CraftableGroupEntry::compareForPanel);
        int safeOffset = Math.min(groupedEntries.size(), batchOffset);
        int endExclusive = Math.min(groupedEntries.size(), safeOffset + batchLimit);
        boolean append = safeOffset > 0;
        boolean hasMore = endExclusive < groupedEntries.size();
        sendCraftables(player, session, new ArrayList<>(groupedEntries.subList(safeOffset, endExclusive)), safeOffset, append, hasMore);
    }

    /**
     * Crafts a selected 3x3 recipe directly into linked storage.
     *
     * <p>The helper owns ingredient selection, repeated craft attempts, output
     * rollback on storage-full failure, recent crafted-item recording, feedback
     * packets, and the post-craft quest detect hook. It continues to delegate
     * all actual stack insertion and extraction to {@link RtsStorageTransfers}
     * so NBT-heavy stacks and capability-backed items follow the same transfer
     * rules as the storage browser. Players must see the same one-click craft,
     * multi-craft, missing ingredient, and storage-full behavior as before.
     */
    public static void craftRecipeToLinked(ServerPlayer player, RtsStorageSession session, String recipeId, int craftCount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            refreshCraftables(player, session);
            return;
        }
        if (recipeId == null || recipeId.isBlank()) {
            refreshCraftables(player, session);
            return;
        }

        ResourceLocation key = ResourceLocation.tryParse(recipeId);
        if (key == null) {
            refreshCraftables(player, session);
            return;
        }

        Recipe<?> raw = player.serverLevel().getRecipeManager().byKey(key).orElse(null);
        if (!(raw instanceof CraftingRecipe craftingRecipe)) {
            refreshCraftables(player, session);
            return;
        }
        if (!supportsWorkbenchCraftPanelRecipe(craftingRecipe)) {
            refreshCraftables(player, session);
            return;
        }

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            refreshCraftables(player, session);
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        boolean includePlayerFallback = RtsLinkedStorageResolver.hasAnyStorage(player, session)
                && !(player.containerMenu instanceof RtsCraftTerminalMenu);
        ItemStack previewResult = resolveCraftablePreviewResult(craftingRecipe, player);
        String resultLabel = previewResult.isEmpty() ? "item" : previewResult.getHoverName().getString();
        ResourceLocation previewResultId = previewResult.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(previewResult.getItem());
        int requestedCrafts = Math.max(1, Math.min(999, craftCount));
        int completedCrafts = 0;
        int totalCraftedCount = 0;
        boolean storageFull = false;
        String craftedItemId = previewResultId == null ? "" : previewResultId.toString();
        Map<String, Integer> consumedCounts = new LinkedHashMap<>();

        for (int i = 0; i < requestedCrafts; i++) {
            CraftExecutionResult result = craftSingleRecipeToLinked(player, handlers, craftingRecipe, includePlayerFallback);
            if (!result.success()) {
                storageFull = result.storageFull();
                break;
            }
            completedCrafts++;
            totalCraftedCount += result.resultCount();
            if (!result.resultItemId().isBlank()) {
                craftedItemId = result.resultItemId();
            }
            mergeConsumedCounts(consumedCounts, result.consumedCounts());
        }

        RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        refreshCraftables(player, session);
        if (completedCrafts <= 0) {
            if (storageFull) {
                player.displayClientMessage(Component.literal("Craft: linked storage is full."), true);
            } else {
                player.displayClientMessage(Component.literal("Craft: missing ingredients."), true);
            }
            return;
        }

        RtsStorageRecentEntries.recordRecentItem(session, craftedItemId, S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, totalCraftedCount);
        RtsStorageManager.saveSessionToPlayerNbt(player, session);
        PacketDistributor.sendToPlayer(player, new S2CRtsCraftFeedbackPayload(
                craftedItemId,
                totalCraftedCount,
                new ArrayList<>(consumedCounts.keySet()),
                new ArrayList<>(consumedCounts.values())));
        StringBuilder summary = new StringBuilder("Crafted ")
                .append(totalCraftedCount)
                .append(" ")
                .append(resultLabel);
        if (completedCrafts < requestedCrafts) {
            summary.append(" (").append(completedCrafts).append("/").append(requestedCrafts).append(" crafts)");
            summary.append(storageFull ? ", linked storage full." : ", missing ingredients for the rest.");
        } else {
            summary.append(".");
        }
        player.displayClientMessage(Component.literal(summary.toString()), true);
        RtsStorageManager.runQuestDetect(player, session, false);
    }

    private static void sendCraftables(
            ServerPlayer player,
            RtsStorageSession session,
            List<CraftableGroupEntry> candidates,
            int offset,
            boolean append,
            boolean hasMore) {
        List<String> recipeIds = new ArrayList<>(candidates.size());
        List<String> resultItemIds = new ArrayList<>(candidates.size());
        List<Integer> resultCounts = new ArrayList<>(candidates.size());
        List<Boolean> craftable = new ArrayList<>(candidates.size());
        List<String> missingSummaries = new ArrayList<>(candidates.size());
        List<Integer> recipeOptionCounts = new ArrayList<>(candidates.size());
        List<String> optionRecipeIds = new ArrayList<>();
        List<Integer> optionResultCounts = new ArrayList<>();
        List<Boolean> optionCraftable = new ArrayList<>();
        List<String> optionSummaries = new ArrayList<>();
        List<String> optionMissingSummaries = new ArrayList<>();
        for (CraftableGroupEntry group : candidates) {
            CraftableCandidate candidate = group.primary();
            recipeIds.add(candidate.recipeId());
            resultItemIds.add(candidate.resultItemId());
            resultCounts.add(candidate.resultCount());
            craftable.add(candidate.craftable());
            missingSummaries.add(candidate.missingSummary());
            recipeOptionCounts.add(group.options().size());
            for (CraftableCandidate option : group.options()) {
                optionRecipeIds.add(option.recipeId());
                optionResultCounts.add(option.resultCount());
                optionCraftable.add(option.craftable());
                optionSummaries.add(option.recipeSummary());
                optionMissingSummaries.add(option.missingSummary());
            }
        }

        PacketDistributor.sendToPlayer(player, new S2CRtsCraftablesPayload(
                session.craftSearch,
                session.craftShowUnavailable,
                Math.max(0, offset),
                append,
                hasMore,
                recipeIds,
                resultItemIds,
                resultCounts,
                craftable,
                missingSummaries,
                recipeOptionCounts,
                optionRecipeIds,
                optionResultCounts,
                optionCraftable,
                optionSummaries,
                optionMissingSummaries));
    }

    private static void refreshCraftables(ServerPlayer player, RtsStorageSession session) {
        requestCraftables(
                player,
                session,
                session.craftSearch,
                session.craftShowUnavailable,
                0,
                Math.max(RtsStorageManager.CRAFTABLE_BATCH_SIZE, session.craftRequestedCount),
                session.craftPinyinSearchEnabled,
                List.copyOf(session.craftLocalizedSearchMatches));
    }

    private static Map<String, Long> summarizeAvailableCraftItems(ServerPlayer player, RtsStorageSession session, List<LinkedHandler> activeLinked) {
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
                RtsStoragePageBuilder.mergeCount(counts, id.toString(), RtsStoragePageBuilder.getHandlerReportedCount(handler, i, stack));
            }
        }

        boolean includePlayerMainInventory = RtsLinkedStorageResolver.hasAnyStorage(player, session)
                && !(player.containerMenu instanceof RtsCraftTerminalMenu);
        if (includePlayerMainInventory) {
            RtsStoragePageBuilder.accumulatePlayerMainInventoryCounts(player, counts, new HashMap<>());
        }
        return counts;
    }

    private static CraftableCandidate buildCraftableCandidate(ServerPlayer player, ResourceLocation recipeId, CraftingRecipe recipe,
            Map<String, Long> availableCounts, String search, boolean pinyinSearchEnabled,
            Set<String> localizedSearchMatches) {
        if (player == null || recipeId == null || recipe == null) {
            return null;
        }

        ItemStack result = resolveCraftablePreviewResult(recipe, player);
        if (result.isEmpty()) {
            return null;
        }

        ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
        if (resultId == null) {
            return null;
        }
        String resultLabel = result.getHoverName().getString();
        if (!matchesCraftablesSearch(resultId, resultLabel, search, pinyinSearchEnabled, localizedSearchMatches)) {
            return null;
        }

        RecipeAvailability availability = evaluateRecipeAvailability(recipe, availableCounts);
        return new CraftableCandidate(
                recipeId.toString(),
                resultId.toString(),
                Math.max(1, result.getCount()),
                resultLabel,
                availability.craftable(),
                availability.missingSummary(),
                availability.missingTotal(),
                buildRecipeSummary(recipe));
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

        ItemStack result = recipe.getResultItem(player.serverLevel().registryAccess());
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

        TransientCraftingContainer input = createCraftingGrid(previewStacks);
        ItemStack assembled = recipe.assemble(input, player.serverLevel().registryAccess());
        return assembled.isEmpty() ? ItemStack.EMPTY : assembled.copy();
    }

    private static TransientCraftingContainer createCraftingGrid(List<ItemStack> stacks) {
        TransientCraftingContainer container = new TransientCraftingContainer(PREVIEW_CRAFTING_MENU, 3, 3);
        if (stacks == null) {
            return container;
        }
        for (int i = 0; i < Math.min(9, stacks.size()); i++) {
            ItemStack stack = stacks.get(i);
            container.setItem(i, stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return container;
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

    private static boolean matchesCraftablesSearch(ResourceLocation resultId, String resultLabel, String search,
            boolean pinyinSearchEnabled, Set<String> localizedSearchMatches) {
        String query = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) {
            return true;
        }
        String rawId = resultId.toString().toLowerCase(Locale.ROOT);
        if (localizedSearchMatches != null && localizedSearchMatches.contains(rawId)) {
            return true;
        }
        String label = resultLabel == null ? "" : resultLabel.toLowerCase(Locale.ROOT);
        String namespace = resultId.getNamespace().toLowerCase(Locale.ROOT);
        for (String token : query.split("\\s+")) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (token.startsWith("@")) {
                String modQuery = token.substring(1).trim();
                if (!modQuery.isEmpty() && !namespace.contains(modQuery)) {
                    return false;
                }
                continue;
            }
            if (!rawId.contains(token) && !label.contains(token)
                    && !(pinyinSearchEnabled && RtsPinyinSearch.contains(resultLabel, token))) {
                return false;
            }
        }
        return true;
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

    private static String buildRecipeSummary(CraftingRecipe recipe) {
        if (recipe == null) {
            return "Recipe";
        }
        Map<String, Integer> ingredients = new LinkedHashMap<>();
        for (Ingredient ingredient : mapCraftingIngredients(recipe)) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            ingredients.merge(resolveIngredientLabel(ingredient), 1, Integer::sum);
        }
        if (ingredients.isEmpty()) {
            return "Recipe";
        }
        StringBuilder summary = new StringBuilder();
        int index = 0;
        int total = ingredients.size();
        for (var entry : ingredients.entrySet()) {
            if (index > 0) {
                summary.append(", ");
            }
            summary.append(entry.getKey());
            if (entry.getValue() > 1) {
                summary.append(" x").append(entry.getValue());
            }
            index++;
            if (index >= 3 && total > index) {
                summary.append("...");
                break;
            }
        }
        return summary.isEmpty() ? "Recipe" : summary.toString();
    }

    private static void mergeConsumedCounts(Map<String, Integer> into, Map<String, Integer> added) {
        if (into == null || added == null || added.isEmpty()) {
            return;
        }
        for (var entry : added.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            int delta = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            if (delta <= 0) {
                continue;
            }
            into.merge(entry.getKey(), delta, Integer::sum);
        }
    }

    private static Map<String, Integer> collectConsumedCounts(ExtractedIngredient[] extracted) {
        Map<String, Integer> consumed = new LinkedHashMap<>();
        if (extracted == null) {
            return consumed;
        }
        for (ExtractedIngredient ingredient : extracted) {
            if (ingredient == null || ingredient.stack().isEmpty()) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(ingredient.stack().getItem());
            if (itemId == null) {
                continue;
            }
            consumed.merge(itemId.toString(), Math.max(1, ingredient.stack().getCount()), Integer::sum);
        }
        return consumed;
    }

    /**
     * Refills an open crafting grid from linked storage using a one-item blueprint.
     *
     * <p>This keeps the craft terminal's click-result loop intact: after the
     * player takes an output stack, the same recipe shape is refilled from
     * linked storage and then from allowed player inventory fallback. It does
     * not decide how stacks are inserted into storage; that remains in
     * {@link RtsStorageTransfers}.
     */
    public static void refillCraftGridFromLinked(ServerPlayer player, RtsStorageSession session, CraftingMenu craftingMenu, ItemStack[] blueprint) {
        refillCraftGridFromLinked(player, session, craftingMenu, blueprint, null);
    }

    public static void refillCraftGridFromLinked(
            ServerPlayer player,
            RtsStorageSession session,
            CraftingMenu craftingMenu,
            ItemStack[] blueprint,
            CraftingRecipe recipe) {
        if (session == null || craftingMenu == null || blueprint == null || blueprint.length != 9) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            return;
        }

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        Ingredient[] ingredients = recipe == null ? null : mapCraftingIngredients(recipe);
        refillCraftGridFromBlueprint(craftingMenu, handlers, player, blueprint, ingredients, false, true);
        craftingMenu.broadcastChanges();
        RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    /**
     * Rehydrates the current crafting grid from item ids sent by the client.
     *
     * <p>This is the server-side continuation of the existing craft refill
     * packet. It may record the result item as recent, but it must not change
     * packet fields, UI state names, or the player-visible blueprint matching
     * behavior. Invalid ids still become empty blueprint slots.
     */
    public static void refillCurrentCraftGridFromBlueprintIds(
            ServerPlayer player,
            RtsStorageSession session,
            List<String> blueprintIds,
            String craftedItemId,
            int craftedCount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        if (player == null || blueprintIds == null || blueprintIds.size() != 9) {
            return;
        }
        if (!(player.containerMenu instanceof CraftingMenu craftingMenu)) {
            return;
        }

        if (session != null && craftedItemId != null && !craftedItemId.isBlank() && craftedCount > 0) {
            RtsStorageRecentEntries.recordRecentItem(session, craftedItemId, S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, craftedCount);
            RtsStorageManager.saveSessionToPlayerNbt(player, session);
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
        refillCraftGridFromLinked(player, session, craftingMenu, blueprint);
    }

    /**
     * Applies a JEI recipe transfer into the current crafting grid.
     *
     * <p>The method owns only crafting-grid population and recipe-result sync.
     * It should preserve the old behavior for max-transfer, clear-grid-first,
     * linked storage priority, player inventory fallback, quest detect, and
     * storage page refresh. It does not own JEI integration registration or
     * packet payload shape.
     */
    public static void applyJeiTransfer(
            ServerPlayer player,
            RtsStorageSession session,
            String recipeId,
            List<ItemStack> ingredientPrototypes,
            boolean maxTransfer,
            boolean clearGridFirst) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.JEI_TRANSFER)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
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
        Recipe<?> raw = player.serverLevel().getRecipeManager().byKey(key).orElse(null);
        if (!(raw instanceof CraftingRecipe craftingRecipe)) {
            return;
        }

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        Ingredient[] required = mapCraftingIngredients(craftingRecipe);
        if (required.length != 9) {
            return;
        }
        ItemStack[] preferredPrototypes = sanitizeIngredientPrototypes(required, ingredientPrototypes);

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

                ItemStack extracted = extractOneMatchingIngredientCombined(handlers, player, ingredient, preferredPrototypes[i]);
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
            RtsStorageTransfers.storeToLinkedWithFallbackPreferExisting(handlers, player, stack);
        }
        refreshCraftingResult(craftingMenu);
        craftingMenu.broadcastChanges();
        RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        if (anyInserted) {
            RtsStorageManager.runQuestDetect(player, session, false);
        }
    }

    private static ItemStack[] sanitizeIngredientPrototypes(Ingredient[] required, List<ItemStack> prototypes) {
        ItemStack[] sanitized = new ItemStack[9];
        for (int i = 0; i < sanitized.length; i++) {
            sanitized[i] = ItemStack.EMPTY;
        }
        if (required == null || required.length != 9 || prototypes == null) {
            return sanitized;
        }
        for (int i = 0; i < sanitized.length && i < prototypes.size(); i++) {
            Ingredient ingredient = required[i];
            ItemStack prototype = prototypes.get(i);
            if (ingredient == null || ingredient.isEmpty() || prototype == null || prototype.isEmpty()) {
                continue;
            }
            if (ingredient.test(prototype)) {
                ItemStack copy = prototype.copy();
                copy.setCount(1);
                sanitized[i] = copy;
            }
        }
        return sanitized;
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

        TransientCraftingContainer input = createCraftingGrid(inputStacks);
        if (!recipe.matches(input, player.serverLevel())) {
            rollbackCraftIngredients(handlers, player, extracted);
            return CraftExecutionResult.failure(false);
        }

        ItemStack result = recipe.assemble(input, player.serverLevel().registryAccess());
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

        List<ItemStack> storedOutputs = new ArrayList<>(outputs.size());
        for (ItemStack stack : outputs) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remain = RtsStorageTransfers.storeToLinkedOnlyPreferExisting(handlers, stack);
            int storedCount = Math.max(0, stack.getCount() - remain.getCount());
            if (storedCount > 0) {
                storedOutputs.add(stack.copyWithCount(storedCount));
            }
            if (!remain.isEmpty()) {
                rollbackStoredCraftOutputs(handlers, storedOutputs);
                rollbackCraftIngredients(handlers, player, extracted);
                return CraftExecutionResult.failure(true);
            }
        }

        ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
        return new CraftExecutionResult(
                true,
                false,
                resultId == null ? "" : resultId.toString(),
                Math.max(1, result.getCount()),
                collectConsumedCounts(extracted));
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
                RtsStorageTransfers.moveToPlayerInventoryOnly(player, ingredient.stack());
                continue;
            }
            ItemStack remain = RtsStorageTransfers.storeToLinkedOnlyPreferExisting(handlers, ingredient.stack());
            if (!remain.isEmpty()) {
                RtsStorageTransfers.moveToPlayerInventoryOnly(player, remain);
            }
        }
    }

    private static void rollbackStoredCraftOutputs(List<IItemHandler> handlers, List<ItemStack> storedOutputs) {
        for (int i = storedOutputs.size() - 1; i >= 0; i--) {
            ItemStack stored = storedOutputs.get(i);
            int remaining = stored.getCount();
            while (remaining > 0) {
                ItemStack extracted = RtsStorageTransfers.extractOneMatchingPrototypeFromLinked(handlers, stored);
                if (extracted.isEmpty()) {
                    break;
                }
                remaining -= extracted.getCount();
            }
        }
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
                if (!preferred.isEmpty() && !ItemStack.isSameItemSameTags(stack, preferred)) {
                    continue;
                }
                ItemStack extracted = handler.extractItem(slot, 1, false);
                if (extracted.isEmpty()) {
                    continue;
                }
                if (ingredient.test(extracted)) {
                    return extracted;
                }
                ItemStack remainder = RtsStorageTransfers.insertToHandlerPreferExisting(handler, extracted);
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
        int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
        int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
        for (int i = start; i < end; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (current.isEmpty() || !ingredient.test(current)) {
                continue;
            }
            if (requirePreferredMatch && !ItemStack.isSameItemSameTags(current, preferred)) {
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
            if (!current.isEmpty() && ItemStack.isSameItemSameTags(current, insert.stack()) && current.getCount() > 0) {
                current.shrink(1);
                if (current.isEmpty()) {
                    grid.set(ItemStack.EMPTY);
                } else {
                    grid.set(current);
                }
                grid.setChanged();
            }
            RtsStorageTransfers.storeToLinkedWithFallbackPreferExisting(handlers, player, insert.stack());
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

    /**
     * Captures a one-item-per-slot blueprint of the current crafting grid.
     *
     * <p>The blueprint is intentionally a shape/prototype snapshot, not a stack
     * ownership record. Taking a craft result should still refill matching slots
     * without preserving source-slot identity.
     */
    public static ItemStack[] snapshotCraftGridBlueprint(CraftingMenu menu) {
        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            Slot grid = menu.getSlot(1 + i);
            ItemStack stack = grid.getItem();
            blueprint[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
        return blueprint;
    }

    /**
     * Performs the low-level grid refill loop from linked storage/player fallback.
     *
     * <p>This helper mutates only the open crafting grid and then asks the menu
     * to recompute its result. It must keep the existing fill-one-pass versus
     * fill-all behavior used by normal result clicks and shift-craft imports.
     */
    public static void refillCraftGridFromBlueprint(CraftingMenu menu, List<IItemHandler> handlers, ServerPlayer player,
            ItemStack[] blueprint, boolean fillAll, boolean includePlayerFallback) {
        refillCraftGridFromBlueprint(menu, handlers, player, blueprint, null, fillAll, includePlayerFallback);
    }

    public static void refillCraftGridFromBlueprint(CraftingMenu menu, List<IItemHandler> handlers, ServerPlayer player,
            ItemStack[] blueprint, Ingredient[] ingredients, boolean fillAll, boolean includePlayerFallback) {
        if (blueprint == null || blueprint.length != 9) {
            return;
        }

        int maxPasses = fillAll ? 64 : 1;
        boolean changed = false;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean inserted = false;
            for (int i = 0; i < 9; i++) {
                ItemStack blueprintStack = blueprint[i];
                Ingredient ingredient = ingredients != null && i < ingredients.length ? ingredients[i] : Ingredient.EMPTY;
                boolean hasBlueprint = blueprintStack != null && !blueprintStack.isEmpty();
                boolean hasIngredient = ingredient != null && !ingredient.isEmpty();
                if (!hasBlueprint && !hasIngredient) {
                    continue;
                }
                Slot grid = menu.getSlot(1 + i);
                ItemStack current = grid.getItem();
                if (!current.isEmpty()) {
                    if (hasIngredient ? !ingredient.test(current) : !ItemStack.isSameItemSameTags(current, blueprintStack)) {
                        continue;
                    }
                    if (current.getCount() >= current.getMaxStackSize()) {
                        continue;
                    }
                    ItemStack extracted = includePlayerFallback
                            ? RtsStorageTransfers.extractOneMatchingPrototypeCombined(handlers, player, current)
                            : RtsStorageTransfers.extractOneMatchingPrototypeFromLinked(handlers, current);
                    if (extracted.isEmpty() || !ItemStack.isSameItemSameTags(current, extracted)) {
                        if (!extracted.isEmpty()) {
                            RtsStorageTransfers.storeToLinkedWithFallbackPreferExisting(handlers, player, extracted);
                        }
                        continue;
                    }
                    current.grow(1);
                    grid.setChanged();
                    inserted = true;
                    changed = true;
                    continue;
                }

                ItemStack extracted = extractCraftGridRefillStack(
                        handlers,
                        player,
                        hasIngredient ? ingredient : Ingredient.EMPTY,
                        hasBlueprint ? blueprintStack : ItemStack.EMPTY,
                        includePlayerFallback);
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

    private static ItemStack extractCraftGridRefillStack(
            List<IItemHandler> handlers,
            ServerPlayer player,
            Ingredient ingredient,
            ItemStack preferred,
            boolean includePlayerFallback) {
        boolean hasIngredient = ingredient != null && !ingredient.isEmpty();
        if (hasIngredient) {
            ItemStack extracted = includePlayerFallback
                    ? extractOneMatchingIngredientCombined(handlers, player, ingredient, preferred)
                    : extractOneMatchingIngredient(handlers, ingredient, preferred);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        if (preferred == null || preferred.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return includePlayerFallback
                ? RtsStorageTransfers.extractOneMatchingPrototypeCombined(handlers, player, preferred)
                : RtsStorageTransfers.extractOneMatchingPrototypeFromLinked(handlers, preferred);
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

    private record RecipeAvailability(boolean craftable, String missingSummary, int missingTotal) {
    }

    private record CraftableGroupEntry(CraftableCandidate primary, List<CraftableCandidate> options) {
        private static int compareForPanel(CraftableGroupEntry a, CraftableGroupEntry b) {
            if (a == null && b == null) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            return CraftableCandidate.compareForPanel(a.primary(), b.primary());
        }
    }

    private record CraftableCandidate(
            String recipeId,
            String resultItemId,
            int resultCount,
            String resultLabel,
            boolean craftable,
            String missingSummary,
            int missingTotal,
            String recipeSummary) {
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

        private static int compareForRecipeSelection(CraftableCandidate a, CraftableCandidate b) {
            if (a == null && b == null) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            if (a.isPreferredOver(b)) {
                return b.isPreferredOver(a) ? 0 : -1;
            }
            if (b.isPreferredOver(a)) {
                return 1;
            }
            return a.recipeId.compareToIgnoreCase(b.recipeId);
        }
    }

    private static final class PreviewCraftingMenu extends AbstractContainerMenu {
        private PreviewCraftingMenu() {
            super(null, -1);
        }

        @Override
        public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return false;
        }
    }

    private record GridInsert(int slotIndex, ItemStack stack) {
    }

    private record ExtractedIngredient(ItemStack stack, boolean fromPlayer) {
    }

    private record CraftExecutionResult(
            boolean success,
            boolean storageFull,
            String resultItemId,
            int resultCount,
            Map<String, Integer> consumedCounts) {
        private static CraftExecutionResult failure(boolean storageFull) {
            return new CraftExecutionResult(false, storageFull, "", 0, Map.of());
        }
    }

}
