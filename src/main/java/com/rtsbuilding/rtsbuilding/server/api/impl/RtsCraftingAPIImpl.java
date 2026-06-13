package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.server.api.RtsCraftingAPI;
import com.rtsbuilding.rtsbuilding.server.service.RtsCraftingService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class RtsCraftingAPIImpl implements RtsCraftingAPI {
    @Override
    public void openCraftTerminal(ServerPlayer player) {
        RtsCraftingService.openCraftTerminal(player);
    }

    @Override
    public void requestCraftables(ServerPlayer player, String search, boolean showUnavailable, int offset, int limit) {
        RtsCraftingService.requestCraftables(player, search, showUnavailable, offset, limit);
    }

    @Override
    public void craftRecipeToLinked(ServerPlayer player, String recipeId, int craftCount) {
        RtsCraftingService.craftRecipeToLinked(player, recipeId, craftCount);
    }

    @Override
    public void refillGridFromIds(ServerPlayer player, List<String> blueprintIds, String craftedItemId, int craftedCount) {
        RtsCraftingService.refillCurrentCraftGridFromBlueprintIds(player, blueprintIds, craftedItemId, craftedCount);
    }

    @Override
    public void refillGridFromStacks(ServerPlayer player, List<ItemStack> blueprintStacks, String craftedItemId, int craftedCount) {
        RtsCraftingService.refillCurrentCraftGridFromBlueprintStacks(player, blueprintStacks, craftedItemId, craftedCount);
    }

    @Override
    public void applyJeiTransfer(ServerPlayer player, String recipeId, List<ItemStack> ingredientPrototypes,
                                 boolean maxTransfer, boolean clearGridFirst) {
        RtsCraftingService.applyJeiTransfer(player, recipeId, ingredientPrototypes, maxTransfer, clearGridFirst);
    }
}
