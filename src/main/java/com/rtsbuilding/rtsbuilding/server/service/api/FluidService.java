package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * 流体服务接口——管理流体抽取和放置。
 */
public interface FluidService {

    /** 从容器抽取流体并存入链接网络。 */
    void storeFluidFromContainer(ServerPlayer player, byte sourceType, byte toolSlot, String itemId);

    /** 从链接网络放置流体到世界。 */
    void placeFluid(ServerPlayer player, BlockPos clickedPos, Direction face,
                    double hitX, double hitY, double hitZ, boolean forcePlace, String fluidId,
                    double rayOriginX, double rayOriginY, double rayOriginZ,
                    double rayDirX, double rayDirY, double rayDirZ);
}
