package com.rtsbuilding.rtsbuilding.common.config;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 客户端设置页使用的会话状态机。
 *
 * <p>它只接受当前连接的 session 和单调请求号，服务器广播使用 requestId=0；旧服、
 * 旧世界或旧页面返回的数据不会覆盖新连接的权威镜像。</p>
 */
public final class RtsServerConfigClientSession {
    private long sessionId;
    private int nextRequestId;
    private int latestRequestId;
    private int latestUpdateRequestId;
    private RtsServerConfigView current = RtsServerConfigView.defaults().readOnly();

    public synchronized long begin() {
        long candidate = ThreadLocalRandom.current().nextLong();
        sessionId = candidate == 0L ? 1L : candidate;
        nextRequestId = 0;
        latestRequestId = 0;
        latestUpdateRequestId = 0;
        current = RtsServerConfigView.defaults().readOnly();
        return sessionId;
    }

    public synchronized void begin(long sessionId) {
        this.sessionId = sessionId == 0L ? 1L : sessionId;
        nextRequestId = 0;
        latestRequestId = 0;
        latestUpdateRequestId = 0;
        current = RtsServerConfigView.defaults().readOnly();
    }

    public synchronized void clear() {
        sessionId = 0L;
        nextRequestId = 0;
        latestRequestId = 0;
        latestUpdateRequestId = 0;
        current = RtsServerConfigView.defaults().readOnly();
    }

    public synchronized long sessionId() {
        return sessionId;
    }

    public synchronized RtsServerConfigView current() {
        return current;
    }

    public synchronized byte[] query() {
        return RtsServerConfigWire.encodeQuery(sessionId, nextRequest(false));
    }

    public synchronized byte[] update(List<RtsServerConfigChange> changes) {
        return update(current.revision(), changes);
    }

    /**
     * 使用设置页打开时保存下来的 revision 发起更新。
     *
     * <p>current 可能已经被广播刷新；这里不能悄悄替换为最新 revision，否则编辑页
     * 会绕过冲突检测覆盖别的管理员刚保存的值。</p>
     */
    public synchronized byte[] update(int expectedRevision, List<RtsServerConfigChange> changes) {
        if (sessionId == 0L) {
            throw new IllegalStateException("configuration session is not connected");
        }
        return RtsServerConfigWire.encodeUpdate(sessionId, nextRequest(true),
                new RtsServerConfigUpdateRequest(expectedRevision, changes));
    }

    /** 最近一次发出的请求号，供 UI 将 ACK 精确对应到本次保存。 */
    public synchronized int lastIssuedRequestId() {
        return nextRequestId;
    }

    /** 最近一次发出的保存请求号；查询和广播不能冒充这个 ACK。 */
    public synchronized int lastIssuedUpdateRequestId() {
        return latestUpdateRequestId;
    }

    public synchronized boolean accept(byte[] encodedResponse) {
        return accept(RtsServerConfigWire.decodeResponse(encodedResponse));
    }

    public synchronized boolean accept(RtsServerConfigWire.Response response) {
        if (response == null || sessionId == 0L || response.sessionId() != sessionId) {
            return false;
        }
        // 查询可能在保存 ACK 在途时被动发出；它不能让精确的保存 ACK 变成“旧请求”。
        if (response.requestId() != 0 && response.requestId() < latestRequestId
                && response.requestId() != latestUpdateRequestId) {
            return false;
        }
        boolean staleRevision = response.result().view().revision() < current.revision();
        // 当前最新请求的 ACK 可能晚于更高 revision 的广播到达：记录 ACK 身份，
        // 但不能让它把 current 镜像回退；更旧的请求和广播仍然直接拒绝。
        if (staleRevision && response.requestId() != latestRequestId
                && response.requestId() != latestUpdateRequestId) {
            return false;
        }
        latestRequestId = Math.max(latestRequestId, response.requestId());
        if (!staleRevision) current = response.result().view();
        return true;
    }

    private int nextRequest(boolean update) {
        if (nextRequestId == Integer.MAX_VALUE) {
            throw new IllegalStateException("configuration request id exhausted");
        }
        nextRequestId++;
        // 把“已发出”的请求也纳入顺序判断；旧查询不能覆盖当前连接。
        latestRequestId = nextRequestId;
        if (update) {
            latestUpdateRequestId = nextRequestId;
        }
        return nextRequestId;
    }
}
