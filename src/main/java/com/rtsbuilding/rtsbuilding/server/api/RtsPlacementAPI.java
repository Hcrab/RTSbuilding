package com.rtsbuilding.rtsbuilding.server.api;

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
}
