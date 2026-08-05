package com.rtsbuilding.rtsbuilding.server.service.crafting;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Crafting material availability evaluator.
 *
 * <p>Responsible for determining whether a given crafting recipe can be assembled from currently available item stacks,
 * using a backtracking algorithm to assign appropriate items to each ingredient slot.
 * When materials are insufficient, automatically estimates the type and quantity of missing items, generating a human-readable missing summary.
 *
 * <p>Core capabilities:
 * <ul>
 *   <li>{@link #evaluateRecipeAvailability} — Returns whether the recipe is craftable</li>
 *   <li>{@link #resolveCraftIngredientPlan} — Executes backtracking assignment, generating slot material plan</li>
 *   <li>{@link #snapshotAvailable} — Snapshots available items from linked storage handlers and player inventory</li>
 *   <li>{@link #estimateMissingIngredients} — Estimates missing information when materials are insufficient</li>
 * </ul>
 *
 * <p>All methods in this class are static utility methods, holding no state.
 */
final class RtsCraftingAvailability {

    private RtsCraftingAvailability() {
    }

    /**
     * Returns a {@link RecipeAvailability} indicating whether the recipe can be crafted with the given available item stacks,
     * or returns the missing content if not.
     */
    static RecipeAvailability evaluateRecipeAvailability(CraftingRecipe recipe, List<AvailableCraftItem> availableStacks) {
        CraftIngredientPlan plan = resolveCraftIngredientPlan(recipe, availableStacks);
        if (plan != null) {
            return new RecipeAvailability(true, "", 0);
        }
        return estimateMissingIngredients(recipe, availableStacks);
    }

    /**
     * Attempts to find a valid material assignment for the recipe from available item stacks.
     * Returns the plan on success, or {@code null} if materials are insufficient.
     */
    static CraftIngredientPlan resolveCraftIngredientPlan(CraftingRecipe recipe, List<AvailableCraftItem> availableStacks) {
        Ingredient[] required = RtsCraftingUtils.mapCraftingIngredients(recipe);
        List<AvailableCraftItem> remaining = RtsCraftingUtils.copyAvailableCraftItems(availableStacks);
        ItemStack[] planned = new ItemStack[9];
        for (int i = 0; i < planned.length; i++) {
            planned[i] = ItemStack.EMPTY;
        }
        List<Integer> requiredSlots = new ArrayList<>();
        for (int i = 0; i < required.length; i++) {
            Ingredient ingredient = required[i];
            if (ingredient != null && !ingredient.isEmpty()) {
                requiredSlots.add(i);
            }
        }
        requiredSlots.sort((left, right) -> Integer.compare(
                countAvailableMatches(required[left], remaining),
                countAvailableMatches(required[right], remaining)));
        return assignCraftIngredients(required, requiredSlots, remaining, planned, 0)
                ? new CraftIngredientPlan(planned)
                : null;
    }

    /**
     * Snapshots available items from the given handlers and optionally the player inventory.
     */
    static List<AvailableCraftItem> snapshotAvailable(ServerPlayer player, java.util.List<IItemHandler> handlers,
            boolean includePlayerMainInventory) {
        java.util.List<AvailableCraftItem> entries = new ArrayList<>();
        if (handlers == null) {
            handlers = java.util.List.of();
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
                        com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder.getHandlerReportedCount(handler, i, stack));
            }
        }
        if (includePlayerMainInventory && player != null) {
            int start = com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
            int end = com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
            for (int slot = start; slot < end; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty()) {
                    RtsCraftingUtils.mergeAvailableCraftItem(entries, stack, stack.getCount());
                }
            }
        }
        return entries;
    }

    // ---- backtracking assignment --------------------------------------------------

    private static boolean assignCraftIngredients(
            Ingredient[] required, List<Integer> requiredSlots,
            List<AvailableCraftItem> remaining, ItemStack[] planned, int depth) {
        if (depth >= requiredSlots.size()) {
            return true;
        }
        int slot = requiredSlots.get(depth);
        Ingredient ingredient = required[slot];
        List<Integer> candidateIndexes = matchingAvailableIndexes(ingredient, remaining);
        candidateIndexes.sort((left, right) -> Long.compare(remaining.get(right).count(), remaining.get(left).count()));
        for (int index : candidateIndexes) {
            AvailableCraftItem candidate = remaining.get(index);
            if (candidate.count() <= 0L) {
                continue;
            }
            planned[slot] = candidate.prototype().copyWithCount(1);
            remaining.set(index, new AvailableCraftItem(candidate.prototype(), candidate.count() - 1L));
            if (assignCraftIngredients(required, requiredSlots, remaining, planned, depth + 1)) {
                return true;
            }
            remaining.set(index, candidate);
            planned[slot] = ItemStack.EMPTY;
        }
        return false;
    }

    private static List<Integer> matchingAvailableIndexes(Ingredient ingredient, List<AvailableCraftItem> remaining) {
        List<Integer> indexes = new ArrayList<>();
        if (ingredient == null || ingredient.isEmpty() || remaining == null) {
            return indexes;
        }
        for (int i = 0; i < remaining.size(); i++) {
            AvailableCraftItem item = remaining.get(i);
            if (item == null || item.count() <= 0L || item.prototype().isEmpty()) {
                continue;
            }
            if (ingredient.test(item.prototype())) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private static int countAvailableMatches(Ingredient ingredient, List<AvailableCraftItem> remaining) {
        int matches = 0;
        if (ingredient == null || ingredient.isEmpty() || remaining == null) {
            return matches;
        }
        for (AvailableCraftItem item : remaining) {
            if (item != null && item.count() > 0L && !item.prototype().isEmpty() && ingredient.test(item.prototype())) {
                matches++;
            }
        }
        return matches;
    }

    // ---- missing-ingredient estimation --------------------------------------------

    private static RecipeAvailability estimateMissingIngredients(CraftingRecipe recipe, List<AvailableCraftItem> availableStacks) {
        Ingredient[] required = RtsCraftingUtils.mapCraftingIngredients(recipe);
        List<AvailableCraftItem> remaining = RtsCraftingUtils.copyAvailableCraftItems(availableStacks);
        List<Integer> requiredSlots = new ArrayList<>();
        for (int i = 0; i < required.length; i++) {
            Ingredient ingredient = required[i];
            if (ingredient != null && !ingredient.isEmpty()) {
                requiredSlots.add(i);
            }
        }
        requiredSlots.sort((left, right) -> Integer.compare(
                countAvailableMatches(required[left], remaining),
                countAvailableMatches(required[right], remaining)));
        Map<String, Integer> missing = new LinkedHashMap<>();
        int missingTotal = 0;
        for (int slot : requiredSlots) {
            Ingredient ingredient = required[slot];
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            List<Integer> matches = matchingAvailableIndexes(ingredient, remaining);
            if (!matches.isEmpty()) {
                int best = matches.get(0);
                for (int index : matches) {
                    if (remaining.get(index).count() > remaining.get(best).count()) {
                        best = index;
                    }
                }
                AvailableCraftItem selected = remaining.get(best);
                remaining.set(best, new AvailableCraftItem(selected.prototype(), selected.count() - 1L));
                continue;
            }
            missing.merge(RtsCraftingUtils.resolveIngredientLabel(ingredient), 1, Integer::sum);
            missingTotal++;
        }
        return new RecipeAvailability(false, RtsCraftingUtils.buildMissingSummary(missing), missingTotal);
    }
}
