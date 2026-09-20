package com.rtsbuilding.rtsbuilding.common.config;

/**
 * 客户端配置同步的最近保存 ACK 邮箱：查询响应和广播不能覆盖精确的 update ACK。
 * 不保留无限历史，断开或换服时由客户端网络门面清空。
 */
public final class RtsServerConfigAckStore {
    private RtsServerConfigUpdateResult result;
    private long sessionId;
    private int requestId;

    /** 只接受当前会话最近发出的保存请求，查询/广播及旧会话响应都会被忽略。 */
    public synchronized void accept(RtsServerConfigWire.Response response,
            long activeSessionId, int latestIssuedUpdateRequestId) {
        if (response == null || response.result() == null || activeSessionId == 0L
                || response.sessionId() != activeSessionId || response.requestId() == 0
                || response.requestId() != latestIssuedUpdateRequestId) return;
        result = response.result(); sessionId = response.sessionId(); requestId = response.requestId();
    }

    /** 清空当前连接的保存结果；不得把上一台服务器的 ACK 带入新会话。 */
    public synchronized void clear() { result = null; sessionId = 0L; requestId = 0; }
    public synchronized RtsServerConfigUpdateResult result() { return result; }
    public synchronized long sessionId() { return sessionId; }
    public synchronized int requestId() { return requestId; }
}
