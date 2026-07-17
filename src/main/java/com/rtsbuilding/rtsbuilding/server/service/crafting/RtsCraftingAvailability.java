package com.rtsbuilding.rtsbuilding.server.service.crafting;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合成材料可用性评估器。
 *
 * <p>负责判断给定合成配方是否能从当前可用的物品栈集合中凑齐材料，
 * 并采用回溯算法（backtracking）为每个材料槽位分配合适的物品。
 * 当材料不足以合成时，自动估算缺失物品的种类和数量，生成人类可读的缺失摘要。
 *
 * <p>核心能力：
 * <ul>
 *   <li>{@link #evaluateRecipeAvailability} — 返回配方是否可合成的判定结果</li>
 *   <li>{@link #resolveCraftIngredientPlan} — 执行回溯分配，生成槽位材料计划</li>
 *   <li>{@link #snapshotAvailable} — 从链接存储处理器和玩家背包中快照可用物品</li>
 *   <li>{@link #estimateMissingIngredients} — 在材料不足时估算缺失信息</li>
 * </ul>
 *
 * <p>此类的所有方法均为静态工具方法，不持有状态。
 */
final class RtsCraftingAvailability {

    private RtsCraftingAvailability() {
    }

    /**
     * 返回一个 {@link RecipeAvailability}，指示是否可用给定的可用物品栈合成配方，
     * 如果不能，则返回缺失的内容。
     */
    static RecipeAvailability evaluateRecipeAvailability(CraftingRecipe recipe, List<AvailableCraftItem> availableStacks) {
        CraftIngredientPlan plan = resolveCraftIngredientPlan(recipe, availableStacks);
        if (plan != null) {
            return new RecipeAvailability(true, "", 0);
        }
        return estimateMissingIngredients(recipe, availableStacks);
    }

    /**
     * 尝试从可用物品栈中为配方找到有效的材料分配。
     * 如果成功返回计划，如果材料不足则返回 {@code null}。
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
     * 从给定的处理器和可选的玩家背包中快照可用物品。
     */
    static List<AvailableCraftItem> snapshotAvailable(
            ServerPlayer player, java.util.List<RtsItemStorage> handlers,
            boolean includePlayerMainInventory) {
        java.util.List<AvailableCraftItem> entries = new ArrayList<>();
        if (handlers == null) {
            handlers = java.util.List.of();
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
