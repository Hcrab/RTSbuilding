package com.rtsbuilding.rtsbuilding.network.blueprint;


import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkHandlers;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

public final class BlueprintClientPayloadBridge {
    private BlueprintClientPayloadBridge() {
    }

    public static void handleStatus(S2CBlueprintStatusPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            RtsClientNetworkHandlers.handleBlueprintStatus(payload, context);
        }
    }
}
