package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.CraftingService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageCrafting;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

public final class RtsCraftingServiceImpl implements CraftingService {

    private final ServiceRegistry registry = ServiceRegistry.getInstance();

    @Override
    public void openCraftTerminal(ServerPlayer player) {
        RtsStorageCrafting.openCraftTerminal(player, registry.session().getIfPresent(player));
    }

    @Override
    public void requestCraftables(ServerPlayer player, String search, boolean showUnavailable,
                                  int offset, int limit, boolean pinyinSearchEnabled,
                                  List<String> localizedSearchMatches) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        RtsStorageCrafting.requestCraftables(
                player,
                registry.session().getOrCreate(player),
                search,
                showUnavailable,
                offset,
                limit,
                pinyinSearchEnabled,
                localizedSearchMatches);
    }

    @Override
    public void requestCraftables(ServerPlayer player, String search, boolean showUnavailable,
                                  int offset, int limit, boolean pinyinSearchEnabled) {
        requestCraftables(player, search, showUnavailable, offset, limit, pinyinSearchEnabled, currentCraftLocalizedSearchMatches(player));
    }

    @Override
    public void requestCraftables(ServerPlayer player, String search, boolean showUnavailable,
                                  int offset, int limit) {
        requestCraftables(player, search, showUnavailable, offset, limit, currentCraftPinyinSearchEnabled(player));
    }

    @Override
    public void craftRecipeToLinked(ServerPlayer player, String recipeId, int craftCount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        RtsStorageCrafting.craftRecipeToLinked(player, registry.session().getOrCreate(player), recipeId, craftCount);
    }

    @Override
    public void refillCurrentCraftGridFromBlueprintIds(ServerPlayer player, List<String> blueprintIds,
                                                       String craftedItemId, int craftedCount) {
        RtsStorageCrafting.refillCurrentCraftGridFromBlueprintIds(
                player,
                registry.session().getIfPresent(player),
                blueprintIds,
                craftedItemId,
                craftedCount);
    }

    @Override
    public void refillCurrentCraftGridFromBlueprintStacks(ServerPlayer player, List<ItemStack> blueprintStacks,
                                                          String craftedItemId, int craftedCount) {
        RtsStorageCrafting.refillCurrentCraftGridFromBlueprintStacks(
                player,
                registry.session().getIfPresent(player),
                blueprintStacks,
                craftedItemId,
                craftedCount);
    }

    @Override
    public void applyJeiTransfer(ServerPlayer player, String recipeId, List<ItemStack> ingredientPrototypes,
                                 boolean maxTransfer, boolean clearGridFirst) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.JEI_TRANSFER)) {
            return;
        }
        RtsStorageCrafting.applyJeiTransfer(
                player,
                registry.session().getOrCreate(player),
                recipeId,
                ingredientPrototypes,
                maxTransfer,
                clearGridFirst);
    }

    @Override
    public ItemStack[] snapshotCraftGridBlueprint(CraftingMenu menu) {
        return RtsStorageCrafting.snapshotCraftGridBlueprint(menu);
    }

    @Override
    public void refillCraftGridFromBlueprint(CraftingMenu menu, List<IItemHandler> handlers, ServerPlayer player,
                                             ItemStack[] blueprint, boolean fillAll, boolean includePlayerFallback) {
        RtsStorageCrafting.refillCraftGridFromBlueprint(menu, handlers, player, blueprint, fillAll, includePlayerFallback);
    }

    @Override
    public void refillCraftGridFromLinked(ServerPlayer player, CraftingMenu craftingMenu,
                                          ItemStack[] blueprint, CraftingRecipe recipe) {
        RtsStorageCrafting.refillCraftGridFromLinked(player, registry.session().getIfPresent(player), craftingMenu, blueprint, recipe);
    }

    @Override
    public void recordCraftedOutput(ServerPlayer player, ItemStack crafted) {
        RtsStorageCrafting.recordCraftedOutput(player, registry.session().getIfPresent(player), crafted);
    }

    // ────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ────────────────────────────────────────────────────────────────

    private boolean currentCraftPinyinSearchEnabled(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : registry.session().getIfPresent(player);
        return session != null && session.browser.craftPinyinSearchEnabled;
    }

    private List<String> currentCraftLocalizedSearchMatches(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : registry.session().getIfPresent(player);
        return session == null ? List.of() : List.copyOf(session.browser.craftLocalizedSearchMatches);
    }
}
