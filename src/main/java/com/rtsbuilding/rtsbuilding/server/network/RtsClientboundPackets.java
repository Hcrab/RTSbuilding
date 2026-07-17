package com.rtsbuilding.rtsbuilding.server.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 服务端到客户端 RTS 自定义包的统一出口。
 *
 * <p>正常游戏中直接委托给 NeoForge 的 {@link PacketDistributor}。GameTest server 使用
 * embedded mock player，它没有真实客户端握手，向它发送自定义 S2C payload 会让测试因为
 * 网络层限制而失败。这里跳过 GameTest 假客户端同步，让 server smoke test 专注验证服务端链路状态。</p>
 */
public final class RtsClientboundPackets {
    private static final String GAMETEST_SERVER_CLASS = "net.minecraft.gametest.framework.GameTestServer";

    private RtsClientboundPackets() {
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player == null || payload == null || isGameTestServerPlayer(player)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static boolean isGameTestServerPlayer(ServerPlayer player) {
        MinecraftServer server = player == null ? null : player.level().getServer();
        return server != null && GAMETEST_SERVER_CLASS.equals(server.getClass().getName());
    }
}
