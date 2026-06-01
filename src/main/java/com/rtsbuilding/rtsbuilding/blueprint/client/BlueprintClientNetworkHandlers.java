package com.rtsbuilding.rtsbuilding.blueprint.client;

import com.rtsbuilding.rtsbuilding.blueprint.network.S2CBlueprintStatusPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class BlueprintClientNetworkHandlers {
    private BlueprintClientNetworkHandlers() {
    }

    public static void handleStatus(S2CBlueprintStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BlueprintPanel.setStatus(payload.status(), payload.messageKey(), payload.detail()));
    }
}
