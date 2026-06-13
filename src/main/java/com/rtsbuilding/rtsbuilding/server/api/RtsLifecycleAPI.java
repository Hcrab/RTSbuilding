package com.rtsbuilding.rtsbuilding.server.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端生命周期钩???
 *
 * <p>RTS 核心框架自动注册这些事件处理???
 * 外部模组通常不需要直接调用这些方???
 */
public interface RtsLifecycleAPI {

    /**
     * 当玩家进??RTS 模式时调???
     *
     * @param player 目标玩家
     */
    void onRtsEnabled(ServerPlayer player);

    /**
     * 当玩家退??RTS 模式时调???
     *
     * @param player 目标玩家
     */
    void onRtsDisabled(ServerPlayer player);

    /**
     * 当玩家登出时调用??
     *
     * @param player 目标玩家
     */
    void onPlayerLogout(ServerPlayer player);

    /**
     * 在玩??tick 处理之前调用??
     */
    void onPlayerTickPre(ServerPlayer player);

    /**
     * 在玩??tick 处理之后调用??
     */
    void onPlayerTickPost(ServerPlayer player);

    /**
     * 全局 tick 处理（主要用于挖掘进度）??
     *
     * @param server Minecraft 服务器实??
     */
    void tick(MinecraftServer server);

    /**
     * 预热创造模式物品栏缓存??
     */
    void warmCreativeTabCaches(MinecraftServer server);
}
