package com.rtsbuilding.rtsbuilding.network.progression;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import com.rtsbuilding.rtsbuilding.network.progression.handler.RtsProgressionNetworkHandlers;
import com.rtsbuilding.rtsbuilding.forgecompat.network.ForgePayloadRegistrar;

/**
 * Registers survival-progression, quest-detect, and RTS-home packets.
 *
 * This class groups packet registration only; payload ids, codecs, and packet
 * directions stay in the payload records.
 */
public final class RtsProgressionPackets {
    private RtsProgressionPackets() {
    }

    public static void register(ForgePayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsQuestDetectPayload.TYPE,
                C2SRtsQuestDetectPayload.STREAM_CODEC,
                RtsProgressionNetworkHandlers::handleQuestDetect);

        registrar.playToServer(
                C2SRtsUnlockProgressionNodePayload.TYPE,
                C2SRtsUnlockProgressionNodePayload.STREAM_CODEC,
                RtsProgressionNetworkHandlers::handleUnlockProgressionNode);

        registrar.playToServer(
                C2SRtsSetSurvivalProgressionPayload.TYPE,
                C2SRtsSetSurvivalProgressionPayload.STREAM_CODEC,
                RtsProgressionNetworkHandlers::handleSetSurvivalProgression);

        registrar.playToServer(
                C2SRtsSetProgressionCostPayload.TYPE,
                C2SRtsSetProgressionCostPayload.STREAM_CODEC,
                RtsProgressionNetworkHandlers::handleSetProgressionCost);

        registrar.playToServer(
                C2SRtsSetHomePayload.TYPE,
                C2SRtsSetHomePayload.STREAM_CODEC,
                RtsProgressionNetworkHandlers::handleSetHome);

        registrar.playToServer(
                C2SRtsBeginHomeSelectionPayload.TYPE,
                C2SRtsBeginHomeSelectionPayload.STREAM_CODEC,
                RtsProgressionNetworkHandlers::handleBeginHomeSelection);

        registrar.playToServer(
                C2SRtsRequestProgressionStatePayload.TYPE,
                C2SRtsRequestProgressionStatePayload.STREAM_CODEC,
                RtsProgressionNetworkHandlers::handleRequestProgressionState);

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
