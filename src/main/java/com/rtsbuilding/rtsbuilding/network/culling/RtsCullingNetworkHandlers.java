package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.server.culling.RtsCullingPersistence;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 范围剔除的服务端存档适配器；维度身份始终取服务端玩家当前所在维度。 */
public final class RtsCullingNetworkHandlers {
    private RtsCullingNetworkHandlers() {
    }

    public static void handleRequest(C2SRtsRequestCullingStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RtsCullingPersistence.State state = RtsCullingPersistence.load(player);
                RtsClientboundPackets.sendToPlayer(player,
                        new S2CRtsCullingStatePayload(
                                player.level().dimension().location().toString(),
                                state.boxes(), state.revealed()));
            }
        });
    }

    public static void handleSave(C2SRtsSaveCullingStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                String currentDimension = player.level().dimension().location().toString();
                // 丢弃跨维度切换迟到的旧包，避免把旧维度坐标写进新维度记录。
                if (currentDimension.equals(payload.dimension())) {
                    RtsCullingPersistence.save(player, payload.boxes(), payload.revealed());
                }
            }
        });
    }
}
