package com.rtsbuilding.rtsbuilding.server.network;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsHarvestTierSkippedPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

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
        if (payload instanceof S2CRtsHarvestTierSkippedPayload harvest) {
            sendHarvestTierSkipped(player, harvest.positions());
            return;
        }
        PacketDistributor.sendToPlayer(player, payload);
    }

    /**
     * 采掘等级反馈是客户端预览修正，列表可能与一次合法区域一样大；按单包上限拆开，
     * 避免反馈包本身触及 custom payload 限制。每片仍是同一旧 payload 类型，客户端
     * 现有追加移除逻辑无需改变。
     */
    private static void sendHarvestTierSkipped(ServerPlayer player, List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) return;
        for (int from = 0; from < positions.size(); from += S2CRtsHarvestTierSkippedPayload.MAX_POSITIONS) {
            int to = Math.min(positions.size(),
                    from + S2CRtsHarvestTierSkippedPayload.MAX_POSITIONS);
            PacketDistributor.sendToPlayer(player,
                    new S2CRtsHarvestTierSkippedPayload(positions.subList(from, to)));
        }
    }

    public static boolean isGameTestServerPlayer(ServerPlayer player) {
        MinecraftServer server = player == null ? null : player.getServer();
        return server != null && GAMETEST_SERVER_CLASS.equals(server.getClass().getName());
    }
}
