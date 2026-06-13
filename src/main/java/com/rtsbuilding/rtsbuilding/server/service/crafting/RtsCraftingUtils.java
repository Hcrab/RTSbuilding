package com.rtsbuilding.rtsbuilding.server.service.crafting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared utility methods used across the crafting sub-package.
 */
final class RtsCraftingUtils {

    private RtsCraftingUtils() {
    }

    /**
     * Maps a crafting recipe's ingredients into a flat 9-slot Ingredient array.
     * Shaped recipes are positioned as a 3x3 grid; shapeless recipes fill slots sequentially.
     */
    static Ingredient[] mapCraftingIngredients(CraftingRecipe recipe) {
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
     * Attempts to reflectively resolve the {@link CraftingContainer} from a {@link CraftingMenu}.
     * Falls back to {@code null} if the field is inaccessible.
     */
    static CraftingContainer resolveCraftingContainer(CraftingMenu menu) {
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
                    // Fall back to the menu's default sync path.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    /**
     * Builds the 1.20.1 crafting input object expected by vanilla recipe APIs.
     */
    static CraftingContainer createCraftingContainer(AbstractContainerMenu owner, List<ItemStack> stacks) {
        TransientCraftingContainer input = new TransientCraftingContainer(owner, 3, 3);
        if (stacks == null) {
            return input;
        }
        for (int i = 0; i < Math.min(9, stacks.size()); i++) {
            ItemStack stack = stacks.get(i);
            input.setItem(i, stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return input;
    }

    /**
     * Merges a slot-by-slot consumed-counts map into a cumulative map.
     */
    static void mergeConsumedCounts(Map<String, Integer> into, Map<String, Integer> added) {
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

    /**
     * Builds a consumed-counts map from the extracted ingredients.
     */
    static Map<String, Integer> collectConsumedCounts(ExtractedIngredient[] extracted) {
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
     * Returns the display name of the first non-empty item in an ingredient.
     */
    static String resolveIngredientLabel(Ingredient ingredient) {
        for (ItemStack option : ingredient.getItems()) {
            if (!option.isEmpty()) {
                return option.getHoverName().getString();
            }
        }
        return "Ingredient";
    }

    /**
     * Builds a human-readable "Missing: item xN, ..." summary (max 3 missing items).
     */
    static String buildMissingSummary(Map<String, Integer> missing) {
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

    /**
     * Builds a compact ingredient summary for the recipe selection panel (max 3 ingredients).
     */
    static String buildRecipeSummary(CraftingRecipe recipe) {
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

    /**
     * Merges an item stack into the available-items list, aggregating same-item entries.
     */
    static void mergeAvailableCraftItem(List<AvailableCraftItem> entries, ItemStack stack, long count) {
        if (entries == null || stack == null || stack.isEmpty() || count <= 0L) {
            return;
        }
        ItemStack prototype = stack.copyWithCount(1);
        for (int i = 0; i < entries.size(); i++) {
            AvailableCraftItem existing = entries.get(i);
            if (!ItemStack.isSameItemSameTags(existing.prototype(), prototype)) {
                continue;
            }
            entries.set(i, new AvailableCraftItem(existing.prototype(), saturatedAdd(existing.count(), count)));
            return;
        }
        entries.add(new AvailableCraftItem(prototype, count));
    }

    static long saturatedAdd(long left, long right) {
        if (left >= Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    static List<AvailableCraftItem> copyAvailableCraftItems(List<AvailableCraftItem> source) {
        List<AvailableCraftItem> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (AvailableCraftItem item : source) {
            if (item == null || item.prototype().isEmpty() || item.count() <= 0L) {
                continue;
            }
            copy.add(new AvailableCraftItem(item.prototype(), item.count()));
        }
        return copy;
    }

    /**
     * Refreshes the crafting result slot in an open menu.
     */
    static void refreshCraftingResult(CraftingMenu menu) {
        if (menu == null) {
            return;
        }
        CraftingContainer craftSlots = resolveCraftingContainer(menu);
        if (craftSlots != null) {
            menu.slotsChanged(craftSlots);
        }
    }
}
