package com.rtsbuilding.rtsbuilding.network.pathfinding;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadContext;

/**
 * Server-side handler for {@link C2SRtsPathfindingPayload}.
 * <p>
 * Delegates directly to {@link RtsPathfindingService#goTo} — no A*, no
 * goal abstraction, just a simple straight-line walk to the target block.
 */
public final class RtsPathfindingNetworkHandlers {

    private RtsPathfindingNetworkHandlers() {}

    public static void handlePathfinding(C2SRtsPathfindingPayload payload, RtsPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().pathfinding().goTo(serverPlayer, payload.target());
            }
        });
    }
}
