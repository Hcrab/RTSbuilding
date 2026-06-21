package com.rtsbuilding.rtsbuilding.forgecompat.network;


import com.rtsbuilding.rtsbuilding.network.RtsForgePayloadRegistrar;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PacketDistributor {
    private static final String GAMETEST_SERVER_CLASS = "net.minecraft.gametest.framework.GameTestServer";

    private PacketDistributor() {
    }

    public static void sendToServer(final Object message) {
        RtsForgePayloadRegistrar.CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(final ServerPlayer player, final Object message) {
        if (player == null || message == null || isGameTestServerPlayer(player) || !hasNetworkChannel(player)) {
            return;
        }
        RtsForgePayloadRegistrar.sendToPlayer(player, message);
    }

    private static boolean hasNetworkChannel(final ServerPlayer player) {
        if (player.connection == null) {
            return false;
        }
        // GameTest 的 mock player 没有真实 Netty channel，不能发客户端同步包。
        Connection connection = player.connection.connection;
        return connection != null && connection.channel() != null;
    }

    public static boolean isGameTestServerPlayer(final ServerPlayer player) {
        MinecraftServer server = player == null ? null : player.getServer();
        return server != null && GAMETEST_SERVER_CLASS.equals(server.getClass().getName());
    }
}
