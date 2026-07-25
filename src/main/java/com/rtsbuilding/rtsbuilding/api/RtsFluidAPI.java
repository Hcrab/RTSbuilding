package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * 流体操作 API。
 *
 * <p>管理 RTS 模式下的流体存储和放置。
 */
public interface RtsFluidAPI {

    /**
     * 从容器存储流体到链接存储。
     *
     * @param player     执行玩家
     * @param sourceType 源类型
     * @param toolSlot   工具栏格索引
     * @param itemId     容器物品 ID
     */
    void storeFromContainer(ServerPlayer player, byte sourceType, byte toolSlot, String itemId);

    /**
     * 在目标位置放置流体。
     */
    void placeFluid(ServerPlayer player, Object clickedPos, Direction face,
                    double hitX, double hitY, double hitZ,
                    boolean forcePlace, String fluidId,
                    double rayOriginX, double rayOriginY, double rayOriginZ,
                    double rayDirX, double rayDirY, double rayDirZ);
}
