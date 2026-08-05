package com.rtsbuilding.rtsbuilding.server.service.crafting;

import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import com.rtsbuilding.rtsbuilding.platform.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import com.rtsbuilding.rtsbuilding.platform.crafting.LegacyRecipeCompat;
import com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 1.12.2 合成服务的版本适配与精确物品栈工具。 */
final class RtsCraftingUtils {
    private RtsCraftingUtils() {}

    static Ingredient[] mapCraftingIngredients(IRecipe recipe) {
        Ingredient[] mapped = new Ingredient[9];
        java.util.Arrays.fill(mapped, Ingredient.EMPTY);
        LegacyRecipeCompat.Description description = LegacyRecipeCompat.describe(recipe);
        if (description.isEmpty()) return mapped;
        List<Ingredient> ingredients = description.ingredients();
        if (description.shaped()) {
            int width = Math.max(1, Math.min(3, description.width()));
            int height = Math.max(1, Math.min(3, description.height()));
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int source = y * width + x;
                    if (source < ingredients.size()) mapped[y * 3 + x] = ingredients.get(source);
                }
            }
        } else {
            for (int i = 0; i < Math.min(9, ingredients.size()); i++) mapped[i] = ingredients.get(i);
        }
        return mapped;
    }

    static boolean isIngredientEmpty(Ingredient ingredient) {
        return ingredient == null || ingredient == Ingredient.EMPTY || ingredient.getMatchingStacks().length == 0;
    }

    static ItemStack one(ItemStack stack) {
        if (stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) return null;
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }

    static InventoryCrafting newCraftingGrid() {
        return new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer player) { return false; }
        }, 3, 3);
    }

    /** item、metadata 与完整 NBT 均一致才视为同一原型。 */
    static boolean sameStack(ItemStack left, ItemStack right) {
        return left != null && right != null
                && com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.areItemsEqual(left, right)
                && ItemStack.areItemStackTagsEqual(left, right);
    }

    static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }

    static void mergeConsumedCounts(Map<String, Integer> into, Map<String, Integer> added) {
        if (into == null || added == null) return;
        for (Map.Entry<String, Integer> entry : added.entrySet()) {
            if (isBlank(entry.getKey())) continue;
            int delta = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            if (delta > 0) into.put(entry.getKey(), into.containsKey(entry.getKey()) ? into.get(entry.getKey()) + delta : delta);
        }
    }

    static Map<String, Integer> collectConsumedCounts(ExtractedIngredient[] extracted) {
        Map<String, Integer> consumed = new LinkedHashMap<String, Integer>();
        if (extracted == null) return consumed;
        for (ExtractedIngredient ingredient : extracted) {
            if (ingredient == null || ingredient.stack() == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(ingredient.stack())) continue;
            ResourceLocation id = RtsRegistries.ITEMS.getKey(ingredient.stack().getItem());
            if (id == null) continue;
            String key = id.toString();
            int count = Math.max(1, ingredient.stack().stackSize);
            consumed.put(key, consumed.containsKey(key) ? consumed.get(key) + count : count);
        }
        return consumed;
    }

    static String resolveIngredientLabel(Ingredient ingredient) {
        if (isIngredientEmpty(ingredient)) return "Ingredient";
        for (ItemStack option : ingredient.getMatchingStacks()) {
            if (option != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(option)) return option.getDisplayName();
        }
        return "Ingredient";
    }

    static String buildMissingSummary(Map<String, Integer> missing) {
        if (missing == null || missing.isEmpty()) return "";
        StringBuilder summary = new StringBuilder("Missing: ");
        int index = 0;
        for (Map.Entry<String, Integer> entry : missing.entrySet()) {
            if (index > 0) summary.append(", ");
            summary.append(entry.getKey()).append(" x").append(entry.getValue());
            index++;
            if (index >= 3 && missing.size() > index) { summary.append("..."); break; }
        }
        return summary.toString();
    }

    static String buildRecipeSummary(IRecipe recipe) {
        if (recipe == null) return "Recipe";
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (Ingredient ingredient : mapCraftingIngredients(recipe)) {
            if (isIngredientEmpty(ingredient)) continue;
            String label = resolveIngredientLabel(ingredient);
            counts.put(label, counts.containsKey(label) ? counts.get(label) + 1 : 1);
        }
        if (counts.isEmpty()) return "Recipe";
        StringBuilder summary = new StringBuilder();
        int index = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (index > 0) summary.append(", ");
            summary.append(entry.getKey());
            if (entry.getValue() > 1) summary.append(" x").append(entry.getValue());
            index++;
            if (index >= 3 && counts.size() > index) { summary.append("..."); break; }
        }
        return summary.length() == 0 ? "Recipe" : summary.toString();
    }

    static void mergeAvailableCraftItem(List<AvailableCraftItem> entries, ItemStack stack, long count) {
        if (entries == null || stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) || count <= 0L) return;
        ItemStack prototype = one(stack);
        for (int i = 0; i < entries.size(); i++) {
            AvailableCraftItem existing = entries.get(i);
            if (!sameStack(existing.prototype(), prototype)) continue;
            entries.set(i, new AvailableCraftItem(existing.prototype(), saturatedAdd(existing.count(), count)));
            return;
        }
        entries.add(new AvailableCraftItem(prototype, count));
    }

    static long saturatedAdd(long left, long right) {
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    static List<AvailableCraftItem> copyAvailableCraftItems(List<AvailableCraftItem> source) {
        List<AvailableCraftItem> copy = new ArrayList<AvailableCraftItem>();
        if (source == null) return copy;
        for (AvailableCraftItem item : source) {
            if (item != null && item.prototype() != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(item.prototype()) && item.count() > 0L) {
                copy.add(new AvailableCraftItem(one(item.prototype()), item.count()));
            }
        }
        return copy;
    }

    static void refreshCraftingResult(ContainerWorkbench menu) {
        if (menu != null) {
            menu.onCraftMatrixChanged(menu.craftMatrix);
            menu.detectAndSendChanges();
        }
    }
}
