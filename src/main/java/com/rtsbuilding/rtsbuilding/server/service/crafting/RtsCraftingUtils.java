package com.rtsbuilding.rtsbuilding.server.service.crafting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
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
 * craft 子包共享工具方法集合。
 *
 * <p>提供在合成流程中多处复用的静态辅助方法，涵盖：
 * <ul>
 *   <li><b>配方材料映射</b>（{@link #mapCraftingIngredients}）— 将任意 {@link CraftingRecipe}
 *   的 Ingredient 列表平铺为固定的 9 槽位数组（有序配方按 3x3 布局定位）</li>
 *   <li><b>容器反射</b>（{@link #resolveCraftingContainer}）— 通过反射从 {@link net.minecraft.world.inventory.CraftingMenu}
 *   中获取 {@link net.minecraft.world.inventory.CraftingContainer}，用于触发结果更新</li>
 *   <li><b>物品合并与统计</b>— 可用物品列表合并（{@link #mergeAvailableCraftItem}）、
 *   消耗计数收集与合并（{@link #collectConsumedCounts} / {@link #mergeConsumedCounts}）</li>
 *   <li><b>人类可读摘要</b>— 缺少材料摘要（{@link #buildMissingSummary}）、
 *   配方材料摘要（{@link #buildRecipeSummary}）、材料名称解析（{@link #resolveIngredientLabel}）</li>
 * </ul>
 */
final class RtsCraftingUtils {

    private RtsCraftingUtils() {
    }

    /**
     * 将合成配方的材料映射为扁平的 9 槽 Ingredient 数组。
     * 有形状的配方定位为 3x3 网格；无序配方按顺序填满槽位。
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
     * 尝试通过反射从 {@link CraftingMenu} 解析 {@link CraftingContainer}。
     * 如果字段不可访问，则回退返回 {@code null}。
     */
    static CraftingContainer resolveCraftingContainer(AbstractContainerMenu menu) {
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
     * 将逐槽消耗计数映射合并到累积映射中。
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
     * 从已提取的材料构建消耗计数映射。
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
     * 返回材料中第一个非空物品的显示名称。
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
     * 构建人类可读的"缺少：item xN, ..."摘要（最多 3 种缺少的物品）。
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
     * 为配方选择面板构建紧凑的材料摘要（最多 3 种材料）。
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
     * 将物品栈合并到可用物品列表中，聚合相同物品的条目。
     */
    static void mergeAvailableCraftItem(List<AvailableCraftItem> entries, ItemStack stack, long count) {
        if (entries == null || stack == null || stack.isEmpty() || count <= 0L) {
            return;
        }
        ItemStack prototype = stack.copyWithCount(1);
        for (int i = 0; i < entries.size(); i++) {
            AvailableCraftItem existing = entries.get(i);
            if (!ItemStack.isSameItemSameComponents(existing.prototype(), prototype)) {
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
     * 刷新打开菜单中的合成结果槽。
     */
    static void refreshCraftingResult(AbstractContainerMenu menu) {
        if (menu == null) {
            return;
        }
        CraftingContainer craftSlots = resolveCraftingContainer(menu);
        if (craftSlots != null) {
            menu.slotsChanged(craftSlots);
        }
    }
}
