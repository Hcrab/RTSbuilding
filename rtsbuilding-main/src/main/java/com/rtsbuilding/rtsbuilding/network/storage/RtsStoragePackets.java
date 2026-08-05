package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
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

        registrar.playToClient(
                S2CRtsCarriedSyncPayload.TYPE,
                S2CRtsCarriedSyncPayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchStorage);
    }
}
