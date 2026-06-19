package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * 寻路跟踪服务接口——追踪玩家在 RTS 模式下的移动目标。
 *
 * <p>该接口记录玩家通过 RTS 寻路功能设置的移动目标位置。
 * 实际移动由客户端处理（平滑寻路动画），本服务仅在服务端
 * 记录目标状态，以便在玩家切换维度或登出时进行清理。
 */
public interface PathfindingService {

    /**
     * 记录玩家正前往指定的目标位置。
     * 当客户端开始寻路移动时，服务端记录此目标用于状态追踪。
     *
     * @param player 目标玩家
     * @param target 目标方块坐标
     */
    void goTo(ServerPlayer player, BlockPos target);

    /**
     * 取消玩家的当前移动目标。
     * 当玩家手动停止移动或到达目标时调用。
     *
     * @param player 目标玩家
     */
    void cancel(ServerPlayer player);

    /**
     * 检查玩家当前是否有活跃的移动目标。
     *
     * @param player 目标玩家
     * @return {@code true} 如果玩家正在移动中
     */
    boolean isMoving(ServerPlayer player);
}
