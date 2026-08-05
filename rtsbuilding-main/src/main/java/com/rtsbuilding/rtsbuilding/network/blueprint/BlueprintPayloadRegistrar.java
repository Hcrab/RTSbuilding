package com.rtsbuilding.rtsbuilding.network.blueprint;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class BlueprintPayloadRegistrar {
    private BlueprintPayloadRegistrar() {
    }

    public static void register(PayloadRegistrar registrar) {

        registrar.playToClient(
                S2CBlueprintStatusPayload.TYPE,
                S2CBlueprintStatusPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBlueprintStatus);
    }
}
