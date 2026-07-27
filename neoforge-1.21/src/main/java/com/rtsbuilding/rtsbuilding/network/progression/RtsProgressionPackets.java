package com.rtsbuilding.rtsbuilding.network.progression;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers quest-detect and RTS-home packets.
 */
public final class RtsProgressionPackets {
    private RtsProgressionPackets() {
    }

    public static void register(PayloadRegistrar registrar) {

        registrar.playToClient(
                S2CRtsQuestDetectStatusPayload.TYPE,
                S2CRtsQuestDetectStatusPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchProgression);

        registrar.playToClient(
                S2CRtsProgressionStatePayload.TYPE,
                S2CRtsProgressionStatePayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchProgression);
    }
}
