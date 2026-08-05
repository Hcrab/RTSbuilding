package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.server.service.crafting.RtsCraftingExecutor;
import com.rtsbuilding.rtsbuilding.server.service.crafting.RtsCraftingGridFiller;
import com.rtsbuilding.rtsbuilding.server.service.crafting.RtsCraftingSearch;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;

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

    public static void recordCraftedOutput(EntityPlayerMP player, RtsStorageSession session, ItemStack crafted) {
        if (player == null || crafted == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(crafted)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsStorageRecentEntries.recordCraftedOutput(session, crafted);
    }

    public static void openCraftTerminal(EntityPlayerMP player, RtsStorageSession session) {
        RtsCraftingExecutor.openCraftTerminal(player, session);
    }

    public static void requestCraftables(EntityPlayerMP player, RtsStorageSession session, String search,
            boolean showUnavailable, int offset, int limit,
            boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        RtsCraftingSearch.requestCraftables(player, session, search, showUnavailable,
                offset, limit, pinyinSearchEnabled, localizedSearchMatches);
    }

    public static void craftRecipeToLinked(EntityPlayerMP player, RtsStorageSession session,
            String recipeId, int craftCount) {
        RtsCraftingExecutor.craftRecipeToLinked(player, session, recipeId, craftCount);
    }

    public static void refillCraftGridFromLinked(EntityPlayerMP player, RtsStorageSession session,
            ContainerWorkbench craftingMenu, ItemStack[] blueprint) {
        RtsCraftingGridFiller.refillCraftGridFromLinked(player, session, craftingMenu, blueprint);
    }

    public static void refillCraftGridFromLinked(EntityPlayerMP player, RtsStorageSession session,
            ContainerWorkbench craftingMenu, ItemStack[] blueprint, IRecipe recipe) {
        RtsCraftingGridFiller.refillCraftGridFromLinked(player, session, craftingMenu, blueprint, recipe);
    }

    public static void refillCurrentCraftGridFromBlueprintIds(
            EntityPlayerMP player, RtsStorageSession session,
            List<String> blueprintIds, String craftedItemId, int craftedCount) {
        RtsCraftingGridFiller.refillCurrentCraftGridFromBlueprintIds(
                player, session, blueprintIds, craftedItemId, craftedCount);
    }

    public static void refillCurrentCraftGridFromBlueprintStacks(
            EntityPlayerMP player, RtsStorageSession session,
            List<ItemStack> blueprintStacks, String craftedItemId, int craftedCount) {
        RtsCraftingGridFiller.refillCurrentCraftGridFromBlueprintStacks(
                player, session, blueprintStacks, craftedItemId, craftedCount);
    }

    public static void applyJeiTransfer(
            EntityPlayerMP player, RtsStorageSession session,
            String recipeId, List<ItemStack> ingredientPrototypes,
            boolean maxTransfer, boolean clearGridFirst) {
        RtsCraftingGridFiller.applyJeiTransfer(
                player, session, recipeId, ingredientPrototypes, maxTransfer, clearGridFirst);
    }

    public static ItemStack[] snapshotCraftGridBlueprint(ContainerWorkbench menu) {
        return RtsCraftingExecutor.snapshotCraftGridBlueprint(menu);
    }

    public static void refillCraftGridFromBlueprint(
            ContainerWorkbench menu, List<IItemHandler> handlers, EntityPlayerMP player,
            ItemStack[] blueprint, boolean fillAll, boolean includePlayerFallback) {
        RtsCraftingGridFiller.refillCraftGridFromBlueprint(
                menu, handlers, player, blueprint, fillAll, includePlayerFallback);
    }
}
