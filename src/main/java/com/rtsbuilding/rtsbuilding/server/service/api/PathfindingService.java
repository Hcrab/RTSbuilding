package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * 寻路跟踪服务接口。
 *
 * <p>追踪玩家的移动目标位置。实际移动由客户端处理，
 * 本服务仅记录状态以便在维度切换/登出时进行清理。
 */
public interface PathfindingService {

    /**
     * 记录玩家正前往 {@code target}。
     */
    void goTo(ServerPlayer player, BlockPos target);

    /**
     * 取消玩家的当前移动目标。
     */
    void cancel(ServerPlayer player);

    /**
     * 玩家当前是否有移动目标。
     */
    boolean isMoving(ServerPlayer player);
}
