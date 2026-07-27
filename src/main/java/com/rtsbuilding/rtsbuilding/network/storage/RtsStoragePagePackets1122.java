package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.storage.handler.RtsStoragePageHandlers1122;
import net.minecraftforge.fml.relauncher.Side;

/** 储存页面请求/快照闭环的固定 discriminator 表。 */
public final class RtsStoragePagePackets1122 {
    private RtsStoragePagePackets1122(){}
    public static void register(){
        RtsPayloadRegistrar.registerMessage(134,RtsStoragePageHandlers1122.Request.class,
                C2SRtsRequestStoragePagePayload.class,Side.SERVER);
        RtsPayloadRegistrar.registerMessage(135,ClientPayloadDispatcher.StoragePageHandler.class,
                S2CRtsStoragePagePayload.class,Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(136,ClientPayloadDispatcher.StorageDirtyHandler.class,
                S2CRtsStorageDirtyPayload.class,Side.CLIENT);
        RtsPayloadRegistrar.registerMessage(137,ClientPayloadDispatcher.RemoteMenuHintHandler.class,
                S2CRtsRemoteMenuHintPayload.class,Side.CLIENT);
    }
}
