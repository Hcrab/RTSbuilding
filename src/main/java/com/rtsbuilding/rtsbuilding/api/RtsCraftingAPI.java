package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 合成终端 API。
 *
 * <p>管理远程合成终端的配方查询、合成执行和网格填充。
 */
public interface RtsCraftingAPI {

    /**
     * 打开合成终端。
     *
     * @param player 目标玩家
     */
    void openCraftTerminal(ServerPlayer player);

    /**
     * 请求可合成物品列表。
     */
    void requestCraftables(ServerPlayer player, String search, boolean showUnavailable,
                           int offset, int limit);

    /**
     * 将配方合成为物品并存入链接存储。
     *
     * @param player     执行玩家
     * @param recipeId   配方 ID
     * @param craftCount 合成次数
     */
    void craftRecipeToLinked(ServerPlayer player, String recipeId, int craftCount);

    /**
     * 从链接存储填充合成网格。
     *
     * @param player       执行玩家
     * @param blueprintIds 蓝图物品 ID 列表（9个槽位）
     * @param craftedItemId 合成输出的物品 ID
     * @param craftedCount  合成数量
     */
    void refillGridFromIds(ServerPlayer player, List<String> blueprintIds,
                           String craftedItemId, int craftedCount);

    /**
     * 使用精确原型栈填充合成网格。
     */
    void refillGridFromStacks(ServerPlayer player, List<ItemStack> blueprintStacks,
                              String craftedItemId, int craftedCount);

    /**
     * 应用 JEI 配方传输。
     */
    void applyJeiTransfer(ServerPlayer player, String recipeId,
                          List<ItemStack> ingredientPrototypes,
                          boolean maxTransfer, boolean clearGridFirst);
}
