package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * 合成服务接口——管理合成终端、配方请求、JEI 传输和合成格填充。
 */
public interface CraftingService {

    /** 打开合成终端。 */
    void openCraftTerminal(ServerPlayer player);

    /** 请求可合成物品列表（带拼音搜索支持）。 */
    void requestCraftables(ServerPlayer player, String search, boolean showUnavailable,
                           int offset, int limit, boolean pinyinSearchEnabled,
                           List<String> localizedSearchMatches);

    /** 请求可合成物品列表。 */
    void requestCraftables(ServerPlayer player, String search, boolean showUnavailable,
                           int offset, int limit, boolean pinyinSearchEnabled);

    /** 请求可合成物品列表。 */
    void requestCraftables(ServerPlayer player, String search, boolean showUnavailable,
                           int offset, int limit);

    /** 将配方合成到链接存储。 */
    void craftRecipeToLinked(ServerPlayer player, String recipeId, int craftCount);

    /** 按物品 ID 填充合成格。 */
    void refillCurrentCraftGridFromBlueprintIds(ServerPlayer player, List<String> blueprintIds,
                                                String craftedItemId, int craftedCount);

    /** 按物品栈填充合成格。 */
    void refillCurrentCraftGridFromBlueprintStacks(ServerPlayer player, List<ItemStack> blueprintStacks,
                                                   String craftedItemId, int craftedCount);

    /** JEI 一键传输——填充合成格并执行合成。 */
    void applyJeiTransfer(ServerPlayer player, String recipeId, List<ItemStack> ingredientPrototypes,
                          boolean maxTransfer, boolean clearGridFirst);

    /** 快照当前合成格的配方蓝图。 */
    ItemStack[] snapshotCraftGridBlueprint(CraftingMenu menu);

    /** 从蓝图填充合成格。 */
    void refillCraftGridFromBlueprint(CraftingMenu menu, List<IItemHandler> handlers,
                                      ServerPlayer player, ItemStack[] blueprint,
                                      boolean fillAll, boolean includePlayerFallback);

    /** 从链接存储填充合成格。 */
    void refillCraftGridFromLinked(ServerPlayer player, CraftingMenu craftingMenu,
                                   ItemStack[] blueprint, net.minecraft.world.item.crafting.CraftingRecipe recipe);

    /** 记录已合成的产物。 */
    void recordCraftedOutput(ServerPlayer player, ItemStack crafted);
}
