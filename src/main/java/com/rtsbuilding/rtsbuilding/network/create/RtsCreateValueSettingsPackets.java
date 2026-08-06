package com.rtsbuilding.rtsbuilding.network.create;

import com.rtsbuilding.rtsbuilding.forgecompat.network.ForgePayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.create.handler.RtsCreateValueSettingsNetworkHandler;

/** 只负责注册 Create Value Settings 的 RTS 专用传输。 */
public final class RtsCreateValueSettingsPackets {
    private RtsCreateValueSettingsPackets() {
    }

    public static void register(ForgePayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsCreateValueSettingsPayload.TYPE,
                C2SRtsCreateValueSettingsPayload.STREAM_CODEC,
                RtsCreateValueSettingsNetworkHandler::handle);
    }
}
