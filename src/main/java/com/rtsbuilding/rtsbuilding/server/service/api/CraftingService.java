package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * 合成服务接口——管理合成终端、配方请求、JEI 传输和合成格填充。
 *
 * <p>该接口定义了 RTS 合成终端的所有功能：
 * 打开合成终端 GUI、请求可合成物品列表（支持拼音搜索）、
 * 执行配方合成到链接存储、一键 JEI 传输填充合成格以及
 * 快照/还原合成格配方蓝图等操作。
 */
public interface CraftingService {

    /**
     * 打开 RTS 合成终端 GUI。
     * 合成终端集成了远程存储访问和自动化合成功能。
     *
     * @param player 目标玩家
     */
    void openCraftTerminal(ServerPlayer player);

    /**
     * 请求可合成物品列表（完整参数，带本地化搜索匹配）。
     * 返回匹配搜索条件的可合成物品分页列表。
     *
     * @param player              目标玩家
     * @param search              搜索关键字（支持拼音搜索）
     * @param showUnavailable     是否显示材料不足的配方
     * @param offset              分页偏移量
     * @param limit               每页条数
     * @param pinyinSearchEnabled 是否启用拼音搜索
     * @param localizedSearchMatches 本地化搜索匹配的预计算物品 ID 列表
     */
    void requestCraftables(ServerPlayer player, String search, boolean showUnavailable,
                           int offset, int limit, boolean pinyinSearchEnabled,
                           List<String> localizedSearchMatches);

    /**
     * 请求可合成物品列表（带拼音搜索设置，无本地化匹配）。
     *
     * @param player              目标玩家
     * @param search              搜索关键字
     * @param showUnavailable     是否显示材料不足的配方
     * @param offset              分页偏移量
     * @param limit               每页条数
     * @param pinyinSearchEnabled 是否启用拼音搜索
     */
    void requestCraftables(ServerPlayer player, String search, boolean showUnavailable,
                           int offset, int limit, boolean pinyinSearchEnabled);

    /**
     * 请求可合成物品列表（基础参数，不使用拼音搜索）。
     *
     * @param player          目标玩家
     * @param search          搜索关键字
     * @param showUnavailable 是否显示材料不足的配方
     * @param offset          分页偏移量
     * @param limit           每页条数
     */
    void requestCraftables(ServerPlayer player, String search, boolean showUnavailable,
                           int offset, int limit);

    /**
     * 执行配方合成，将产物自动存入链接存储。
     * 系统会自动从链接网络提取材料、执行合成、并将产物存入存储。
     *
     * @param player     目标玩家
     * @param recipeId   目标配方的 ID
     * @param craftCount 要合成的次数
     */
    void craftRecipeToLinked(ServerPlayer player, String recipeId, int craftCount);

    /**
     * 按物品 ID 列表填充当前合成格的配方材料。
     * 从链接网络或玩家背包中提取匹配的物品种类填入合成格。
     *
     * @param player       目标玩家
     * @param blueprintIds 配方材料的物品 ID 列表
     * @param craftedItemId 正在合成的目标物品 ID
     * @param craftedCount  已合成的数量
     */
    void refillCurrentCraftGridFromBlueprintIds(ServerPlayer player, List<String> blueprintIds,
                                                String craftedItemId, int craftedCount);

    /**
     * 按物品栈列表填充当前合成格的配方材料。
     * 与 {@link #refillCurrentCraftGridFromBlueprintIds} 类似，
     * 但使用完整的物品栈（包含 NBT 数据）进行匹配。
     *
     * @param player         目标玩家
     * @param blueprintStacks 配方材料的物品栈列表
     * @param craftedItemId  正在合成的目标物品 ID
     * @param craftedCount   已合成的数量
     */
    void refillCurrentCraftGridFromBlueprintStacks(ServerPlayer player, List<ItemStack> blueprintStacks,
                                                   String craftedItemId, int craftedCount);

    /**
     * JEI 一键传输——自动填充合成格并执行合成。
     * 从 JEI 配方界面一键传输材料到合成格并开始合成。
     *
     * @param player              目标玩家
     * @param recipeId            配方 ID
     * @param ingredientPrototypes 配方材料的原型栈列表
     * @param maxTransfer         是否最大化传输（一次合尽可能多次）
     * @param clearGridFirst      是否在填充前清空合成格
     */
    void applyJeiTransfer(ServerPlayer player, String recipeId, List<ItemStack> ingredientPrototypes,
                          boolean maxTransfer, boolean clearGridFirst);

    /**
     * 通用 JEI 配方转移——不绑定特定配方类型，按 (原型, 数量) 对从存储取料填入合成格。
     * 同物品堆叠（单格≤64），超过9格的溢出物品放入玩家背包，再放不下则忽略。
     *
     * @param player         目标玩家
     * @param prototypes     物品原型列表（每种唯一物品一个）
     * @param quantities     每种物品的需求数量
     * @param clearGridFirst 是否在填充前清空合成格
     */
    void applyUniversalJeiTransfer(ServerPlayer player, List<ItemStack> prototypes,
                                   List<Integer> quantities, boolean clearGridFirst);

    /**
     * 快照当前合成格的配方蓝图（材料栈数组）。
     * 用于在自动合成前或合成后记录/恢复合成格状态。
     *
     * @param menu 合成菜单
     * @return 表示当前合成格中材料的物品栈数组
     */
    ItemStack[] snapshotCraftGridBlueprint(AbstractContainerMenu menu);

    /**
     * 从蓝图数组填充合成格的配方材料。
     * 从指定的处理器（链接存储）和玩家背包中提取匹配物品。
     *
     * @param menu                  合成菜单
     * @param handlers              物品处理器列表（链接存储）
     * @param player                目标玩家
     * @param blueprint             配方蓝图（材料栈数组）
     * @param fillAll               是否填充所有材料槽（不保留空缺）
     * @param includePlayerFallback 是否在链接存储不足时从玩家背包补充
     */
    void refillCraftGridFromBlueprint(AbstractContainerMenu menu, List<IItemHandler> extractHandlers,
                                      List<IItemHandler> insertHandlers,
                                      ServerPlayer player, ItemStack[] blueprint,
                                      boolean fillAll, boolean includePlayerFallback);

    /**
     * 从链接存储提取匹配物品填充合成格。
     * 根据蓝图和配方定义，从远程存储中提取准确的物品填充到合成菜单的格子中。
     *
     * @param player       目标玩家
     * @param craftingMenu 合成菜单
     * @param blueprint    配方蓝图（材料栈数组）
     * @param recipe       当前合成配方
     */
    void refillCraftGridFromLinked(ServerPlayer player, AbstractContainerMenu craftingMenu,
                                   ItemStack[] blueprint, net.minecraft.world.item.crafting.CraftingRecipe recipe);

    /**
     * 记录已合成的产出物品到最近物品列表。
     * 用于在合成终端的最近使用物品列表中显示。
     *
     * @param player  目标玩家
     * @param crafted 已合成的产物物品栈
     */
    void recordCraftedOutput(ServerPlayer player, ItemStack crafted);

    /**
     * 清空合成网格中的物品。
     * 当 {@code toPlayerInventory} 为 true 时，物品转入玩家背包；
     * 否则归还到关联存储。
     *
     * @param player            目标玩家
     * @param toPlayerInventory 是否转入玩家背包
     */
    void clearCraftingGrid(ServerPlayer player, boolean toPlayerInventory);
}
