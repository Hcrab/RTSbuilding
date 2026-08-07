package com.rtsbuilding.rtsbuilding.network.create;

import com.rtsbuilding.rtsbuilding.network.create.handler.RtsCreateValueSettingsNetworkHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 仅注册 RTS Create Value Settings 的单一 C2S 兼容包。 */
public final class RtsCreateValueSettingsPackets {
    private RtsCreateValueSettingsPackets() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsCreateValueSettingsPayload.TYPE,
                C2SRtsCreateValueSettingsPayload.STREAM_CODEC,
                RtsCreateValueSettingsNetworkHandler::handle);
    }
}
