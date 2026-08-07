package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.server.culling.RtsCullingPersistence;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 范围剔除的服务端持久化适配器；维度身份始终取服务端玩家当前所在维度。
 *
 * <p>这是客户端视觉功能的最小权威边界：请求只读取该玩家当前维度，保存也拒绝延迟到达的
 * 跨维度旧包。它不施加距离、冷却或额外互动限制，因此不会破坏 RTS 的正常远程操作。</p>
 */
public final class RtsCullingNetworkHandlers {
    private RtsCullingNetworkHandlers() {
    }

    public static void handleRequest(C2SRtsRequestCullingStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RtsCullingPersistence.State state = RtsCullingPersistence.load(player);
                RtsClientboundPackets.sendToPlayer(player,
                        new S2CRtsCullingStatePayload(
                                player.level().dimension().identifier().toString(),
                                state.boxes(), state.revealed()));
            }
        });
    }

    public static void handleSave(C2SRtsSaveCullingStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                String currentDimension = player.level().dimension().identifier().toString();
                // 丢弃跨维度切换后迟到的旧包，不能把旧坐标写进新维度记录。
                if (currentDimension.equals(payload.dimension())) {
                    RtsCullingPersistence.save(player, payload.boxes(), payload.revealed());
                }
            }
        });
    }
}
