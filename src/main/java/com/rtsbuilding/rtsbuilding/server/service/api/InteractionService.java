package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * 远程交互服务接口——处理 RTS 模式下与方块/实体的远程交互。
 *
 * <p>该接口定义了 RTS 模式的远程交互功能：
 * 玩家在 RTS 相机视角下，可以通过射线追踪与远距离的方块或实体进行交互，
 * 包括使用物品、打开容器 GUI、与实体互动等操作。
 * 交互时通过 {@code sourceType} 和 {@code toolSlot} 指定使用的物品来源。
 */
public interface InteractionService {

    /**
     * 远程与目标方块或实体进行交互。
     *
     * @param player      目标玩家
     * @param entityId    目标实体 ID（-1 表示交互目标是方块）
     * @param clickedPos  点击的目标方块坐标
     * @param face        点击的面
     * @param hitX,hitY,hitZ 点击位置坐标
     * @param sourceType  源类型（0=主手, 1=快捷槽, 2=工具槽）
     * @param toolSlot    工具槽位编号
     * @param itemId      用于交互的物品 ID
     * @param rayOriginX,rayOriginY,rayOriginZ 射线起点
     * @param rayDirX,rayDirY,rayDirZ 射线方向
     */
    void interactTarget(ServerPlayer player, int entityId, BlockPos clickedPos, Direction face,
                        double hitX, double hitY, double hitZ,
                        byte sourceType, byte toolSlot, String itemId,
                        double rayOriginX, double rayOriginY, double rayOriginZ,
                        double rayDirX, double rayDirY, double rayDirZ);
}
