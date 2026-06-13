package com.rtsbuilding.rtsbuilding.blueprint.network;

import com.rtsbuilding.rtsbuilding.forgecompat.network.ForgePayloadRegistrar;

public final class BlueprintPayloadRegistrar {
    private BlueprintPayloadRegistrar() {
    }

    public static void register(ForgePayloadRegistrar registrar) {
        registrar.playToServer(
                C2SBlueprintPlacePayload.TYPE,
                C2SBlueprintPlacePayload.STREAM_CODEC,
                BlueprintNetworkHandlers::handlePlace);

        registrar.playToClient(
                S2CBlueprintStatusPayload.TYPE,
                S2CBlueprintStatusPayload.STREAM_CODEC,
                BlueprintClientPayloadBridge::handleStatus);
    }
}
