package com.rtsbuilding.rtsbuilding.compat.jei;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
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
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RtsCraftTerminalJeiTransferHandler
        implements IRecipeTransferHandler<CraftingMenu, RecipeHolder<CraftingRecipe>> {
    private static final int CRAFT_GRID_SLOT_START = 1;
    private static final int CRAFT_GRID_SLOT_COUNT = 9;
    private static final int INVENTORY_SLOT_START = 10;
    private static final int INVENTORY_SLOT_COUNT = 36;

    private final IRecipeTransferHandler<CraftingMenu, RecipeHolder<CraftingRecipe>> vanillaDelegate;

    public RtsCraftTerminalJeiTransferHandler(IRecipeTransferHandlerHelper transferHelper) {
        Objects.requireNonNull(transferHelper, "transferHelper");
        this.vanillaDelegate = transferHelper.createUnregisteredRecipeTransferHandler(
                transferHelper.createBasicRecipeTransferInfo(
                        CraftingMenu.class,
                        MenuType.CRAFTING,
                        RecipeTypes.CRAFTING,
                        CRAFT_GRID_SLOT_START,
                        CRAFT_GRID_SLOT_COUNT,
                        INVENTORY_SLOT_START,
                        INVENTORY_SLOT_COUNT));
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
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public IRecipeTransferError transferRecipe(CraftingMenu container, RecipeHolder<CraftingRecipe> recipe,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (!isRtsCraftTerminalScreen(container)) {
            return this.vanillaDelegate.transferRecipe(container, recipe, recipeSlots, player, maxTransfer, doTransfer);
        }
        if (!doTransfer) {
            return null;
        }

        RtsClientNetworkBridge.send(new C2SRtsJeiTransferPayload(
                recipe.id().toString(),
                buildIngredientPrototypes(recipe.value(), recipeSlots),
                maxTransfer,
                true));
        return null;
    }

    private static boolean isRtsCraftTerminalScreen(CraftingMenu container) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.screen instanceof RtsCraftTerminalScreen screen
                && screen.getMenu() == container;
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
}
