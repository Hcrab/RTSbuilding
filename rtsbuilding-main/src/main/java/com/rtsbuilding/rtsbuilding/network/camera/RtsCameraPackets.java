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

        registrar.playToClient(
                S2CRtsCameraStatePayload.TYPE,
                S2CRtsCameraStatePayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchCamera);

        registrar.playToClient(
                S2CRtsCameraAnchorPayload.TYPE,
                S2CRtsCameraAnchorPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchCamera);

        registrar.playToClient(
                S2CRtsDroneAnimPayload.TYPE,
                S2CRtsDroneAnimPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchCamera);
    }
}
