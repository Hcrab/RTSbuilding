package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers RTS placement, mining, interaction, and quick-drop packets.
 *
 * This class groups packet registration only; payload ids, codecs, and packet
 * directions stay in the payload records.
 */
public final class RtsBuilderPackets {
    private RtsBuilderPackets() {
    }

    public static void register(PayloadRegistrar registrar) {

        registrar.playToClient(
                S2CRtsMineProgressPayload.TYPE,
                S2CRtsMineProgressPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);

        registrar.playToClient(
                S2CRtsPlaceAnimationPayload.TYPE,
                S2CRtsPlaceAnimationPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);

        registrar.playToClient(
                S2CRtsBreakAnimationPayload.TYPE,
                S2CRtsBreakAnimationPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);

        registrar.playToClient(
                S2CRtsUltimineProgressPayload.TYPE,
                S2CRtsUltimineProgressPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);

        // ===== Undo =====

        registrar.playToClient(
                S2CRtsHistorySyncPayload.TYPE,
                S2CRtsHistorySyncPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);

        registrar.playToClient(
                S2CRtsWorkflowProgressPayload.TYPE,
                S2CRtsWorkflowProgressPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);

        registrar.playToClient(
                S2CRtsWorkflowProgressBatchPayload.TYPE,
                S2CRtsWorkflowProgressBatchPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchBuilder);
    }
}
