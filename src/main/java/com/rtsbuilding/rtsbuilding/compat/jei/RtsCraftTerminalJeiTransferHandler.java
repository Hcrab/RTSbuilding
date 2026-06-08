package com.rtsbuilding.rtsbuilding.compat.jei;

import java.util.Optional;

import com.rtsbuilding.rtsbuilding.network.C2SRtsJeiTransferPayload;

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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.resources.ResourceLocation;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

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
                maxTransfer,
                true));
        return null;
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
