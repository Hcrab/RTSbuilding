package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;

import com.rtsbuilding.rtsbuilding.forgecompat.network.ForgePayloadRegistrar;

/**
 * Registers RTS crafting terminal, craftable-list, and JEI transfer packets.
 *
 * This class groups packet registration only; payload ids, codecs, and packet
 * directions stay in the payload records.
 */
public final class RtsCraftPackets {
    private RtsCraftPackets() {
    }

    public static void register(ForgePayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsRequestCraftablesPayload.TYPE,
                C2SRtsRequestCraftablesPayload.STREAM_CODEC,
                RtsCraftNetworkHandlers::handleRequestCraftables);

        registrar.playToServer(
                C2SRtsOpenCraftTerminalPayload.TYPE,
                C2SRtsOpenCraftTerminalPayload.STREAM_CODEC,
                RtsCraftNetworkHandlers::handleOpenCraftTerminal);

        registrar.playToServer(
                C2SRtsCraftRefillPayload.TYPE,
                C2SRtsCraftRefillPayload.STREAM_CODEC,
                RtsCraftNetworkHandlers::handleCraftRefill);

        registrar.playToServer(
                C2SRtsCraftRecipePayload.TYPE,
                C2SRtsCraftRecipePayload.STREAM_CODEC,
                RtsCraftNetworkHandlers::handleCraftRecipe);

        registrar.playToServer(
                C2SRtsJeiTransferPayload.TYPE,
                C2SRtsJeiTransferPayload.STREAM_CODEC,
                RtsCraftNetworkHandlers::handleJeiTransfer);

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
