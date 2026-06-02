package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.network.RtsClientPayloadBridge;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkHandlers;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RtsCameraPackets {
    private RtsCameraPackets() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsToggleCameraPayload.TYPE,
                C2SRtsToggleCameraPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleToggle);

        registrar.playToServer(
                C2SRtsCameraMovePayload.TYPE,
                C2SRtsCameraMovePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleMove);

        registrar.playToClient(
                S2CRtsCameraStatePayload.TYPE,
                S2CRtsCameraStatePayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleCameraState);
    }
}
