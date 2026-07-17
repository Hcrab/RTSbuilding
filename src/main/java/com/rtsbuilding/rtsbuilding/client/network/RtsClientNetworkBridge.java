package com.rtsbuilding.rtsbuilding.client.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 客户端网络运输边界。
 *
 * <p>UI、输入和工作流只表达“把这个意图发给服务端”，不应知道 NeoForge
 * 在 26.1 将 serverbound 发送器移动到了客户端包。后续 Fabric 或其他
 * NeoForge 版本只替换这个实现。</p>
 */
public final class RtsClientNetworkBridge {
    private RtsClientNetworkBridge() {
    }

    public static void send(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
