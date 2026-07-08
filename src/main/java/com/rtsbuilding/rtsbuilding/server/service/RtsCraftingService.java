package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageCrafting;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * 合成服务——管理合成终端、配方请求、JEI 传输和合成格填充??
 *
 * <p>职责范围??
 * <ul>
 *   <li>打开/关闭合成终端</li>
 *   <li>请求可合成物品列??/li>
 *   <li>执行配方合成到链接存??/li>
 *   <li>JEI 一键传??/li>
 *   <li>合成格快照与填充</li>
 *   <li>记录合成输出</li>
 * </ul>
 */
public final class RtsCraftingService {

    public static final RtsCraftingService INSTANCE = new RtsCraftingService();

    private RtsCraftingService() {
    }

    /**
     * 打开合成终端??
     */
    public static void openCraftTerminal(ServerPlayer player) {
        RtsStorageCrafting.openCraftTerminal(player, RtsSessionService.getIfPresent(player));
    }

    /**
     * 请求可合成物品列表（带拼音搜索支持）??
     */
    public static void requestCraftables(ServerPlayer player, String search, boolean showUnavailable, int offset, int limit,
            boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        RtsStorageCrafting.requestCraftables(
                player,
                RtsSessionService.getOrCreate(player),
                search,
                showUnavailable,
                offset,
                limit,
                pinyinSearchEnabled,
                localizedSearchMatches);
    }

    /**
     * 请求可合成物品列???
     */
    public static void requestCraftables(ServerPlayer player, String search, boolean showUnavailable, int offset, int limit,
            boolean pinyinSearchEnabled) {
        requestCraftables(player, search, showUnavailable, offset, limit, pinyinSearchEnabled, currentCraftLocalizedSearchMatches(player));
    }

    /**
     * 请求可合成物品列???
     */
    public static void requestCraftables(ServerPlayer player, String search, boolean showUnavailable, int offset, int limit) {
        requestCraftables(player, search, showUnavailable, offset, limit, currentCraftPinyinSearchEnabled(player));
    }

    /**
     * 将配方合成到链接存储??
     */
    public static void craftRecipeToLinked(ServerPlayer player, String recipeId, int craftCount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        RtsStorageCrafting.craftRecipeToLinked(player, RtsSessionService.getOrCreate(player), recipeId, craftCount);
    }

    /**
     * 按物??ID 填充合成???
     */
    public static void refillCurrentCraftGridFromBlueprintIds(ServerPlayer player, List<String> blueprintIds,
            String craftedItemId, int craftedCount) {
        RtsStorageCrafting.refillCurrentCraftGridFromBlueprintIds(
                player,
                RtsSessionService.getIfPresent(player),
                blueprintIds,
                craftedItemId,
                craftedCount);
    }

    /**
     * 按物品栈填充合成???
     */
    public static void refillCurrentCraftGridFromBlueprintStacks(ServerPlayer player, List<ItemStack> blueprintStacks,
            String craftedItemId, int craftedCount) {
        RtsStorageCrafting.refillCurrentCraftGridFromBlueprintStacks(
                player,
                RtsSessionService.getIfPresent(player),
                blueprintStacks,
                craftedItemId,
                craftedCount);
    }

    /**
     * JEI 一键传输——填充合成格并执行合???
     */
    public static void applyJeiTransfer(ServerPlayer player, String recipeId, List<ItemStack> ingredientPrototypes,
            boolean maxTransfer, boolean clearGridFirst) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.JEI_TRANSFER)) {
            return;
        }
        RtsStorageCrafting.applyJeiTransfer(
                player,
                RtsSessionService.getOrCreate(player),
                recipeId,
                ingredientPrototypes,
                maxTransfer,
                clearGridFirst);
    }

    /**
     * 快照当前合成格的配方蓝图??
     */
    public static ItemStack[] snapshotCraftGridBlueprint(CraftingMenu menu) {
        return RtsStorageCrafting.snapshotCraftGridBlueprint(menu);
    }

    /**
     * 从蓝图填充合成格??
     */
    public static void refillCraftGridFromBlueprint(CraftingMenu menu, List<IItemHandler> handlers, ServerPlayer player,
            ItemStack[] blueprint, boolean fillAll, boolean includePlayerFallback) {
        RtsStorageCrafting.refillCraftGridFromBlueprint(menu, handlers, player, blueprint, fillAll, includePlayerFallback);
    }

    /**
     * 从链接存储填充合成格??
     */
    public static void refillCraftGridFromLinked(ServerPlayer player, CraftingMenu craftingMenu,
            ItemStack[] blueprint, CraftingRecipe recipe) {
        RtsStorageCrafting.refillCraftGridFromLinked(player, RtsSessionService.getIfPresent(player), craftingMenu, blueprint, recipe);
    }

    /**
     * 记录已合成的产物??
     */
    public static void recordCraftedOutput(ServerPlayer player, ItemStack crafted) {
        RtsStorageCrafting.recordCraftedOutput(player, RtsSessionService.getIfPresent(player), crafted);
    }

    // ======================================================================
    // 内部辅助
    // ======================================================================

    private static boolean currentCraftPinyinSearchEnabled(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : RtsSessionService.getIfPresent(player);
        return session != null && session.browser.craftPinyinSearchEnabled;
    }

    private static List<String> currentCraftLocalizedSearchMatches(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : RtsSessionService.getIfPresent(player);
        return session == null ? List.of() : List.copyOf(session.browser.craftLocalizedSearchMatches);
    }
}
