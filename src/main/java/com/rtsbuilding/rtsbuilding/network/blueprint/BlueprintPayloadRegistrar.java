package com.rtsbuilding.rtsbuilding.network.blueprint;

import com.rtsbuilding.rtsbuilding.forgecompat.network.ForgePayloadRegistrar;

/**
 * 蓝图网络域的唯一注册入口。
 *
 * <p>业务协议与主线保持同一分层；这里只把 NeoForge 的 registrar 类型翻译成
 * Forge 1.20.1 的薄适配器，不在根注册器中复制蓝图处理逻辑。</p>
 */
public final class BlueprintPayloadRegistrar {
    private BlueprintPayloadRegistrar() {
    }

    public static void register(ForgePayloadRegistrar registrar) {
        registrar.playToServer(
                C2SBlueprintPlacePayload.TYPE,
                C2SBlueprintPlacePayload.STREAM_CODEC,
                BlueprintNetworkHandlers::handlePlace);

        registrar.playToClient(
                S2CBlueprintStatusPayload.TYPE,
                S2CBlueprintStatusPayload.STREAM_CODEC,
                BlueprintClientPayloadBridge::handleStatus);
    }
}
