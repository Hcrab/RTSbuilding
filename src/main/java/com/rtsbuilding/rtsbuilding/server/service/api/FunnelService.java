package com.rtsbuilding.rtsbuilding.server.service.api;

import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * 掉落物漏斗服务接口——自动收集地面掉落物并存入链接存储。
 */
public interface FunnelService {

    /**
     * 启用漏斗。
     */
    void enable(ServerPlayer player, RtsStorageSession session);

    /**
     * 禁用漏斗并清空缓冲区。
     */
    void disableAndFlush(ServerPlayer player, RtsStorageSession session);

    /**
     * 更新漏斗目标位置。
     */
    void updateTarget(ServerPlayer player, RtsStorageSession session, BlockPos target);

    /**
     * Tick 处理漏斗逻辑。
     */
    void tick(ServerPlayer player, RtsStorageSession session);
}
