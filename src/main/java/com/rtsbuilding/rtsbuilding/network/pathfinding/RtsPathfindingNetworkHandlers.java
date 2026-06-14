package com.rtsbuilding.rtsbuilding.network.pathfinding;

import com.rtsbuilding.rtsbuilding.server.service.RtsPathfindingService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

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
                RtsPathfindingService.goTo(serverPlayer, payload.target());
            }
        });
    }
}
