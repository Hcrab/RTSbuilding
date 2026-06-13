package com.rtsbuilding.rtsbuilding.server.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * 蓝图材料 API：为蓝图系统提供材料查询与提???
 *
 * <p>附属模组（如蓝图系统）可通过此接口从玩家的链接存??
 * 中精确提取特定物品用于建???
 */
public interface RtsBlueprintAPI {

    /**
     * 计算特定物品在链接存储和玩家主物品栏中的总量??
     *
     * @param player 目标玩家
     * @param item   要计数的物品
     * @return 可用的物品总数
     */
    long countMaterial(ServerPlayer player, Item item);

    /**
     * 从链接存储中提取指定数量的物???
     *
     * @param player 目标玩家
     * @param item   要提取的物品类型
     * @param count  期望提取的数??
     * @return 实际提取到的 ItemStack（可能少于请求量??
     */
    ItemStack extractMaterial(ServerPlayer player, Item item, int count);

    /**
     * 计算指定流体在链接存储中的总量??
     *
     * @param player 目标玩家
     * @param fluid  目标流体
     * @return 流体总量（mB??
     */
    long countFluidMb(ServerPlayer player, Fluid fluid);

    /**
     * 从链接存储中提取指定量的流体??
     *
     * @param player   目标玩家
     * @param fluid    目标流体
     * @param amountMb 提取量（mB??
     * @return 是否成功提取了足量的流体
     */
    boolean extractFluid(ServerPlayer player, Fluid fluid, int amountMb);

    /**
     * 将物品退还到链接存储（蓝图放置取消时使用???
     *
     * @param player 目标玩家
     * @param stack  要退还的物品
     */
    void refundMaterial(ServerPlayer player, ItemStack stack);

    /**
     * 通知蓝图系统已放置一个方块（更新最近条目和声音???
     *
     * @param player  执行放置的玩??
     * @param pos     放置位置（使??net.minecraft.core.BlockPos??
     * @param itemId  放置的物??ID
     */
    void noteBlockPlaced(ServerPlayer player, Object pos, String itemId);

    /**
     * 刷新蓝图的存储页???
     *
     * @param player 目标玩家
     */
    void refreshPage(ServerPlayer player);
}
