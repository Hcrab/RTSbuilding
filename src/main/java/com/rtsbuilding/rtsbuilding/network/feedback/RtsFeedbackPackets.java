package com.rtsbuilding.rtsbuilding.network.feedback;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers lightweight server-to-client RTS feedback packets.
 *
 * Feedback payloads are S2C-only and should still route through
 * ClientPayloadDispatcher to preserve dedicated-server classloading safety.
 */
public final class RtsFeedbackPackets {
    private RtsFeedbackPackets() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                S2CRtsDamageFeedbackPayload.TYPE,
                S2CRtsDamageFeedbackPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchFeedback);
    }
}
