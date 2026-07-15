package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 流体服务接口——管理流体抽取和放置。
 */
public interface FluidService {

    void storeFluidFromContainer(ServerPlayer player, byte sourceType, byte toolSlot, String itemId);

    void placeFluid(ServerPlayer player, BlockPos clickedPos, Direction face,
                    double hitX, double hitY, double hitZ, boolean forcePlace, String fluidId,
                    double rayOriginX, double rayOriginY, double rayOriginZ,
                    double rayDirX, double rayDirY, double rayDirZ);

    /** 排队批量流体放置（智能放置湖泊填充）。 */
    void enqueuePlaceFluidBatch(ServerPlayer player, List<BlockPos> positions, String fluidId);
}
