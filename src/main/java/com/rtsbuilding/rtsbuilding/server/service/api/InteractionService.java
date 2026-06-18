package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * 远程交互服务接口——处理 RTS 模式下与方块/实体的远程交互。
 */
public interface InteractionService {

    /** 远程交互目标（方块/实体）。 */
    void interactTarget(ServerPlayer player, int entityId, BlockPos clickedPos, Direction face,
                        double hitX, double hitY, double hitZ,
                        byte sourceType, byte toolSlot, String itemId,
                        double rayOriginX, double rayOriginY, double rayOriginZ,
                        double rayDirX, double rayDirY, double rayDirZ);
}
