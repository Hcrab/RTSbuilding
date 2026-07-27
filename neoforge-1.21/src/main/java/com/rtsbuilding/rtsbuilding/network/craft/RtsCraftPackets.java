package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers RTS crafting terminal, craftable-list, and JEI transfer packets.
 *
 * This class groups packet registration only; payload ids, codecs, and packet
 * directions stay in the payload records.
 */
public final class RtsCraftPackets {
    private RtsCraftPackets() {
    }

    public static void register(PayloadRegistrar registrar) {

        registrar.playToClient(
                S2CRtsCraftablesPayload.TYPE,
                S2CRtsCraftablesPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchCraft);

        registrar.playToClient(
                S2CRtsCraftFeedbackPayload.TYPE,
                S2CRtsCraftFeedbackPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchCraft);
    }
}
