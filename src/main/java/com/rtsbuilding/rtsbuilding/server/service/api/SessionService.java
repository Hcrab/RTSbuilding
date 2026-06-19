package com.rtsbuilding.rtsbuilding.server.service.api;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家会话服务接口——管理 RTS 模式会话的完整生命周期、存储和查询。
 *
 * <p>会话（{@link RtsStorageSession}）是 RTS 模式的核心状态对象，
 * 每个启用了 RTS 模式的玩家对应一个唯一的会话实例。
 * 会话包含链接存储信息、快捷槽配置、模式设置等所有玩家自定义状态。
 *
 * <p>该服务负责会话的创建、持久化（写入玩家 NBT）、
 * 以及玩家启用/禁用 RTS 模式和登出时的生命周期回调。
 */
public interface SessionService {

    /**
     * 获取或创建指定玩家的 RTS 会话。
     * 如果玩家尚无会话，则会从玩家的持久化数据中加载或创建新会话。
     *
     * @param player 目标玩家
     * @return 玩家的 RTS 储存会话
     */
    RtsStorageSession getOrCreate(ServerPlayer player);

    /**
     * 获取指定玩家的 RTS 会话（如果存在）。
     * 与 {@link #getOrCreate} 不同，此方法不会创建新会话。
     *
     * @param player 目标玩家
     * @return 玩家的 RTS 储存会话，或 {@code null} 如果会话不存在
     */
    RtsStorageSession getIfPresent(ServerPlayer player);

    /**
     * 获取所有活跃的 RTS 会话的只读视图。
     * 用于服务器范围的遍历操作（如方块破坏事件清理引用）。
     *
     * @return 玩家 UUID 到会话的映射
     */
    Map<UUID, RtsStorageSession> allSessions();

    /**
     * 将会话状态持久化到玩家的 NBT 数据中。
     * 在关键操作后调用，确保玩家数据不会丢失。
     *
     * @param player  目标玩家
     * @param session 要保存的会话
     */
    void saveToPlayerNbt(ServerPlayer player, RtsStorageSession session);

    /**
     * 玩家启用 RTS 模式时触发的生命周期回调。
     * 负责初始化会话、注册 Tick 服务等。
     *
     * @param player 目标玩家
     */
    void onRtsEnabled(ServerPlayer player);

    /**
     * 玩家禁用 RTS 模式时触发的生命周期回调。
     * 负责清理会话资源、保存状态、注销 Tick 服务等。
     *
     * @param player 目标玩家
     */
    void onRtsDisabled(ServerPlayer player);

    /**
     * 玩家登出时触发的生命周期回调。
     * 负责保存最终会话状态并清理服务端资源。
     *
     * @param player 目标玩家
     */
    void onPlayerLogout(ServerPlayer player);

    /**
     * 获取玩家当前的建造模式。
     *
     * @param player 目标玩家
     * @return 当前的建造模式枚举值
     */
    BuilderMode getMode(ServerPlayer player);
}
