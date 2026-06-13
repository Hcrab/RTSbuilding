package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.server.service.crafting.RtsCraftingExecutor;
import com.rtsbuilding.rtsbuilding.server.service.crafting.RtsCraftingGridFiller;
import com.rtsbuilding.rtsbuilding.server.service.crafting.RtsCraftingSearch;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * Facade for RTS crafting operations.
 *
 * <p>All methods delegate to the appropriate sub-module in the
 * {@code crafting} package.  This class exists solely to preserve the
 * existing service and network call sites without importing crafting internals.
 *
 * <p>The actual implementation lives in:
 * <ul>
 *   <li>{@link RtsCraftingSearch}  ??craftable-panel search &amp; recipe scanning</li>
 *   <li>{@link RtsCraftingExecutor} ??craft execution &amp; terminal opening</li>
 *   <li>{@link RtsCraftingGridFiller} ??grid refill &amp; JEI transfer</li>
 * </ul>
 *
 * @see RtsCraftingSearch
 * @see RtsCraftingExecutor
 * @see RtsCraftingGridFiller
 */
public final class RtsStorageCrafting {
    private RtsStorageCrafting() {
    }

    public static void recordCraftedOutput(ServerPlayer player, RtsStorageSession session, ItemStack crafted) {
        if (player == null || crafted == null || crafted.isEmpty()) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsStorageRecentEntries.recordCraftedOutput(session, crafted);
    }

    public static void openCraftTerminal(ServerPlayer player, RtsStorageSession session) {
        RtsCraftingExecutor.openCraftTerminal(player, session);
    }

    public static void requestCraftables(ServerPlayer player, RtsStorageSession session, String search,
            boolean showUnavailable, int offset, int limit,
            boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        RtsCraftingSearch.requestCraftables(player, session, search, showUnavailable,
                offset, limit, pinyinSearchEnabled, localizedSearchMatches);
    }

    public static void craftRecipeToLinked(ServerPlayer player, RtsStorageSession session,
            String recipeId, int craftCount) {
        RtsCraftingExecutor.craftRecipeToLinked(player, session, recipeId, craftCount);
    }

    public static void refillCraftGridFromLinked(ServerPlayer player, RtsStorageSession session,
            CraftingMenu craftingMenu, ItemStack[] blueprint) {
        RtsCraftingGridFiller.refillCraftGridFromLinked(player, session, craftingMenu, blueprint);
    }

    public static void refillCraftGridFromLinked(ServerPlayer player, RtsStorageSession session,
            CraftingMenu craftingMenu, ItemStack[] blueprint, CraftingRecipe recipe) {
        RtsCraftingGridFiller.refillCraftGridFromLinked(player, session, craftingMenu, blueprint, recipe);
    }

    public static void refillCurrentCraftGridFromBlueprintIds(
            ServerPlayer player, RtsStorageSession session,
            List<String> blueprintIds, String craftedItemId, int craftedCount) {
        RtsCraftingGridFiller.refillCurrentCraftGridFromBlueprintIds(
                player, session, blueprintIds, craftedItemId, craftedCount);
    }

    public static void refillCurrentCraftGridFromBlueprintStacks(
            ServerPlayer player, RtsStorageSession session,
            List<ItemStack> blueprintStacks, String craftedItemId, int craftedCount) {
        RtsCraftingGridFiller.refillCurrentCraftGridFromBlueprintStacks(
                player, session, blueprintStacks, craftedItemId, craftedCount);
    }

    public static void applyJeiTransfer(
            ServerPlayer player, RtsStorageSession session,
            String recipeId, List<ItemStack> ingredientPrototypes,
            boolean maxTransfer, boolean clearGridFirst) {
        RtsCraftingGridFiller.applyJeiTransfer(
                player, session, recipeId, ingredientPrototypes, maxTransfer, clearGridFirst);
    }

    public static ItemStack[] snapshotCraftGridBlueprint(CraftingMenu menu) {
        return RtsCraftingExecutor.snapshotCraftGridBlueprint(menu);
    }

    public static void refillCraftGridFromBlueprint(
            CraftingMenu menu, List<IItemHandler> handlers, ServerPlayer player,
            ItemStack[] blueprint, boolean fillAll, boolean includePlayerFallback) {
        RtsCraftingGridFiller.refillCraftGridFromBlueprint(
                menu, handlers, player, blueprint, fillAll, includePlayerFallback);
    }
}
