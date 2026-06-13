package com.rtsbuilding.rtsbuilding.server.api;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import net.minecraft.server.level.ServerPlayer;

/**
 * 会话查询 API。
 *
 * <p>允许外部模组查询玩家的 RTS 会话状态，
 * 而无需直接访问 RtsStorageSession 内部类。
 */
public interface RtsSessionQueryAPI {

    /**
     * 获取玩家当前的建造模式。
     *
     * @param player 目标玩家
     * @return 当前模式，非 RTS 模式下返回 INTERACT
     */
    BuilderMode getMode(ServerPlayer player);
}
