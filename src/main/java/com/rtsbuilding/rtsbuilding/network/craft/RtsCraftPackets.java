package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.network.RtsClientPayloadBridge;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkHandlers;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RtsCraftPackets {
    private RtsCraftPackets() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsRequestCraftablesPayload.TYPE,
                C2SRtsRequestCraftablesPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleRequestCraftables);

        registrar.playToServer(
                C2SRtsOpenCraftTerminalPayload.TYPE,
                C2SRtsOpenCraftTerminalPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleOpenCraftTerminal);

        registrar.playToServer(
                C2SRtsCraftRefillPayload.TYPE,
                C2SRtsCraftRefillPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleCraftRefill);

        registrar.playToServer(
                C2SRtsCraftRecipePayload.TYPE,
                C2SRtsCraftRecipePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleCraftRecipe);

        registrar.playToServer(
                C2SRtsJeiTransferPayload.TYPE,
                C2SRtsJeiTransferPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleJeiTransfer);

        registrar.playToClient(
                S2CRtsCraftablesPayload.TYPE,
                S2CRtsCraftablesPayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleCraftables);

        registrar.playToClient(
                S2CRtsCraftFeedbackPayload.TYPE,
                S2CRtsCraftFeedbackPayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleCraftFeedback);
    }
}
