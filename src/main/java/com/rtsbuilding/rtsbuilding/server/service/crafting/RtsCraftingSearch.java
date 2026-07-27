package com.rtsbuilding.rtsbuilding.server.service.crafting;

import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsBrowserState;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 扫描 1.12 Forge 配方注册表，并按精确输出原型构建远程合成候选。 */
public final class RtsCraftingSearch {
    private RtsCraftingSearch() {}

    public static void requestCraftables(EntityPlayerMP player, RtsStorageSession session, String search,
            boolean showUnavailable, int offset, int limit,
            boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        if (player == null || session == null) return;
        session.browser.craftSearch = search == null ? "" : search.trim();
        session.browser.craftShowUnavailable = showUnavailable;
        session.browser.craftPinyinSearchEnabled = pinyinSearchEnabled;
        session.browser.craftLocalizedSearchMatches.clear();
        session.browser.craftLocalizedSearchMatches.addAll(sanitizeLocalizedSearchMatches(localizedSearchMatches));
        int batchOffset = Math.max(0, offset);
        int batchLimit = Math.max(1, limit);
        session.browser.craftRequestedCount = Math.max(RtsBrowserState.CRAFTABLE_BATCH_SIZE, batchOffset + batchLimit);
        RtsEffectAccumulator.INSTANCE.markPersistence(player.getUniqueID(), player.dimension);

        if (session.browser.craftSearch.trim().isEmpty()) {
            sendCraftables(player, session, Collections.<CraftableGroupEntry>emptyList(), 0, false, false);
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<LinkedHandler> active = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (active.isEmpty()) {
            sendCraftables(player, session, Collections.<CraftableGroupEntry>emptyList(), 0, false, false);
            return;
        }

        List<AvailableCraftItem> available = RtsCraftingAvailability.snapshotAvailable(
                player, RtsLinkedStorageResolver.itemHandlersForExtract(active),
                !session.linkedStorageInfo.isEmpty()
                        && !(player.openContainer instanceof com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu));
        Map<String, List<CraftableCandidate>> grouped = new LinkedHashMap<String, List<CraftableCandidate>>();
        for (IRecipe recipe : CraftingManager.REGISTRY) {
            if (!supportsWorkbenchCraftPanelRecipe(recipe)) continue;
            CraftableCandidate candidate = buildCraftableCandidate(player, recipe, available,
                    session.browser.craftSearch, pinyinSearchEnabled, session.browser.craftLocalizedSearchMatches);
            if (candidate == null) continue;
            String groupKey = exactOutputGroupKey(resolveCraftablePreviewResult(recipe, player));
            List<CraftableCandidate> options = grouped.get(groupKey);
            if (options == null) { options = new ArrayList<CraftableCandidate>(); grouped.put(groupKey, options); }
            options.add(candidate);
        }

        List<CraftableGroupEntry> entries = new ArrayList<CraftableGroupEntry>();
        for (List<CraftableCandidate> options : grouped.values()) {
            if (options.isEmpty()) continue;
            Collections.sort(options, new Comparator<CraftableCandidate>() {
                @Override public int compare(CraftableCandidate a, CraftableCandidate b) {
                    return CraftableCandidate.compareForRecipeSelection(a, b);
                }
            });
            boolean any = false;
            for (CraftableCandidate option : options) if (option.craftable()) { any = true; break; }
            if (!showUnavailable && !any) continue;
            entries.add(new CraftableGroupEntry(options.get(0),
                    Collections.unmodifiableList(new ArrayList<CraftableCandidate>(options))));
        }
        Collections.sort(entries, new Comparator<CraftableGroupEntry>() {
            @Override public int compare(CraftableGroupEntry a, CraftableGroupEntry b) {
                return CraftableGroupEntry.compareForPanel(a, b);
            }
        });
        int safeOffset = Math.min(entries.size(), batchOffset);
        int end = Math.min(entries.size(), safeOffset + batchLimit);
        sendCraftables(player, session, new ArrayList<CraftableGroupEntry>(entries.subList(safeOffset, end)),
                safeOffset, safeOffset > 0, end < entries.size());
    }

    public static void refreshCraftables(EntityPlayerMP player, RtsStorageSession session) {
        if (session == null) return;
        requestCraftables(player, session, session.browser.craftSearch, session.browser.craftShowUnavailable,
                0, Math.max(RtsBrowserState.CRAFTABLE_BATCH_SIZE, session.browser.craftRequestedCount),
                session.browser.craftPinyinSearchEnabled,
                new ArrayList<String>(session.browser.craftLocalizedSearchMatches));
    }

    private static Set<String> sanitizeLocalizedSearchMatches(List<String> input) {
        Set<String> sanitized = RtsStoragePageBuilder.sanitizeLocalizedSearchMatches(input);
        return sanitized == null ? Collections.<String>emptySet() : sanitized;
    }

    static void sendCraftables(EntityPlayerMP player, RtsStorageSession session,
            List<CraftableGroupEntry> groups, int offset, boolean append, boolean hasMore) {
        List<String> recipeIds = new ArrayList<String>();
        List<String> itemIds = new ArrayList<String>();
        List<Integer> counts = new ArrayList<Integer>();
        List<Boolean> craftable = new ArrayList<Boolean>();
        List<String> missing = new ArrayList<String>();
        List<Integer> optionCounts = new ArrayList<Integer>();
        List<String> optionRecipeIds = new ArrayList<String>();
        List<Integer> optionResultCounts = new ArrayList<Integer>();
        List<Boolean> optionCraftable = new ArrayList<Boolean>();
        List<String> optionSummaries = new ArrayList<String>();
        List<String> optionMissing = new ArrayList<String>();
        for (CraftableGroupEntry group : groups) {
            CraftableCandidate primary = group.primary();
            recipeIds.add(primary.recipeId()); itemIds.add(primary.resultItemId()); counts.add(primary.resultCount());
            craftable.add(primary.craftable()); missing.add(primary.missingSummary()); optionCounts.add(group.options().size());
            for (CraftableCandidate option : group.options()) {
                optionRecipeIds.add(option.recipeId()); optionResultCounts.add(option.resultCount());
                optionCraftable.add(option.craftable()); optionSummaries.add(option.recipeSummary());
                optionMissing.add(option.missingSummary());
            }
        }
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsCraftablesPayload(
                session.browser.craftSearch, session.browser.craftShowUnavailable,
                Math.max(0, offset), append, hasMore, recipeIds, itemIds, counts, craftable, missing,
                optionCounts, optionRecipeIds, optionResultCounts, optionCraftable, optionSummaries, optionMissing));
    }

