package com.rtsbuilding.rtsbuilding.network.blueprint;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;

public final class BlueprintPayloadRegistrar {
    private BlueprintPayloadRegistrar() {
    }

    public static void register(RtsPayloadRegistrar registrar) {
        registrar.playToServer(
                C2SBlueprintPlacePayload.TYPE,
                C2SBlueprintPlacePayload.STREAM_CODEC,
                BlueprintNetworkHandlers::handlePlace);

        registrar.playToClient(
                S2CBlueprintStatusPayload.TYPE,
                S2CBlueprintStatusPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBlueprintStatus);
    }
}
