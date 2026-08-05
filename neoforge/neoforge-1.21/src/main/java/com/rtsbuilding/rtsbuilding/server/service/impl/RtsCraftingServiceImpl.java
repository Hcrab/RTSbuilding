package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.RtsService;

import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageCrafting;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * {@link RtsCraftingServiceImpl} 的默认实现——处理所有合成终端相关的服务端逻辑。
 *
 * <p>该实现类作为 {@link com.rtsbuilding.rtsbuilding.server.storage.RtsStorageCrafting}
 * 静态工具方法的代理层，将请求委托给RTS储存合成系统处理。
 * 负责管理合成终端GUI的打开、可合成物品列表的请求、
 * 配方合成到链接存储、JEI 一键传输以及合成格填充等操作。
 */
public final class RtsCraftingServiceImpl implements RtsService {

    private final RtsServer server = RtsServer.get();

    public void openCraftTerminal(ServerPlayer player) {
        RtsStorageCrafting.openCraftTerminal(player, server.session().getIfPresent(player));
    }

    public void requestCraftables(ServerPlayer player, String search, boolean showUnavailable,
                                  int offset, int limit, boolean pinyinSearchEnabled,
                                  List<String> localizedSearchMatches) {
        RtsStorageCrafting.requestCraftables(
                player,
                server.session().getOrCreate(player),
                search,
                showUnavailable,
                offset,
                limit,
                pinyinSearchEnabled,
                localizedSearchMatches);
    }

    public void requestCraftables(ServerPlayer player, String search, boolean showUnavailable,
                                  int offset, int limit, boolean pinyinSearchEnabled) {
        requestCraftables(player, search, showUnavailable, offset, limit, pinyinSearchEnabled, currentCraftLocalizedSearchMatches(player));
    }

    public void requestCraftables(ServerPlayer player, String search, boolean showUnavailable,
                                  int offset, int limit) {
        requestCraftables(player, search, showUnavailable, offset, limit, currentCraftPinyinSearchEnabled(player));
    }

    public void craftRecipeToLinked(ServerPlayer player, String recipeId, int craftCount) {
        RtsStorageCrafting.craftRecipeToLinked(player, server.session().getOrCreate(player), recipeId, craftCount);
    }

    public void refillCurrentCraftGridFromBlueprintIds(ServerPlayer player, List<String> blueprintIds,
                                                       String craftedItemId, int craftedCount) {
        RtsStorageCrafting.refillCurrentCraftGridFromBlueprintIds(
                player,
                server.session().getIfPresent(player),
                blueprintIds,
                craftedItemId,
                craftedCount);
    }

    public void refillCurrentCraftGridFromBlueprintStacks(ServerPlayer player, List<ItemStack> blueprintStacks,
                                                          String craftedItemId, int craftedCount) {
        RtsStorageCrafting.refillCurrentCraftGridFromBlueprintStacks(
                player,
                server.session().getIfPresent(player),
                blueprintStacks,
                craftedItemId,
                craftedCount);
    }

    public void applyJeiTransfer(ServerPlayer player, String recipeId, List<ItemStack> ingredientPrototypes,
                                 boolean maxTransfer, boolean clearGridFirst) {
        RtsStorageCrafting.applyJeiTransfer(
                player,
                server.session().getOrCreate(player),
                recipeId,
                ingredientPrototypes,
                maxTransfer,
                clearGridFirst);
    }

    public ItemStack[] snapshotCraftGridBlueprint(CraftingMenu menu) {
        return RtsStorageCrafting.snapshotCraftGridBlueprint(menu);
    }

    public void refillCraftGridFromBlueprint(CraftingMenu menu, List<IItemHandler> handlers, ServerPlayer player,
                                             ItemStack[] blueprint, boolean fillAll, boolean includePlayerFallback) {
        RtsStorageCrafting.refillCraftGridFromBlueprint(menu, handlers, player, blueprint, fillAll, includePlayerFallback);
    }

    public void refillCraftGridFromLinked(ServerPlayer player, CraftingMenu craftingMenu,
                                          ItemStack[] blueprint, CraftingRecipe recipe) {
        RtsStorageCrafting.refillCraftGridFromLinked(player, server.session().getIfPresent(player), craftingMenu, blueprint, recipe);
    }

    public void recordCraftedOutput(ServerPlayer player, ItemStack crafted) {
        RtsStorageCrafting.recordCraftedOutput(player, server.session().getIfPresent(player), crafted);
    }

    // ────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ────────────────────────────────────────────────────────────────

    private boolean currentCraftPinyinSearchEnabled(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : server.session().getIfPresent(player);
        return session != null && session.browser.craftPinyinSearchEnabled;
    }

    private List<String> currentCraftLocalizedSearchMatches(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : server.session().getIfPresent(player);
        return session == null ? List.of() : List.copyOf(session.browser.craftLocalizedSearchMatches);
    }
}
