package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.network.RtsClientPayloadBridge;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkHandlers;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RtsStoragePackets {
    private RtsStoragePackets() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsSetFunnelPayload.TYPE,
                C2SRtsSetFunnelPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetFunnel);

        registrar.playToServer(
                C2SRtsSetAutoStorePayload.TYPE,
                C2SRtsSetAutoStorePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetAutoStore);

        registrar.playToServer(
                C2SRtsSetBdNetworkPayload.TYPE,
                C2SRtsSetBdNetworkPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetBdNetwork);

        registrar.playToServer(
                C2SRtsLinkStoragePayload.TYPE,
                C2SRtsLinkStoragePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleLinkStorage);

        registrar.playToServer(
                C2SRtsStoreHotbarSlotPayload.TYPE,
                C2SRtsStoreHotbarSlotPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleStoreHotbarSlot);

        registrar.playToServer(
                C2SRtsSetQuickSlotPayload.TYPE,
                C2SRtsSetQuickSlotPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetQuickSlot);

        registrar.playToServer(
                C2SRtsSetGuiBindingPayload.TYPE,
                C2SRtsSetGuiBindingPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleSetGuiBinding);

        registrar.playToServer(
                C2SRtsOpenGuiBindingPayload.TYPE,
                C2SRtsOpenGuiBindingPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleOpenGuiBinding);

        registrar.playToServer(
                C2SRtsRequestStoragePagePayload.TYPE,
                C2SRtsRequestStoragePagePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleRequestStoragePage);

        registrar.playToServer(
                C2SRtsFunnelTargetPayload.TYPE,
                C2SRtsFunnelTargetPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleFunnelTarget);

        registrar.playToServer(
                C2SRtsFillInventoryPayload.TYPE,
                C2SRtsFillInventoryPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleFillInventory);

        registrar.playToServer(
                C2SRtsLinkedPickupPayload.TYPE,
                C2SRtsLinkedPickupPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleLinkedPickup);

        registrar.playToServer(
                C2SRtsLinkedQuickMovePayload.TYPE,
                C2SRtsLinkedQuickMovePayload.STREAM_CODEC,
                RtsNetworkHandlers::handleLinkedQuickMove);

        registrar.playToServer(
                C2SRtsReturnCarriedPayload.TYPE,
                C2SRtsReturnCarriedPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleReturnCarried);

        registrar.playToServer(
                C2SRtsImportMenuSlotPayload.TYPE,
                C2SRtsImportMenuSlotPayload.STREAM_CODEC,
                RtsNetworkHandlers::handleImportMenuSlot);

        registrar.playToClient(
                S2CRtsStoragePagePayload.TYPE,
                S2CRtsStoragePagePayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleStoragePage);

        registrar.playToClient(
                S2CRtsRemoteMenuHintPayload.TYPE,
                S2CRtsRemoteMenuHintPayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleRemoteMenuHint);
    }
}
