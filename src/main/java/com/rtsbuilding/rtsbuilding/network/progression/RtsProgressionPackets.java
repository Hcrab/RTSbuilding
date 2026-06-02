package com.rtsbuilding.rtsbuilding.network.progression;

import com.rtsbuilding.rtsbuilding.network.RtsClientPayloadBridge;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkHandlers;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RtsProgressionPackets {
    private RtsProgressionPackets() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsQuestDetectPayload.TYPE,
                C2SRtsQuestDetectPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleQuestDetect);

        registrar.playToServer(
                C2SRtsUnlockProgressionNodePayload.TYPE,
                C2SRtsUnlockProgressionNodePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleUnlockProgressionNode);

        registrar.playToServer(
                C2SRtsSetSurvivalProgressionPayload.TYPE,
                C2SRtsSetSurvivalProgressionPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetSurvivalProgression);

        registrar.playToServer(
                C2SRtsSetProgressionCostPayload.TYPE,
                C2SRtsSetProgressionCostPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetProgressionCost);

        registrar.playToServer(
                C2SRtsSetHomePayload.TYPE,
                C2SRtsSetHomePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetHome);

        registrar.playToServer(
                C2SRtsBeginHomeSelectionPayload.TYPE,
                C2SRtsBeginHomeSelectionPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleBeginHomeSelection);

        registrar.playToServer(
                C2SRtsRequestProgressionStatePayload.TYPE,
                C2SRtsRequestProgressionStatePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleRequestProgressionState);

        registrar.playToClient(
                S2CRtsQuestDetectStatusPayload.TYPE,
                S2CRtsQuestDetectStatusPayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleQuestDetectStatus);

        registrar.playToClient(
                S2CRtsProgressionStatePayload.TYPE,
                S2CRtsProgressionStatePayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleProgressionState);
    }
}
