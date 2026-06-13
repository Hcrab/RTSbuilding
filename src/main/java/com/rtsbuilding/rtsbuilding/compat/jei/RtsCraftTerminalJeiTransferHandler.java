package com.rtsbuilding.rtsbuilding.compat.jei;

import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsJeiTransferPayload;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.resources.ResourceLocation;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RtsCraftTerminalJeiTransferHandler
        implements IRecipeTransferHandler<CraftingMenu, CraftingRecipe> {
    public RtsCraftTerminalJeiTransferHandler(IRecipeTransferHandlerHelper transferHelper) {
        // Keep constructor signature for JEI registration helper compatibility.
    }

    @Override
    public Class<? extends CraftingMenu> getContainerClass() {
        return CraftingMenu.class;
    }

    @Override
    public Optional<MenuType<CraftingMenu>> getMenuType() {
        return Optional.of(MenuType.CRAFTING);
    }

    @Override
    public RecipeType<CraftingRecipe> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public IRecipeTransferError transferRecipe(CraftingMenu container, CraftingRecipe recipe,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (!doTransfer) {
            return null;
        }

        String recipeId = resolveRecipeId(player, recipe);
        if (recipeId.isBlank()) {
            return null;
        }

        PacketDistributor.sendToServer(new C2SRtsJeiTransferPayload(
                recipeId,
                buildIngredientPrototypes(recipe, recipeSlots),
                maxTransfer,
                true));
        return null;
    }

    private static List<ItemStack> buildIngredientPrototypes(CraftingRecipe recipe, IRecipeSlotsView recipeSlots) {
        List<ItemStack> prototypes = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            prototypes.add(ItemStack.EMPTY);
        }
        if (recipe == null || recipeSlots == null) {
            return prototypes;
        }

        List<IRecipeSlotView> inputViews = recipeSlots.getSlotViews().stream()
                .filter(view -> view.getRole() == RecipeIngredientRole.INPUT || view.getRole() == RecipeIngredientRole.CATALYST)
                .toList();
        Ingredient[] mapped = mapCraftingIngredients(recipe);
        int viewIndex = 0;
        for (int slot = 0; slot < mapped.length && viewIndex < inputViews.size(); slot++) {
            Ingredient ingredient = mapped[slot];
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            ItemStack chosen = choosePrototype(inputViews.get(viewIndex), ingredient);
            prototypes.set(slot, chosen.isEmpty() ? ItemStack.EMPTY : chosen.copyWithCount(1));
            viewIndex++;
        }
        return prototypes;
    }

    private static ItemStack choosePrototype(IRecipeSlotView view, Ingredient ingredient) {
        if (view == null) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> stacks = view.getIngredients(VanillaTypes.ITEM_STACK).toList();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty() && ingredient.test(stack)) {
                return stack;
            }
        }
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
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
                    if (src >= 0 && src < ingredients.size()) {
                        mapped[y * 3 + x] = ingredients.get(src);
                    }
                }
            }
            return mapped;
        }

        int count = Math.min(9, ingredients.size());
        for (int i = 0; i < count; i++) {
            mapped[i] = ingredients.get(i);
        }
        return mapped;
    }

    private static String resolveRecipeId(Player player, CraftingRecipe recipe) {
        if (player == null || player.level() == null || recipe == null) {
            return "";
        }
        for (ResourceLocation id : player.level().getRecipeManager().getRecipeIds().toList()) {
            Recipe<?> candidate = player.level().getRecipeManager().byKey(id).orElse(null);
            if (candidate == recipe) {
                return id.toString();
            }
        }
        return "";
    }
}
