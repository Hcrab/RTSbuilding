package com.rtsbuilding.rtsbuilding.compat.jei;

import java.util.Optional;

import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsJeiTransferPayload;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RtsCraftTerminalJeiTransferHandler
        implements IRecipeTransferHandler<CraftingMenu, RecipeHolder<CraftingRecipe>> {
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
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public IRecipeTransferError transferRecipe(CraftingMenu container, RecipeHolder<CraftingRecipe> recipe,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (!doTransfer) {
            return null;
        }

        PacketDistributor.sendToServer(new C2SRtsJeiTransferPayload(
                recipe.id().toString(),
                maxTransfer,
                true));
        return null;
    }
}
