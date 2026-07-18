package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.server.service.crafting.RtsCraftingExecutor;
import com.rtsbuilding.rtsbuilding.server.service.crafting.RtsCraftingGridFiller;
import com.rtsbuilding.rtsbuilding.server.service.crafting.RtsCraftingSearch;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * RTS 合成操作的外观（Facade）。
 *
 * <p>所有方法委托给 {@code crafting} 包中的相应子模块。
 * 本类仅用于保留 {@link com.rtsbuilding.rtsbuilding.server.RtsStorageManager}
 * 和网络层中现有的调用点，无需更改导入。
 *
 * <p>实际实现位于：
 * <ul>
 *   <li>{@link RtsCraftingSearch}——可合成面板搜索与配方扫描</li>
 *   <li>{@link RtsCraftingExecutor}——合成执行与终端打开</li>
 *   <li>{@link RtsCraftingGridFiller}——合成网格填充与 JEI 转移</li>
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
            AbstractContainerMenu craftingMenu, ItemStack[] blueprint) {
        RtsCraftingGridFiller.refillCraftGridFromLinked(player, session, craftingMenu, blueprint);
    }

    public static void refillCraftGridFromLinked(ServerPlayer player, RtsStorageSession session,
            AbstractContainerMenu craftingMenu, ItemStack[] blueprint, CraftingRecipe recipe) {
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

    public static void applyUniversalJeiTransfer(
            ServerPlayer player, RtsStorageSession session,
            List<ItemStack> prototypes, List<Integer> quantities,
            boolean clearGridFirst) {
        RtsCraftingGridFiller.applyUniversalJeiTransfer(
                player, session, prototypes, quantities, clearGridFirst);
    }

    public static ItemStack[] snapshotCraftGridBlueprint(AbstractContainerMenu menu) {
        return RtsCraftingExecutor.snapshotCraftGridBlueprint(menu);
    }

    public static void clearCraftingGrid(ServerPlayer player, RtsStorageSession session,
                                          boolean toPlayerInventory) {
        RtsCraftingGridFiller.clearCraftingGrid(player, session, toPlayerInventory);
    }

    public static void refillCraftGridFromBlueprint(
            AbstractContainerMenu menu, List<IItemHandler> extractHandlers,
            List<IItemHandler> insertHandlers,
            ServerPlayer player, ItemStack[] blueprint, boolean fillAll, boolean includePlayerFallback) {
        RtsCraftingGridFiller.refillCraftGridFromBlueprint(
                menu, extractHandlers, insertHandlers, player, blueprint, fillAll, includePlayerFallback);
    }
}
