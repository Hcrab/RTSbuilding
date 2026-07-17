package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import com.rtsbuilding.rtsbuilding.network.storage.handler.RtsBindingHandlers;
import com.rtsbuilding.rtsbuilding.network.storage.handler.RtsPageHandlers;
import com.rtsbuilding.rtsbuilding.network.storage.handler.RtsTransferHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers linked-storage browser, GUI binding, and overlay transfer packets.
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
                RtsBindingHandlers::handleSetFunnel);

        registrar.playToServer(
                C2SRtsSetAutoStorePayload.TYPE,
                C2SRtsSetAutoStorePayload.STREAM_CODEC,
                RtsBindingHandlers::handleSetAutoStore);

        registrar.playToServer(
                C2SRtsSetBdNetworkPayload.TYPE,
                C2SRtsSetBdNetworkPayload.STREAM_CODEC,
                RtsBindingHandlers::handleSetBdNetwork);

        registrar.playToServer(
                C2SRtsLinkStoragePayload.TYPE,
                C2SRtsLinkStoragePayload.STREAM_CODEC,
                RtsBindingHandlers::handleLinkStorage);

        registrar.playToServer(
                C2SRtsUnlinkStoragePayload.TYPE,
                C2SRtsUnlinkStoragePayload.STREAM_CODEC,
                RtsBindingHandlers::handleUnlinkStorage);

        registrar.playToServer(
                C2SRtsUpdateLinkedStoragePayload.TYPE,
                C2SRtsUpdateLinkedStoragePayload.STREAM_CODEC,
                RtsBindingHandlers::handleUpdateLinkedStorage);

        registrar.playToServer(
                C2SRtsStoreHotbarSlotPayload.TYPE,
                C2SRtsStoreHotbarSlotPayload.STREAM_CODEC,
                RtsBindingHandlers::handleStoreHotbarSlot);

        registrar.playToServer(
                C2SRtsSetQuickSlotPayload.TYPE,
                C2SRtsSetQuickSlotPayload.STREAM_CODEC,
                RtsBindingHandlers::handleSetQuickSlot);

        registrar.playToServer(
                C2SRtsSetGuiBindingPayload.TYPE,
                C2SRtsSetGuiBindingPayload.STREAM_CODEC,
                RtsBindingHandlers::handleSetGuiBinding);

        registrar.playToServer(
                C2SRtsOpenGuiBindingPayload.TYPE,
                C2SRtsOpenGuiBindingPayload.STREAM_CODEC,
                RtsBindingHandlers::handleOpenGuiBinding);

        registrar.playToServer(
                C2SRtsRequestStoragePagePayload.TYPE,
                C2SRtsRequestStoragePagePayload.STREAM_CODEC,
                RtsPageHandlers::handleRequestStoragePage);

        registrar.playToServer(
                C2SRtsFunnelTargetPayload.TYPE,
                C2SRtsFunnelTargetPayload.STREAM_CODEC,
                RtsBindingHandlers::handleFunnelTarget);

        registrar.playToServer(
                C2SRtsFillInventoryPayload.TYPE,
                C2SRtsFillInventoryPayload.STREAM_CODEC,
                RtsTransferHandlers::handleFillInventory);

        registrar.playToServer(
                C2SRtsLinkedPickupPayload.TYPE,
                C2SRtsLinkedPickupPayload.STREAM_CODEC,
                RtsTransferHandlers::handleLinkedPickup);

        registrar.playToServer(
                C2SRtsLinkedQuickMovePayload.TYPE,
                C2SRtsLinkedQuickMovePayload.STREAM_CODEC,
                RtsTransferHandlers::handleLinkedQuickMove);

        registrar.playToServer(
                C2SRtsReturnCarriedPayload.TYPE,
                C2SRtsReturnCarriedPayload.STREAM_CODEC,
                RtsTransferHandlers::handleReturnCarried);

        registrar.playToServer(
                C2SRtsImportMenuSlotPayload.TYPE,
                C2SRtsImportMenuSlotPayload.STREAM_CODEC,
                RtsTransferHandlers::handleImportMenuSlot);

        registrar.playToServer(
                C2SRtsBulkStorageOpPayload.TYPE,
                C2SRtsBulkStorageOpPayload.STREAM_CODEC,
                RtsTransferHandlers::handleBulkStorageOp);

        registrar.playToServer(
                C2SRtsCloseRemoteMenuPayload.TYPE,
                C2SRtsCloseRemoteMenuPayload.STREAM_CODEC,
                RtsBindingHandlers::handleCloseRemoteMenu);

        registrar.playToClient(
                S2CRtsStoragePagePayload.TYPE,
                S2CRtsStoragePagePayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchStorage);

        registrar.playToClient(
                S2CRtsStorageDirtyPayload.TYPE,
                S2CRtsStorageDirtyPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchStorage);

        registrar.playToClient(
                S2CRtsRemoteMenuHintPayload.TYPE,
                S2CRtsRemoteMenuHintPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchStorage);
    }
}
