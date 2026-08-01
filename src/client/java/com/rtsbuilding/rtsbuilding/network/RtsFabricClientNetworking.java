package com.rtsbuilding.rtsbuilding.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 在客户端环境安装全部 S2C 接收器和安全分发桥。 */
public final class RtsFabricClientNetworking {
    private RtsFabricClientNetworking() {
    }

    public static void register() {
        ClientPayloadDispatcher.install(FabricClientPayloadSink::dispatch);
        for (RtsPayloadRegistrar.ClientRegistration<?> registration
                : RtsPayloadRegistrar.clientRegistrations()) {
            registerReceiver(registration);
        }
    }

    private static <T extends CustomPacketPayload> void registerReceiver(
            RtsPayloadRegistrar.ClientRegistration<T> registration) {
        ClientPlayNetworking.registerGlobalReceiver(registration.type(),
                (payload, context) -> registration.handler().handle(
                        payload, new FabricClientPayloadContext(context)));
    }
}
