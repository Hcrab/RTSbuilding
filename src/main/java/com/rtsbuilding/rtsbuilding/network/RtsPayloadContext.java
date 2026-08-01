package com.rtsbuilding.rtsbuilding.network;

import net.minecraft.world.entity.player.Player;

/**
 * 网络处理器可见的最小上下文，隔离 NeoForge/Fabric 的接收回调类型。
 */
public interface RtsPayloadContext {
    Player player();

    void enqueueWork(Runnable work);
}
