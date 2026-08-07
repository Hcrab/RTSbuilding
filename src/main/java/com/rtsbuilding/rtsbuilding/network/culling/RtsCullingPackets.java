package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.network.ClientPayloadDispatcher;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 注册范围剔除状态的请求、保存与恢复数据包。 */
public final class RtsCullingPackets {
    private RtsCullingPackets() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                C2SRtsRequestCullingStatePayload.TYPE,
                C2SRtsRequestCullingStatePayload.STREAM_CODEC,
                RtsCullingNetworkHandlers::handleRequest);
        registrar.playToServer(
                C2SRtsSaveCullingStatePayload.TYPE,
                C2SRtsSaveCullingStatePayload.STREAM_CODEC,
                RtsCullingNetworkHandlers::handleSave);
        registrar.playToClient(
                S2CRtsCullingStatePayload.TYPE,
                S2CRtsCullingStatePayload.STREAM_CODEC,
                ClientPayloadDispatcher::dispatchCulling);
    }
}
