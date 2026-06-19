package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 放置服务接口——管理 RTS 模式下的远程方块放置、批量放置和方块旋转。
 *
 * <p>该接口定义了 RTS 储存构建器的放置功能：
 * 支持单个方块的远程交互式放置、大批量方块放置入队处理、
 * 挂起放置作业的提交恢复、以及已放置方块的旋转操作。
 * 放置时支持从链接存储或玩家背包中提取物品。
 */
public interface PlacementService {

    /**
     * 在指定位置远程放置一个选中的方块。
     * 支持从链接存储或玩家背包中提取物品进行放置。
     *
     * @param player      目标玩家
     * @param clickedPos  点击的目标方块坐标
     * @param face        点击的面
     * @param hitX,hitY,hitZ 点击位置坐标
     * @param rotateSteps 顺时针 90 度旋转步数
     * @param forcePlace  是否强制放置（模拟潜行点击）
     * @param skipIfOccupied 跳过已被占用的位置
     * @param itemId      储存物品 ID（null 或空则使用主手物品）
     * @param itemPrototype 提取的首选原型物品栈
     * @param rayOriginX,rayOriginY,rayOriginZ 射线起点
     * @param rayDirX,rayDirY,rayDirZ 射线方向
     * @param quickBuild  是否为快速建造（使用预解析的状态计划）
     * @param forceEmptyHand 是否强制使用空手交互
     */
    void placeSelected(ServerPlayer player, BlockPos clickedPos, Direction face,
                       double hitX, double hitY, double hitZ, byte rotateSteps,
                       boolean forcePlace, boolean skipIfOccupied, String itemId,
                       ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
                       double rayDirX, double rayDirY, double rayDirZ,
                       boolean quickBuild, boolean forceEmptyHand);

    /**
     * 将一组方块位置的批量放置请求加入处理队列。
     * 批量放置以每 tick 限制速度处理，避免单帧负载过高。
     *
     * @param player           目标玩家
     * @param clickedPositions 点击的目标方块坐标列表
     * @param face             点击的面
     * @param hitOffsetX,hitOffsetY,hitOffsetZ 点击偏移量
     * @param rotateSteps      旋转步数
     * @param forcePlace       是否强制放置
     * @param skipIfOccupied   跳过已被占用的位置
     * @param itemId           储存物品 ID
     * @param itemPrototype    原型物品栈
     * @param rayOriginX,rayOriginY,rayOriginZ 射线起点
     * @param rayDirX,rayDirY,rayDirZ 射线方向
     */
    void enqueuePlaceBatch(ServerPlayer player, List<BlockPos> clickedPositions, Direction face,
                           double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
                           boolean forcePlace, boolean skipIfOccupied, String itemId,
                           ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
                           double rayDirX, double rayDirY, double rayDirZ);

    /**
     * 提交当前挂起的放置作业（因物品不足而暂停的作业）继续执行。
     * 当玩家补充了物品后，调用此方法恢复被挂起的放置作业。
     *
     * @param player 目标玩家
     * @return 已恢复的放置作业数量
     */
    int submitPendingPlacement(ServerPlayer player);

    /**
     * 旋转世界中已放置的方块。
     *
     * @param player 目标玩家
     * @param pos    要旋转的方块坐标
     */
    void rotateBlock(ServerPlayer player, BlockPos pos);

    /**
     * 获取当前批量放置作业中的总方块数。
     *
     * @param player 目标玩家
     * @return 总方块数
     */
    int getPlaceBatchTotalBlocks(ServerPlayer player);

    /**
     * 获取当前批量放置作业中已完成（已放置）的方块数量。
     *
     * @param player 目标玩家
     * @return 已放置方块数
     */
    int getPlaceBatchCompletedBlocks(ServerPlayer player);

    /**
     * 获取当前批量放置作业中剩余的未放置方块数。
     *
     * @param player 目标玩家
     * @return 未放置方块数
     */
    int getPlaceBatchRemainingBlocks(ServerPlayer player);

    /**
     * 获取当前批量放置作业正在放置的方块类型（物品 ID）。
     *
     * @param player 目标玩家
     * @return 物品 ID 字符串
     */
    String getPlaceBatchItemId(ServerPlayer player);
}
