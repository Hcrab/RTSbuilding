package com.rtsbuilding.rtsbuilding.network.feedback;

import com.rtsbuilding.rtsbuilding.network.RtsClientPayloadBridge;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RtsFeedbackPackets {
    private RtsFeedbackPackets() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                S2CRtsDamageFeedbackPayload.TYPE,
                S2CRtsDamageFeedbackPayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleDamageFeedback);
    }
}
