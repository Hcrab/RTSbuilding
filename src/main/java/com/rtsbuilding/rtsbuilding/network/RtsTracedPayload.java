package com.rtsbuilding.rtsbuilding.network;

/** 带客户端因果身份和同一意图内顺序号的网络负载公共契约。 */
public interface RtsTracedPayload {
    long traceId();

    int sequence();
}
