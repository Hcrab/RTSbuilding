package com.rtsbuilding.rtsbuilding.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 加载器无关的 RTS 数据包处理函数。 */
@FunctionalInterface
public interface RtsPayloadHandler<T extends CustomPacketPayload> {
    void handle(T payload, RtsPayloadContext context);
}
