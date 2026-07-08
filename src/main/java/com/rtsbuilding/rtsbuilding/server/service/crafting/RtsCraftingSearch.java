package com.rtsbuilding.rtsbuilding.server.service.crafting;

import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.items.IItemHandler;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

import java.util.*;

/**
 * Handles craftable-panel search, recipe scanning, and candidate building.
 */
public final class RtsCraftingSearch {

    private RtsCraftingSearch() {
    }

    /**
     * Scans all server recipes for those the linked storage can craft, grouped by result item.
     */
    public static void requestCraftables(ServerPlayer player, RtsStorageSession session, String search,
            boolean showUnavailable, int offset, int limit,
            boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        // Search state is written into session fields, then read back for pagination.
        session.browser.craftSearch = search == null ? "" : search.trim();
        session.browser.craftShowUnavailable = showUnavailable;
        session.browser.craftPinyinSearchEnabled = pinyinSearchEnabled;
        session.browser.craftLocalizedSearchMatches.clear();
        session.browser.craftLocalizedSearchMatches.addAll(sanitizeLocalizedSearchMatches(localizedSearchMatches));
        int batchOffset = Math.max(0, offset);
        int batchLimit = Math.max(1, limit);
        session.browser.craftRequestedCount = Math.max(RtsStorageSession.CRAFTABLE_BATCH_SIZE, batchOffset + batchLimit);
        RtsSessionService.saveToPlayerNbt(player, session);

        if (session.browser.craftSearch.isBlank()) {
            sendCraftables(player, session, List.of(), 0, false, false);
            return;
        }

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            sendCraftables(player, session, List.of(), 0, false, false);
            return;
        }

        List<AvailableCraftItem> availableStacks = snapshotAvailableCraftItems(player, session, activeLinked);
        Map<String, List<CraftableCandidate>> byResultItem = new LinkedHashMap<>();
        for (ResourceLocation recipeId : player.serverLevel().getRecipeManager().getRecipeIds().toList()) {
            Recipe<?> rawRecipe = player.serverLevel().getRecipeManager().byKey(recipeId).orElse(null);
            if (!(rawRecipe instanceof CraftingRecipe craftingRecipe)) {
                continue;
            }
            if (!supportsWorkbenchCraftPanelRecipe(craftingRecipe)) {
                continue;
            }
            CraftableCandidate candidate = buildCraftableCandidate(
                    player, recipeId, craftingRecipe, availableStacks,
                    session.browser.craftSearch, session.browser.craftPinyinSearchEnabled,
                    session.browser.craftLocalizedSearchMatches);
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
            if (!session.browser.craftShowUnavailable && !anyCraftable) {
                continue;
            }
            groupedEntries.add(new CraftableGroupEntry(options.get(0), List.copyOf(options)));
        }

        groupedEntries.sort(CraftableGroupEntry::compareForPanel);
        int safeOffset = Math.min(groupedEntries.size(), batchOffset);
        int endExclusive = Math.min(groupedEntries.size(), safeOffset + batchLimit);
        boolean append = safeOffset > 0;
        boolean hasMore = endExclusive < groupedEntries.size();
        sendCraftables(player, session,
                new ArrayList<>(groupedEntries.subList(safeOffset, endExclusive)),
                safeOffset, append, hasMore);
    }

    /**
     * Refreshes the currently visible craftable panel (re-uses search state from session).
     */
    public static void refreshCraftables(ServerPlayer player, RtsStorageSession session) {
        requestCraftables(player, session,
                session.browser.craftSearch, session.browser.craftShowUnavailable,
                0, Math.max(RtsStorageSession.CRAFTABLE_BATCH_SIZE, session.browser.craftRequestedCount),
                session.browser.craftPinyinSearchEnabled,
                List.copyOf(session.browser.craftLocalizedSearchMatches));
    }

    // ---- internal helpers -------------------------------------------------------

    private static Set<String> sanitizeLocalizedSearchMatches(List<String> localizedSearchMatches) {
        return RtsStoragePageBuilder.sanitizeLocalizedSearchMatches(localizedSearchMatches);
    }

    static void sendCraftables(
            ServerPlayer player, RtsStorageSession session,
            List<CraftableGroupEntry> candidates, int offset, boolean append, boolean hasMore) {
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
                session.browser.craftSearch, session.browser.craftShowUnavailable,
                Math.max(0, offset), append, hasMore,
                recipeIds, resultItemIds, resultCounts, craftable, missingSummaries,
                recipeOptionCounts,
                optionRecipeIds, optionResultCounts, optionCraftable, optionSummaries, optionMissingSummaries));
    }

    private static List<AvailableCraftItem> snapshotAvailableCraftItems(
            ServerPlayer player, RtsStorageSession session, List<LinkedHandler> activeLinked) {
        boolean includePlayerMainInventory = session != null
                && !session.linkedStorages.isEmpty()
                && player != null
                && !(player.containerMenu instanceof com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu);
        return snapshotAvailableCraftItemsFromHandlers(
                player, RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked), includePlayerMainInventory);
    }

    private static List<AvailableCraftItem> snapshotAvailableCraftItemsFromHandlers(
            ServerPlayer player, List<IItemHandler> handlers, boolean includePlayerMainInventory) {
        List<AvailableCraftItem> entries = new ArrayList<>();
        if (handlers == null) {
            handlers = List.of();
        }
        for (IItemHandler handler : handlers) {
            if (handler == null) {
                continue;
            }
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                RtsCraftingUtils.mergeAvailableCraftItem(entries, stack,
                        RtsStoragePageBuilder.getHandlerReportedCount(handler, i, stack));
            }
        }
        if (includePlayerMainInventory && player != null) {
            int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
            int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
            for (int slot = start; slot < end; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty()) {
                    RtsCraftingUtils.mergeAvailableCraftItem(entries, stack, stack.getCount());
                }
            }
        }
        return entries;
    }

    // ---- candidate building ----------------------------------------------------

    private static CraftableCandidate buildCraftableCandidate(
            ServerPlayer player, ResourceLocation recipeId, CraftingRecipe recipe,
            List<AvailableCraftItem> availableStacks, String search,
            boolean pinyinSearchEnabled, Set<String> localizedSearchMatches) {
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
        RecipeAvailability availability = RtsCraftingAvailability.evaluateRecipeAvailability(recipe, availableStacks);
        return new CraftableCandidate(
                recipeId.toString(), resultId.toString(),
                Math.max(1, result.getCount()), resultLabel,
                availability.craftable(), availability.missingSummary(), availability.missingTotal(),
                RtsCraftingUtils.buildRecipeSummary(recipe));
    }

    /**
     * Checks whether a recipe is supported by the workbench craft panel (3x3 grid only).
     */
    static boolean supportsWorkbenchCraftPanelRecipe(CraftingRecipe recipe) {
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
            if (ingredients.size() > 9) {
                return false;
            }
        }
        boolean anyNonEmpty = false;
        for (Ingredient ingredient : RtsCraftingUtils.mapCraftingIngredients(recipe)) {
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
        Ingredient[] mapped = RtsCraftingUtils.mapCraftingIngredients(recipe);
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
        ItemStack assembled = recipe.assemble(RtsCraftingUtils.createCraftingContainer(player, previewStacks), player.serverLevel().registryAccess());
        return assembled.isEmpty() ? ItemStack.EMPTY : assembled.copy();
    }

    private static boolean matchesCraftablesSearch(
            ResourceLocation resultId, String resultLabel, String search,
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
}
