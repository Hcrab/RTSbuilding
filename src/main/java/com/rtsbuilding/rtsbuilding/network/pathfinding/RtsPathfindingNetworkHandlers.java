package com.rtsbuilding.rtsbuilding.network.pathfinding;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

/**
 * Server-side handler for {@link C2SRtsPathfindingPayload}.
 * <p>
 * 直接委托给共享的寻路服务，网络层不再保存第二份目标状态。
 */
public final class RtsPathfindingNetworkHandlers {

    private RtsPathfindingNetworkHandlers() {}

    public static void handlePathfinding(C2SRtsPathfindingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().pathfinding().goTo(serverPlayer, payload.target());
            }
        });
    }
}
