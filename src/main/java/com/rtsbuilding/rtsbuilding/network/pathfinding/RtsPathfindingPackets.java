package com.rtsbuilding.rtsbuilding.network.pathfinding;

import com.rtsbuilding.rtsbuilding.forgecompat.network.ForgePayloadRegistrar;

/**
 * Registers RTS pathfinding C2S packet.
 */
public final class RtsPathfindingPackets {

    private RtsPathfindingPackets() {}

    public static void register(ForgePayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsPathfindingPayload.TYPE,
                C2SRtsPathfindingPayload.STREAM_CODEC,
                RtsPathfindingNetworkHandlers::handlePathfinding);
    }
}
