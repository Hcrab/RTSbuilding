package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsClientPayloadBridge;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkHandlers;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RtsBuilderPackets {
    private RtsBuilderPackets() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsSetModePayload.TYPE,
                C2SRtsSetModePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetMode);

        registrar.playToServer(
                C2SRtsRotateBlockPayload.TYPE,
                C2SRtsRotateBlockPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleRotateBlock);

        registrar.playToServer(
                C2SRtsPlacePayload.TYPE,
                C2SRtsPlacePayload.STREAM_CODEC,
                RtsNetworkHandlers::handlePlace);

        registrar.playToServer(
                C2SRtsPlaceBatchPayload.TYPE,
                C2SRtsPlaceBatchPayload.STREAM_CODEC,
                RtsNetworkHandlers::handlePlaceBatch);

        registrar.playToServer(
                C2SRtsPlaceFluidPayload.TYPE,
                C2SRtsPlaceFluidPayload.STREAM_CODEC,
                RtsNetworkHandlers::handlePlaceFluid);

        registrar.playToServer(
                C2SRtsStoreFluidPayload.TYPE,
                C2SRtsStoreFluidPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleStoreFluid);

        registrar.playToServer(
                C2SRtsInteractPayload.TYPE,
                C2SRtsInteractPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleInteract);

        registrar.playToServer(
                C2SRtsQuickDropPayload.TYPE,
                C2SRtsQuickDropPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleQuickDrop);

        registrar.playToServer(
                C2SRtsBreakPayload.TYPE,
                C2SRtsBreakPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleBreak);

        registrar.playToServer(
                C2SRtsMinePayload.TYPE,
                C2SRtsMinePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleMine);

        registrar.playToServer(
                C2SRtsUltiminePayload.TYPE,
                C2SRtsUltiminePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleUltimine);

        registrar.playToClient(
                S2CRtsMineProgressPayload.TYPE,
                S2CRtsMineProgressPayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleMineProgress);
    }
}
