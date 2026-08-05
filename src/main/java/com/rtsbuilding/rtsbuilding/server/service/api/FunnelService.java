package com.rtsbuilding.rtsbuilding.server.service.api;

import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

/**
 * 掉落物漏斗服务接口——自动收集地面掉落物并存入链接存储。
 *
 * <p>该接口定义了 RTS 模式的掉落物自动收集功能：
 * 漏斗会持续扫描目标位置附近的掉落物实体，
 * 自动吸入并存入玩家的链接存储系统。
 */
public interface FunnelService {

    /**
     * 启用掉落物漏斗，开始自动收集。
     *
     * @param player  目标玩家
     * @param session 玩家的 RTS 储存会话
     */
    void enable(EntityPlayerMP player, RtsStorageSession session);

    /**
     * 禁用掉落物漏斗并清空内部缓冲区中的所有物品到链接存储。
     *
     * @param player  目标玩家
     * @param session 玩家的 RTS 储存会话
     */
    void disableAndFlush(EntityPlayerMP player, RtsStorageSession session);

    /**
     * 更新漏斗的掉落物收集目标位置。
     * 漏斗将以该位置为中心扫描周围的掉落物实体。
     *
     * @param player  目标玩家
     * @param session 玩家的 RTS 储存会话
     * @param target  新的收集目标位置
     */
    void updateTarget(EntityPlayerMP player, RtsStorageSession session, BlockPos target);

    /**
     * 每 Tick 处理漏斗逻辑：扫描目标位置的掉落物并吸入链接存储。
     *
     * @param player  目标玩家
     * @param session 玩家的 RTS 储存会话
     */
    void tick(EntityPlayerMP player, RtsStorageSession session);

    /** 由统一 Task Engine 调用的预算化入口。 */
    FunnelTickResult tickBudgeted(
            EntityPlayerMP player, RtsStorageSession session, int maxUnits, long deadlineNanos);

    final class FunnelTickResult {
        private final int processedUnits;
        private final boolean active;

        public FunnelTickResult(int processedUnits, boolean active) {
            this.processedUnits = processedUnits;
            this.active = active;
        }

        public int processedUnits() {
            return processedUnits;
        }

        public boolean active() {
            return active;
        }
    }
}
