package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * 远程交互 API。
 *
 * <p>处理 RTS 模式下右键点击方块、实体、使用物品等操作。
 */
public interface RtsInteractionAPI {

    /**
     * 远程与目标交互（使用工具栏格物品、大头针物品或空手）。
     *
     * @param player      执行玩家
     * @param entityId    目标实体 ID（-1 = 无实体目标）
     * @param clickedPos  点击的方块坐标（net.minecraft.core.BlockPos）
     * @param face        点击的面
     * @param hitX        X 命中坐标
     * @param hitY        Y 命中坐标
     * @param hitZ        Z 命中坐标
     * @param sourceType  交互源类型（工具栏格/大头针/空手）
     * @param toolSlot    工具栏格索引
     * @param itemId      物品 ID
     * @param rayOriginX  射线起点 X
     * @param rayOriginY  射线起点 Y
     * @param rayOriginZ  射线起点 Z
     * @param rayDirX     射线方向 X
     * @param rayDirY     射线方向 Y
     * @param rayDirZ     射线方向 Z
     */
    void interactTarget(ServerPlayer player, int entityId, Object clickedPos,
                        Direction face, double hitX, double hitY, double hitZ,
                        byte sourceType, byte toolSlot, String itemId,
                        double rayOriginX, double rayOriginY, double rayOriginZ,
                        double rayDirX, double rayDirY, double rayDirZ);

    /**
     * 远程破坏已放置的方块。
     *
     * @param player               执行玩家
     * @param pos                  目标坐标（net.minecraft.core.BlockPos）
     * @param face                 破坏的面
     * @param allowAdjacentFallback 是否允许相邻回退
     */
    void breakPlaced(ServerPlayer player, Object pos, Direction face, boolean allowAdjacentFallback);

    /**
     * 远程旋转方块。
     *
     * @param player 执行玩家
     * @param pos    目标坐标（net.minecraft.core.BlockPos）
     */
    void rotateBlock(ServerPlayer player, Object pos);
}
