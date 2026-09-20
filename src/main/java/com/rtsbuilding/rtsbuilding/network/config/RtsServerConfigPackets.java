package com.rtsbuilding.rtsbuilding.network.config;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import com.rtsbuilding.rtsbuilding.server.network.RtsServerConfigNetwork;
import com.rtsbuilding.rtsbuilding.forgecompat.network.ForgePayloadRegistrar;

/** Forge 配置专用 payload 注册。 */
public final class RtsServerConfigPackets {
    private RtsServerConfigPackets() {}
    public static void register(ForgePayloadRegistrar registrar) {
        registrar.playToServer(C2SRtsServerConfigQueryPayload.TYPE, C2SRtsServerConfigQueryPayload.STREAM_CODEC, RtsServerConfigNetwork::handleQuery);
        registrar.playToServer(C2SRtsServerConfigUpdatePayload.TYPE, C2SRtsServerConfigUpdatePayload.STREAM_CODEC, RtsServerConfigNetwork::handleUpdate);
        registrar.playToClient(S2CRtsServerConfigResultPayload.TYPE, S2CRtsServerConfigResultPayload.STREAM_CODEC, ClientPayloadDispatcher::dispatchServerConfig);
    }
}
