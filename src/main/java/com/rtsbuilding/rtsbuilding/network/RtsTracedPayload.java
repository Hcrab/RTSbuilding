package com.rtsbuilding.rtsbuilding.network;

/** 带有客户端因果身份的网络消息；零表示服务端主动推送或历史兼容路径。 */
public interface RtsTracedPayload {
    long traceId();
}
