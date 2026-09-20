package com.rtsbuilding.rtsbuilding.common.config;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 客户端配置页的会话与旧响应过滤状态机。 */
public final class RtsServerConfigClientSession {
    private long sessionId;
    private int nextRequestId;
    private int latestRequestId;
    private int latestUpdateRequestId;
    private RtsServerConfigView current = RtsServerConfigView.defaults().readOnly();
    public synchronized long begin() { long value = ThreadLocalRandom.current().nextLong(); sessionId = value == 0L ? 1L : value; nextRequestId = 0; latestRequestId = 0; latestUpdateRequestId = 0; current = RtsServerConfigView.defaults().readOnly(); return sessionId; }
    public synchronized void begin(long value) { sessionId = value == 0L ? 1L : value; nextRequestId = 0; latestRequestId = 0; latestUpdateRequestId = 0; current = RtsServerConfigView.defaults().readOnly(); }
    public synchronized void clear() { sessionId = 0L; nextRequestId = 0; latestRequestId = 0; latestUpdateRequestId = 0; current = RtsServerConfigView.defaults().readOnly(); }
    public synchronized long sessionId() { return sessionId; }
    public synchronized RtsServerConfigView current() { return current; }
    public synchronized byte[] query() { return RtsServerConfigWire.encodeQuery(sessionId, nextRequest(false)); }
    public synchronized byte[] update(List<RtsServerConfigChange> changes) { return update(current.revision(), changes); }
    /** 使用打开页面时保存的 revision 发起更新，避免广播悄悄替换冲突基准。 */
    public synchronized byte[] update(int expectedRevision, List<RtsServerConfigChange> changes) { if (sessionId == 0L) throw new IllegalStateException("configuration session is not connected"); return RtsServerConfigWire.encodeUpdate(sessionId, nextRequest(true), new RtsServerConfigUpdateRequest(expectedRevision, changes)); }
    /** 最近一次发出的请求号，供 UI 精确对应保存 ACK。 */
    public synchronized int lastIssuedRequestId() { return nextRequestId; }
    /** 最近一次发出的保存请求号；查询和广播不能冒充这个 ACK。 */
    public synchronized int lastIssuedUpdateRequestId() { return latestUpdateRequestId; }
    public synchronized boolean accept(byte[] encoded) { return accept(RtsServerConfigWire.decodeResponse(encoded)); }
    public synchronized boolean accept(RtsServerConfigWire.Response response) { if (response == null || sessionId == 0L || response.sessionId() != sessionId) return false; if (response.requestId() != 0 && response.requestId() < latestRequestId && response.requestId() != latestUpdateRequestId) return false; boolean staleRevision = response.result().view().revision() < current.revision(); if (staleRevision && response.requestId() != latestRequestId && response.requestId() != latestUpdateRequestId) return false; latestRequestId = Math.max(latestRequestId, response.requestId()); if (!staleRevision) current = response.result().view(); return true; }
    private int nextRequest(boolean update) { if (nextRequestId == Integer.MAX_VALUE) throw new IllegalStateException("configuration request id exhausted"); latestRequestId = ++nextRequestId; if (update) latestUpdateRequestId = latestRequestId; return latestRequestId; }
}
