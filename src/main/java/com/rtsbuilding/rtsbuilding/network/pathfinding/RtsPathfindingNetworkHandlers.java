package com.rtsbuilding.rtsbuilding.network.pathfinding;

import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

/**
 * Server-side handler for {@link C2SRtsPathfindingPayload}.
 * <p>
 * Delegates directly to {@link RtsPathfindingService#goTo} — no A*, no
 * goal abstraction, just a simple straight-line walk to the target block.
 */
public final class RtsPathfindingNetworkHandlers {

    private RtsPathfindingNetworkHandlers() {}

    public static void handlePathfinding(C2SRtsPathfindingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                // 1.20.1 端的实际移动同样完全由客户端完成；服务端只验证 RTS 会话和目标范围。
                if (RtsCameraManager.isActive(serverPlayer)
                        && RtsCameraManager.isWithinActionRange(serverPlayer, payload.target())) {
                    RtsPathfindingTargetTracker.goTo(serverPlayer, payload.target());
                }
            }
        });
    }
}
