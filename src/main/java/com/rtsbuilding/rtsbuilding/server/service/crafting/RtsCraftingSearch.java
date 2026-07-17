package com.rtsbuilding.rtsbuilding.server.service.crafting;

import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsBrowserState;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;

import java.util.*;

/**
 * 可合成物品搜索器，负责扫描所有服务器配方并构建可合成面板。
 *
 * <p>核心职责：
 * <ul>
 *   <li><b>配方扫描</b>（{@link #requestCraftables}）— 遍历服务器所有 {@link net.minecraft.world.item.crafting.CraftingRecipe}，
 *   过滤出 3x3 工作台配方，评估可用材料，构建可合成候选项列表</li>
 *   <li><b>搜索过滤</b>— 支持物品 ID、显示名称、拼音（{@code pinyinSearchEnabled}）、
 *   模组命名空间（{@code @modid}）等多种搜索方式</li>
 *   <li><b>分组排序</b>— 按产出物品分组合成方案，可合成优先、标签排序</li>
 *   <li><b>刷新</b>（{@link #refreshCraftables}）— 重用会话中的搜索状态重新扫描</li>
 * </ul>
 *
 * <p>搜索状态存储在 {@link com.rtsbuilding.rtsbuilding.server.storage.session.RtsBrowserState} 中，
 * 支持分页加载。每个配方组（{@link CraftableGroupEntry}）包含多个候选方案
 * （如不同配方 ID 产出同一物品），客户端可切换选择。
 */
public final class RtsCraftingSearch {

    private RtsCraftingSearch() {
    }

    /**
     * 扫描所有服务器配方，找出链接存储可合成的配方，按结果物品分组。
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
        session.browser.craftRequestedCount = Math.max(RtsBrowserState.CRAFTABLE_BATCH_SIZE, batchOffset + batchLimit);
        RtsEffectAccumulator.INSTANCE.markPersistence(player.getUUID(), player.level().dimension());

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
        for (RecipeHolder<?> loadedHolder : player.level().recipeAccess().getRecipes()) {
            if (!(loadedHolder.value() instanceof CraftingRecipe recipe)) {
                continue;
            }
            RecipeHolder<CraftingRecipe> holder = new RecipeHolder<>(loadedHolder.id(), recipe);
            if (!supportsWorkbenchCraftPanelRecipe(holder.value())) {
                continue;
            }
            CraftableCandidate candidate = buildCraftableCandidate(
                    player, holder, availableStacks,
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
     * 刷新当前可见的可合成面板（重用会话中的搜索状态）。
     */
    public static void refreshCraftables(ServerPlayer player, RtsStorageSession session) {
        requestCraftables(player, session,
                session.browser.craftSearch, session.browser.craftShowUnavailable,
                0, Math.max(RtsBrowserState.CRAFTABLE_BATCH_SIZE, session.browser.craftRequestedCount),
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
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsCraftablesPayload(
                session.browser.craftSearch, session.browser.craftShowUnavailable,
                Math.max(0, offset), append, hasMore,
                recipeIds, resultItemIds, resultCounts, craftable, missingSummaries,
                recipeOptionCounts,
                optionRecipeIds, optionResultCounts, optionCraftable, optionSummaries, optionMissingSummaries));
    }

    private static List<AvailableCraftItem> snapshotAvailableCraftItems(
            ServerPlayer player, RtsStorageSession session, List<LinkedHandler> activeLinked) {
        boolean includePlayerMainInventory = session != null
                && !session.linkedStorageInfo.isEmpty()
                && player != null
                && !(player.containerMenu instanceof com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu);
        return snapshotAvailableCraftItemsFromHandlers(
                player, RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked), includePlayerMainInventory);
    }

    private static List<AvailableCraftItem> snapshotAvailableCraftItemsFromHandlers(
            ServerPlayer player, List<RtsItemStorage> handlers, boolean includePlayerMainInventory) {
        List<AvailableCraftItem> entries = new ArrayList<>();
        if (handlers == null) {
            handlers = List.of();
        }
        for (RtsItemStorage handler : handlers) {
            if (handler == null) {
                continue;
            }
            for (int i = 0; i < handler.slotCount(); i++) {
                ItemStack stack = handler.stackInSlot(i);
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
            ServerPlayer player, RecipeHolder<CraftingRecipe> holder,
            List<AvailableCraftItem> availableStacks, String search,
            boolean pinyinSearchEnabled, Set<String> localizedSearchMatches) {
        if (player == null || holder == null || holder.value() == null) {
            return null;
        }
        ItemStack result = resolveCraftablePreviewResult(holder.value(), player);
        if (result.isEmpty()) {
            return null;
        }
        Identifier resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
        if (resultId == null) {
            return null;
        }
        String resultLabel = result.getHoverName().getString();
        if (!matchesCraftablesSearch(resultId, resultLabel, search, pinyinSearchEnabled, localizedSearchMatches)) {
            return null;
        }
        RecipeAvailability availability = RtsCraftingAvailability.evaluateRecipeAvailability(holder.value(), availableStacks);
        return new CraftableCandidate(
                holder.id().toString(), resultId.toString(),
                Math.max(1, result.getCount()), resultLabel,
                availability.craftable(), availability.missingSummary(), availability.missingTotal(),
                RtsCraftingUtils.buildRecipeSummary(holder.value()));
    }

    /**
     * 检查配方是否受工作台合成面板支持（仅 3x3 网格）。
     */
    static boolean supportsWorkbenchCraftPanelRecipe(CraftingRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        List<Ingredient> ingredients = recipe.placementInfo().ingredients();
        if (ingredients == null || ingredients.isEmpty()) {
            return false;
        }
        if (recipe instanceof ShapedRecipe shaped) {
            if (shaped.getWidth() < 1 || shaped.getWidth() > 3 || shaped.getHeight() < 1 || shaped.getHeight() > 3) {
                return false;
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            if (shapeless.placementInfo().ingredients().isEmpty()
                    || shapeless.placementInfo().ingredients().size() > 9) {
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
        Ingredient[] mapped = RtsCraftingUtils.mapCraftingIngredients(recipe);
        List<ItemStack> previewStacks = new ArrayList<>(9);
        for (Ingredient ingredient : mapped) {
            if (ingredient == null || ingredient.isEmpty()) {
                previewStacks.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack[] options = RtsCraftingUtils.ingredientOptions(ingredient);
            if (options.length <= 0 || options[0].isEmpty()) {
                return ItemStack.EMPTY;
            }
            previewStacks.add(options[0].copyWithCount(1));
        }
        ItemStack assembled = recipe.assemble(CraftingInput.of(3, 3, previewStacks));
        return assembled.isEmpty() ? ItemStack.EMPTY : assembled.copy();
    }

    private static boolean matchesCraftablesSearch(
            Identifier resultId, String resultLabel, String search,
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
