package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * 流体服务接口——管理流体抽取和放置。
 *
 * <p>该接口定义了 RTS 模式下的远程流体操作：
 * 从世界中的容器抽取流体存入链接网络，
 * 以及从链接网络提取流体放置到世界中。
 */
public interface FluidService {

    /**
     * 从世界中的流体容器（如储罐、桶）抽取流体并存入链接网络。
     *
     * @param player     目标玩家
     * @param sourceType 源类型（0=主手, 1=快捷槽, 2=工具槽）
     * @param toolSlot   工具槽位编号
     * @param itemId     用于抽取的工具/容器物品 ID
     */
    void storeFluidFromContainer(ServerPlayer player, byte sourceType, byte toolSlot, String itemId);

    /**
     * 从链接网络提取流体并放置到世界中的指定位置。
     *
     * @param player      目标玩家
     * @param clickedPos  点击的目标方块坐标
     * @param face        点击的面
     * @param hitX,hitY,hitZ 点击位置坐标
     * @param forcePlace  是否强制放置（模拟潜行点击）
     * @param fluidId     要放置的流体 ID
     * @param rayOriginX,rayOriginY,rayOriginZ 射线起点
     * @param rayDirX,rayDirY,rayDirZ 射线方向
     */
    void placeFluid(ServerPlayer player, BlockPos clickedPos, Direction face,
                    double hitX, double hitY, double hitZ, boolean forcePlace, String fluidId,
                    double rayOriginX, double rayOriginY, double rayOriginZ,
                    double rayDirX, double rayDirY, double rayDirZ);
}
