package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;
import com.rtsbuilding.rtsbuilding.server.culling.RtsCullingPersistence;
import net.minecraft.server.level.ServerPlayer;

/**
 * 范围剔除的服务端持久化适配器。
 *
 * <p>维度身份始终取服务端当前玩家状态；客户端提交的维度只用于丢弃切换维度后迟到的旧包。</p>
 */
public final class RtsCullingNetworkHandlers {
    private RtsCullingNetworkHandlers() {
    }

    public static void handleRequest(C2SRtsRequestCullingStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RtsCullingPersistence.State state = RtsCullingPersistence.load(player);
                PacketDistributor.sendToPlayer(player, new S2CRtsCullingStatePayload(
                        player.level().dimension().location().toString(),
                        state.boxes(),
                        state.revealed()));
            }
        });
    }

    public static void handleSave(C2SRtsSaveCullingStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                String currentDimension = player.level().dimension().location().toString();
                if (currentDimension.equals(payload.dimension())) {
                    RtsCullingPersistence.save(player, payload.boxes(), payload.revealed());
                }
            }
        });
    }
}
