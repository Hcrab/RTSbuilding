package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.network.RtsClientPayloadBridge;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers linked-storage browser, GUI binding, and overlay transfer packets.
 *
 * This class groups packet registration only; payload ids, codecs, and packet
 * directions stay in the payload records.
 */
public final class RtsStoragePackets {
    private RtsStoragePackets() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsSetFunnelPayload.TYPE,
                C2SRtsSetFunnelPayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleSetFunnel);

        registrar.playToServer(
                C2SRtsSetAutoStorePayload.TYPE,
                C2SRtsSetAutoStorePayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleSetAutoStore);

        registrar.playToServer(
                C2SRtsSetBdNetworkPayload.TYPE,
                C2SRtsSetBdNetworkPayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleSetBdNetwork);

        registrar.playToServer(
                C2SRtsLinkStoragePayload.TYPE,
                C2SRtsLinkStoragePayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleLinkStorage);

        registrar.playToServer(
                C2SRtsUnlinkStoragePayload.TYPE,
                C2SRtsUnlinkStoragePayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleUnlinkStorage);

        registrar.playToServer(
                C2SRtsUpdateLinkedStoragePayload.TYPE,
                C2SRtsUpdateLinkedStoragePayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleUpdateLinkedStorage);

        registrar.playToServer(
                C2SRtsStoreHotbarSlotPayload.TYPE,
                C2SRtsStoreHotbarSlotPayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleStoreHotbarSlot);

        registrar.playToServer(
                C2SRtsSetQuickSlotPayload.TYPE,
                C2SRtsSetQuickSlotPayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleSetQuickSlot);

        registrar.playToServer(
                C2SRtsSetGuiBindingPayload.TYPE,
                C2SRtsSetGuiBindingPayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleSetGuiBinding);

        registrar.playToServer(
                C2SRtsOpenGuiBindingPayload.TYPE,
                C2SRtsOpenGuiBindingPayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleOpenGuiBinding);

        registrar.playToServer(
                C2SRtsRequestStoragePagePayload.TYPE,
                C2SRtsRequestStoragePagePayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleRequestStoragePage);

        registrar.playToServer(
                C2SRtsFunnelTargetPayload.TYPE,
                C2SRtsFunnelTargetPayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleFunnelTarget);

        registrar.playToServer(
                C2SRtsFillInventoryPayload.TYPE,
                C2SRtsFillInventoryPayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleFillInventory);

        registrar.playToServer(
                C2SRtsLinkedPickupPayload.TYPE,
                C2SRtsLinkedPickupPayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleLinkedPickup);

        registrar.playToServer(
                C2SRtsLinkedQuickMovePayload.TYPE,
                C2SRtsLinkedQuickMovePayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleLinkedQuickMove);

        registrar.playToServer(
                C2SRtsReturnCarriedPayload.TYPE,
                C2SRtsReturnCarriedPayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleReturnCarried);

        registrar.playToServer(
                C2SRtsImportMenuSlotPayload.TYPE,
                C2SRtsImportMenuSlotPayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleImportMenuSlot);

        registrar.playToServer(
                C2SRtsCloseRemoteMenuPayload.TYPE,
                C2SRtsCloseRemoteMenuPayload.STREAM_CODEC,
                RtsStorageNetworkHandlers::handleCloseRemoteMenu);

        registrar.playToClient(
                S2CRtsStoragePagePayload.TYPE,
                S2CRtsStoragePagePayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleStoragePage);

        registrar.playToClient(
                S2CRtsStorageDirtyPayload.TYPE,
                S2CRtsStorageDirtyPayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleStorageDirty);

        registrar.playToClient(
                S2CRtsRemoteMenuHintPayload.TYPE,
                S2CRtsRemoteMenuHintPayload.STREAM_CODEC,
                RtsClientPayloadBridge::handleRemoteMenuHint);
    }
}
