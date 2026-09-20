package com.rtsbuilding.rtsbuilding.network.config;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import com.rtsbuilding.rtsbuilding.server.network.RtsServerConfigNetwork;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 配置专用 payload 注册；业务权限和保存顺序由服务端网络适配器拥有。 */
public final class RtsServerConfigPackets {
    private RtsServerConfigPackets() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(C2SRtsServerConfigQueryPayload.TYPE,
                C2SRtsServerConfigQueryPayload.STREAM_CODEC, RtsServerConfigNetwork::handleQuery);
        registrar.playToServer(C2SRtsServerConfigUpdatePayload.TYPE,
                C2SRtsServerConfigUpdatePayload.STREAM_CODEC, RtsServerConfigNetwork::handleUpdate);
        registrar.playToClient(S2CRtsServerConfigResultPayload.TYPE,
                S2CRtsServerConfigResultPayload.STREAM_CODEC, ClientPayloadDispatcher::dispatchServerConfig);
    }
}
