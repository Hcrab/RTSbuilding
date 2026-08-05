package com.rtsbuilding.rtsbuilding.platform.crafting;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1.7.10 配方表与输入形状的唯一版本适配入口。
 *
 * <p>原版和 Forge 矿辞配方走强类型路径；GTNH 模组常见的自定义配方若公开
 * {@code getInput()}，则走受控反射回退。无法证明输入布局的配方会从远程合成面板隐藏，
 * 而不会猜测材料后吞物品。实际合成始终再调用原生 {@link IRecipe#matches}。</p>
 */
public final class LegacyRecipeCompat {
    private static final String ID_PREFIX = "rtsbuilding:legacy_recipe_";

    private LegacyRecipeCompat() {
    }

    @SuppressWarnings("unchecked")
    public static List<IRecipe> recipes() {
        return (List<IRecipe>) (List<?>) CraftingManager.getInstance().getRecipeList();
    }

    public static String id(IRecipe recipe) {
        int index = recipes().indexOf(recipe);
        return index < 0 ? "" : ID_PREFIX + index;
    }

    public static IRecipe byId(String id) {
        if (id == null || !id.startsWith(ID_PREFIX)) return null;
        try {
            int index = Integer.parseInt(id.substring(ID_PREFIX.length()));
            List<IRecipe> recipes = recipes();
            return index < 0 || index >= recipes.size() ? null : recipes.get(index);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Description describe(IRecipe recipe) {
        if (recipe instanceof ShapedRecipes) {
            ShapedRecipes shaped = (ShapedRecipes) recipe;
            return description(shaped.recipeItems, shaped.recipeWidth, shaped.recipeHeight, true);
        }
        if (recipe instanceof ShapelessRecipes) {
            ShapelessRecipes shapeless = (ShapelessRecipes) recipe;
            return description(shapeless.recipeItems, shapeless.recipeItems.size(), 1, false);
        }
        if (recipe instanceof ShapedOreRecipe) {
            ShapedOreRecipe shaped = (ShapedOreRecipe) recipe;
            return description(shaped.getInput(), readInt(shaped, "width"), readInt(shaped, "height"), true);
        }
        if (recipe instanceof ShapelessOreRecipe) {
            ShapelessOreRecipe shapeless = (ShapelessOreRecipe) recipe;
            return description(shapeless.getInput(), shapeless.getInput().size(), 1, false);
        }

        Object customInput = invokeNoArg(recipe, "getInput");
        if (customInput == null) return Description.EMPTY;
        int width = invokeInt(recipe, "getRecipeWidth");
        int height = invokeInt(recipe, "getRecipeHeight");
        boolean shaped = width > 0 && height > 0;
        int size = collectionSize(customInput);
        return description(customInput, shaped ? width : size, shaped ? height : 1, shaped);
    }

    public static List<ItemStack> remainingItems(InventoryCrafting input) {
        if (input == null) return Collections.emptyList();
        List<ItemStack> remaining = new ArrayList<ItemStack>();
        for (int slot = 0; slot < input.getSizeInventory(); slot++) {
            ItemStack stack = input.getStackInSlot(slot);
            if (stack == null || stack.getItem() == null) continue;
            if (stack.getItem().hasContainerItem(stack)) {
                ItemStack container = stack.getItem().getContainerItem(stack);
                if (container != null && container.stackSize > 0) remaining.add(container.copy());
            }
        }
        return remaining;
    }

    private static Description description(Object raw, int width, int height, boolean shaped) {
        List<Object> values = flattenTopLevel(raw);
        if (values.isEmpty() || values.size() > 9) return Description.EMPTY;
        List<Ingredient> ingredients = new ArrayList<Ingredient>(values.size());
        for (Object value : values) ingredients.add(Ingredient.fromLegacyInput(value));
        int safeWidth = shaped ? width : values.size();
        int safeHeight = shaped ? height : 1;
        if (safeWidth < 1 || safeHeight < 1 || safeWidth > 3 || safeHeight > 3
                || safeWidth * safeHeight < values.size()) return Description.EMPTY;
        return new Description(ingredients, safeWidth, safeHeight, shaped);
    }

    private static List<Object> flattenTopLevel(Object raw) {
        List<Object> values = new ArrayList<Object>();
        if (raw instanceof Object[]) Collections.addAll(values, (Object[]) raw);
        else if (raw instanceof List<?>) values.addAll((List<?>) raw);
        return values;
    }

    private static int collectionSize(Object raw) {
        if (raw instanceof Object[]) return ((Object[]) raw).length;
        if (raw instanceof List<?>) return ((List<?>) raw).size();
        return 0;
    }

    private static int readInt(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    private static int invokeInt(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static final class Description {
        private static final Description EMPTY = new Description(
                Collections.<Ingredient>emptyList(), 0, 0, false);
        private final List<Ingredient> ingredients;
        private final int width;
        private final int height;
        private final boolean shaped;

        private Description(List<Ingredient> ingredients, int width, int height, boolean shaped) {
            this.ingredients = Collections.unmodifiableList(new ArrayList<Ingredient>(ingredients));
            this.width = width;
            this.height = height;
            this.shaped = shaped;
        }

        public List<Ingredient> ingredients() { return this.ingredients; }
        public int width() { return this.width; }
        public int height() { return this.height; }
        public boolean shaped() { return this.shaped; }
        public boolean isEmpty() { return this.ingredients.isEmpty(); }
    }
}