    private static CraftableCandidate buildCraftableCandidate(EntityPlayerMP player, IRecipe recipe,
            List<AvailableCraftItem> available, String search, boolean pinyin, Set<String> localized) {
        if (recipe == null || recipe.getRegistryName() == null) return null;
        ItemStack result = resolveCraftablePreviewResult(recipe, player);
        if (result.isEmpty()) return null;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(result.getItem());
        if (itemId == null || !matchesCraftablesSearch(itemId, result.getDisplayName(), search, pinyin, localized)) return null;
        RecipeAvailability state = RtsCraftingAvailability.evaluateRecipeAvailability(recipe, available);
        return new CraftableCandidate(recipe.getRegistryName().toString(), itemId.toString(), Math.max(1, result.getCount()),
                result.getDisplayName(), state.craftable(), state.missingSummary(), state.missingTotal(),
                RtsCraftingUtils.buildRecipeSummary(recipe));
    }

    static boolean supportsWorkbenchCraftPanelRecipe(IRecipe recipe) {
        if (recipe == null || recipe.getIngredients() == null || recipe.getIngredients().isEmpty() || !recipe.canFit(3, 3)) return false;
        if (recipe instanceof IShapedRecipe) {
            IShapedRecipe shaped = (IShapedRecipe) recipe;
            if (shaped.getRecipeWidth() < 1 || shaped.getRecipeWidth() > 3
                    || shaped.getRecipeHeight() < 1 || shaped.getRecipeHeight() > 3) return false;
        } else if (recipe instanceof ShapelessRecipes && recipe.getIngredients().size() > 9) return false;
        else if (recipe.getIngredients().size() > 9) return false;
        for (Ingredient ingredient : RtsCraftingUtils.mapCraftingIngredients(recipe)) {
            if (!RtsCraftingUtils.isIngredientEmpty(ingredient)) return true;
        }
        return false;
    }

    static ItemStack resolveCraftablePreviewResult(IRecipe recipe, EntityPlayerMP player) {
        if (recipe == null || player == null) return ItemStack.EMPTY;
        ItemStack declared = recipe.getRecipeOutput();
        if (declared != null && !declared.isEmpty()) return declared.copy();
        InventoryCrafting grid = RtsCraftingUtils.newCraftingGrid();
        Ingredient[] mapped = RtsCraftingUtils.mapCraftingIngredients(recipe);
        for (int i = 0; i < mapped.length; i++) {
            if (RtsCraftingUtils.isIngredientEmpty(mapped[i])) continue;
            ItemStack[] choices = mapped[i].getMatchingStacks();
            if (choices.length == 0 || choices[0].isEmpty()) return ItemStack.EMPTY;
            grid.setInventorySlotContents(i, RtsCraftingUtils.one(choices[0]));
        }
        if (!recipe.matches(grid, player.world)) return ItemStack.EMPTY;
        ItemStack result = recipe.getCraftingResult(grid);
        return result == null || result.isEmpty() ? ItemStack.EMPTY : result.copy();
    }

    /** 输出分组包含 metadata/NBT；不同变体绝不能只因 item id 相同而合并。 */
    private static String exactOutputGroupKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return String.valueOf(id) + "#" + stack.getMetadata() + "#"
                + (stack.hasTagCompound() ? stack.getTagCompound().toString() : "");
    }

    private static boolean matchesCraftablesSearch(ResourceLocation resultId, String label, String search,
            boolean pinyin, Set<String> localized) {
        String query = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) return true;
        String rawId = resultId.toString().toLowerCase(Locale.ROOT);
        if (localized != null && localized.contains(rawId)) return true;
        String lowerLabel = label == null ? "" : label.toLowerCase(Locale.ROOT);
        String namespace = resultId.getNamespace().toLowerCase(Locale.ROOT);
        for (String token : query.split("\\s+")) {
            if (token == null || token.trim().isEmpty()) continue;
            if (token.startsWith("@")) {
                String mod = token.substring(1).trim();
                if (!mod.isEmpty() && !namespace.contains(mod)) return false;
            } else if (!rawId.contains(token) && !lowerLabel.contains(token)
                    && !(pinyin && RtsPinyinSearch.contains(label, token))) return false;
        }
        return true;
    }
}
