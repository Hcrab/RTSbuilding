package com.rtsbuilding.rtsbuilding.common.config;

/**
 * 客户端配置同步的最近保存 ACK 邮箱。
 *
 * <p>查询响应和 requestId=0 的运行期广播只更新 typed current 镜像，不能覆盖设置页
 * 正在等待的保存结果。这里不保存无限历史，只保留当前连接最近一次、且身份精确匹配
 * 的 update ACK；连接切换时由客户端网络门面清空。</p>
 */
public final class RtsServerConfigAckStore {
    private RtsServerConfigUpdateResult result;
    private long sessionId;
    private int requestId;

    /** 只接受当前会话最近发出的保存请求，查询/广播及旧会话响应都会被忽略。 */
    public synchronized void accept(RtsServerConfigWire.Response response,
            long activeSessionId, int latestIssuedUpdateRequestId) {
        if (response == null || response.result() == null || activeSessionId == 0L
                || response.sessionId() != activeSessionId
                || response.requestId() == 0
                || response.requestId() != latestIssuedUpdateRequestId) {
            return;
        }
        result = response.result();
        sessionId = response.sessionId();
        requestId = response.requestId();
    }

    /** 清空当前连接的保存结果；不得把上一台服务器的 ACK 带入新会话。 */
    public synchronized void clear() {
        result = null;
        sessionId = 0L;
        requestId = 0;
    }

    public synchronized RtsServerConfigUpdateResult result() {
        return result;
    }

    public synchronized long sessionId() {
        return sessionId;
    }

    public synchronized int requestId() {
        return requestId;
    }
}
