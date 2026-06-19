package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * 蓝图材料服务接口——管理蓝图所需的材料统计、提取、退还和页面刷新。
 *
 * <p>该接口提供 RTS 蓝图放置系统所需的材料管理功能：
 * 统计链接网络和玩家背包中指定物品/流体的可用数量、
 * 从网络提取材料、退还多余材料、记录已放置的蓝图方块
 * 以及刷新蓝图对应的存储页面。
 *
 * <p>Phase 2 服务解耦的一部分。将 {@code RtsBlueprintService}
 * 的静态方法封装为实例方法，便于依赖注入和单元测试。
 */
public interface BlueprintService {

    /**
     * 统计指定物品在链接网络和玩家背包中的总可用数量。
     * 用于在蓝图放置前检查材料是否充足。
     *
     * @param player 目标玩家
     * @param item   要统计的物品
     * @return 物品的总可用数量
     */
    long countMaterial(ServerPlayer player, Item item);

    /**
     * 从链接网络提取指定数量的物品用于蓝图放置。
     *
     * @param player 目标玩家
     * @param item   要提取的物品类型
     * @param count  要提取的数量
     * @return 实际提取到的物品栈（可能少于请求数量）
     */
    ItemStack extractMaterial(ServerPlayer player, Item item, int count);

    /**
     * 统计指定流体在链接网络中的总可用量（以毫桶 mB 为单位）。
     * 用于在蓝图放置前检查流体是否充足。
     *
     * @param player 目标玩家
     * @param fluid  要统计的流体类型
     * @return 流体的总可用量（mB）
     */
    long countFluidMb(ServerPlayer player, Fluid fluid);

    /**
     * 从链接网络提取指定量的流体用于蓝图放置。
     *
     * @param player   目标玩家
     * @param fluid    要提取的流体类型
     * @param amountMb 要提取的量（mB）
     * @return {@code true} 如果提取成功
     */
    boolean extractFluid(ServerPlayer player, Fluid fluid, int amountMb);

    /**
     * 将多余的材料退还到链接存储或玩家背包。
     * 当蓝图放置取消或部分完成后，将已提取但未使用的材料退还。
     *
     * @param player 目标玩家
     * @param stack  要退还的物品栈
     */
    void refundMaterial(ServerPlayer player, ItemStack stack);

    /**
     * 记录已放置的蓝图方块并播放相应的放置音效。
     * 同时会更新已放置方块追踪数据。
     *
     * @param player 目标玩家
     * @param pos    已放置方块的世界坐标
     * @param itemId 放置的物品 ID
     */
    void noteBlockPlaced(ServerPlayer player, BlockPos pos, String itemId);

    /**
     * 刷新蓝图对应的存储页面。
     * 在材料变更后调用，以确保客户端显示最新的存储状态。
     *
     * @param player 目标玩家
     */
    void refreshPage(ServerPlayer player);
}
