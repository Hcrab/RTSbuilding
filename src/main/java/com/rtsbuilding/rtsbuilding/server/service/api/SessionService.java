package com.rtsbuilding.rtsbuilding.server.service.api;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家会话服务接口——管理 RTS 模式会话的生命周期、存储和查询。
 *
 * <p>会话是 RTS 的核心状态对象，每个启用了 RTS 模式的玩家对应一个会话。
 */
public interface SessionService {

    /** 获取或创建玩家的 RTS 会话。 */
    RtsStorageSession getOrCreate(ServerPlayer player);

    /** 获取玩家的 RTS 会话，不存在时返回 null。 */
    RtsStorageSession getIfPresent(ServerPlayer player);

    /** 获取所有活跃会话的只读视图。 */
    Map<UUID, RtsStorageSession> allSessions();

    /** 将会话持久化到玩家 NBT。 */
    void saveToPlayerNbt(ServerPlayer player, RtsStorageSession session);

    /** 玩家启用 RTS 模式时调用。 */
    void onRtsEnabled(ServerPlayer player);

    /** 玩家禁用 RTS 模式时调用。 */
    void onRtsDisabled(ServerPlayer player);

    /** 玩家登出时调用。 */
    void onPlayerLogout(ServerPlayer player);

    /** 获取玩家的当前建造模式。 */
    BuilderMode getMode(ServerPlayer player);
}
