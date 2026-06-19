package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 远程方块放置 API。
 *
 * <p>管理 RTS 模式下的方块放置队列和即时放置操作。
 */
public interface RtsPlacementAPI {

    /**
     * 放置单个选中的方块。
     *
     * @param player           执行玩家
     * @param clickedPos       点击的方块坐标（net.minecraft.core.BlockPos）
     * @param face             点击的面
     * @param hitX             X 命中坐标
     * @param hitY             Y 命中坐标
     * @param hitZ             Z 命中坐标
     * @param rotateSteps      旋转步数
     * @param forcePlace       是否强制放置
     * @param skipIfOccupied   如果被占用则跳过
     * @param itemId           物品 ID
     * @param itemPrototype    物品原型
     * @param rayOriginX       射线起点 X
     * @param rayOriginY       射线起点 Y
     * @param rayOriginZ       射线起点 Z
     * @param rayDirX          射线方向 X
     * @param rayDirY          射线方向 Y
     * @param rayDirZ          射线方向 Z
     * @param quickBuild       是否快速建造
     * @param forceEmptyHand   是否强制空手
     */
    void placeSelected(ServerPlayer player, Object clickedPos, Direction face,
                       double hitX, double hitY, double hitZ,
                       byte rotateSteps, boolean forcePlace, boolean skipIfOccupied,
                       String itemId, ItemStack itemPrototype,
                       double rayOriginX, double rayOriginY, double rayOriginZ,
                       double rayDirX, double rayDirY, double rayDirZ,
                       boolean quickBuild, boolean forceEmptyHand);

    /**
     * 将多个位置加入放置队列。
     */
    void enqueueBatch(ServerPlayer player, List<Object> clickedPositions, Direction face,
                      double hitOffsetX, double hitOffsetY, double hitOffsetZ,
                      byte rotateSteps, boolean forcePlace, boolean skipIfOccupied,
                      String itemId, ItemStack itemPrototype,
                      double rayOriginX, double rayOriginY, double rayOriginZ,
                      double rayDirX, double rayDirY, double rayDirZ);

    // ======================================================================
    //  Placement Progress Queries
    // ======================================================================

    /**
     * 获取当前批量范围放置的总方块数（放置方块总数）。
     *
     * @param player 目标玩家
     * @return 总方块数，如果没有进行中的批量放置则返回 0
     */
    int getPlaceBatchTotalBlocks(ServerPlayer player);

    /**
     * 获取当前批量范围放置的已放置方块数量。
     *
     * @param player 目标玩家
     * @return 已放置方块数，如果没有进行中的批量放置则返回 0
     */
    int getPlaceBatchCompletedBlocks(ServerPlayer player);

    /**
     * 获取当前批量范围放置的未放置方块数（剩余待放置方块）。
     *
     * @param player 目标玩家
     * @return 未放置方块数，如果没有进行中的批量放置则返回 0
     */
    int getPlaceBatchRemainingBlocks(ServerPlayer player);

    /**
     * 获取当前批量范围放置的方块类型（物品 ID）。
     * 返回首个活跃或挂起的放置作业所使用的物品 ID，
     * 便于外部系统（如合成）知道当前在放置什么方块。
     *
     * @param player 目标玩家
     * @return 物品 ID 字符串（如 "minecraft:diamond_block"），
     *         如果没有进行中的批量放置则返回空字符串
     */
    String getPlaceBatchItemId(ServerPlayer player);
}
