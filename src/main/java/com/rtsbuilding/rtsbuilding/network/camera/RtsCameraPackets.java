package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers RTS camera session and movement packets.
 *
 * This class groups packet registration only; payload ids, codecs, and packet
 * directions stay in the payload records.
 */
public final class RtsCameraPackets {
    private RtsCameraPackets() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsToggleCameraPayload.TYPE,
                C2SRtsToggleCameraPayload.STREAM_CODEC,
                RtsCameraNetworkHandlers::handleToggle);

        registrar.playToServer(
                C2SRtsCameraMovePayload.TYPE,
                C2SRtsCameraMovePayload.STREAM_CODEC,
                RtsCameraNetworkHandlers::handleMove);

        registrar.playToClient(
                S2CRtsCameraStatePayload.TYPE,
                S2CRtsCameraStatePayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchCamera);
    }
}
