package com.rtsbuilding.rtsbuilding.server.network;

import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * 服务端到客户端 RTS 自定义包的统一出口。
 *
 * <p>正常游戏中委托给 1.12.2 的统一 SimpleNetworkWrapper 出口。自动化测试若使用没有
 * 客户端握手的假玩家，则继续按服务器实现类名跳过发送，让服务端行为测试专注于业务链路；
 * 普通专用服务器玩家不会进入这条跳过路径。</p>
 */
public final class RtsClientboundPackets {
    private static final String GAMETEST_SERVER_CLASS = "net.minecraft.gametest.framework.GameTestServer";

    private RtsClientboundPackets() {
    }

    public static void sendToPlayer(EntityPlayerMP player, IMessage payload) {
        if (player == null || payload == null || isGameTestServerPlayer(player)) {
            return;
        }
        RtsPayloadRegistrar.sendToPlayer(player, payload);
    }

    public static boolean isGameTestServerPlayer(EntityPlayerMP player) {
        MinecraftServer server = player == null ? null : player.getServer();
        return server != null && GAMETEST_SERVER_CLASS.equals(server.getClass().getName());
    }
}
