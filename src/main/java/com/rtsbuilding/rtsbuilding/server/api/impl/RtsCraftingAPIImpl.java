package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.RtsCraftingAPI;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * {@link RtsCraftingAPI} 的实现——委托给合成服务层。
 */
public final class RtsCraftingAPIImpl implements RtsCraftingAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

    @Override
    public void openCraftTerminal(EntityPlayerMP player) {
        REGISTRY.crafting().openCraftTerminal(player);
    }

    @Override
    public void requestCraftables(EntityPlayerMP player, String search, boolean showUnavailable, int offset, int limit) {
        REGISTRY.crafting().requestCraftables(player, search, showUnavailable, offset, limit);
    }

    @Override
    public void craftRecipeToLinked(EntityPlayerMP player, String recipeId, int craftCount) {
        REGISTRY.crafting().craftRecipeToLinked(player, recipeId, craftCount);
    }

    @Override
    public void refillGridFromIds(EntityPlayerMP player, List<String> blueprintIds, String craftedItemId, int craftedCount) {
        REGISTRY.crafting().refillCurrentCraftGridFromBlueprintIds(player, blueprintIds, craftedItemId, craftedCount);
    }

    @Override
    public void refillGridFromStacks(EntityPlayerMP player, List<ItemStack> blueprintStacks, String craftedItemId, int craftedCount) {
        REGISTRY.crafting().refillCurrentCraftGridFromBlueprintStacks(player, blueprintStacks, craftedItemId, craftedCount);
    }

    @Override
    public void applyJeiTransfer(EntityPlayerMP player, String recipeId, List<ItemStack> ingredientPrototypes,
                                 boolean maxTransfer, boolean clearGridFirst) {
        REGISTRY.crafting().applyJeiTransfer(player, recipeId, ingredientPrototypes, maxTransfer, clearGridFirst);
    }
}
