package com.rtsbuilding.rtsbuilding.common.diagnostics;

import com.rtsbuilding.rtsbuilding.common.trace.RtsTraceIds;

/**
 * 网络入口到 Pipeline 的不可变诊断上下文。
 *
 * <p>只携带因果身份与时序，不携带目标坐标、库存或 NBT，也不得参与业务判断。</p>
 */
public final class RtsOperationTraceContext {
    private final long traceId;
    private final int sequence;
    private final long clientTick;
    private final int heldMs;
    private final RtsTraceInputKind inputKind;
    private final RtsMiningStopOrigin stopOrigin;
    private final String packet;
    private final long operationId;
    private final long receiveNanos;
    private final long receiveServerTick;
    private final String traceSource;

    public RtsOperationTraceContext(long traceId, int sequence, long clientTick, int heldMs,
            RtsTraceInputKind inputKind, RtsMiningStopOrigin stopOrigin, String packet,
            long operationId, long receiveNanos, long receiveServerTick, String traceSource) {
        this.traceId = traceId;
        this.sequence = Math.max(0, sequence);
        this.clientTick = clientTick;
        this.heldMs = Math.max(0, heldMs);
        this.inputKind = inputKind == null ? RtsTraceInputKind.UNKNOWN : inputKind;
        this.stopOrigin = stopOrigin == null ? RtsMiningStopOrigin.NONE : stopOrigin;
        this.packet = safeToken(packet, "UNKNOWN");
        this.operationId = operationId;
        this.receiveNanos = receiveNanos;
        this.receiveServerTick = receiveServerTick;
        this.traceSource = safeToken(traceSource,
                traceId == RtsTraceIds.NONE ? "LEGACY_MISSING" : "CLIENT");
    }

    public static RtsOperationTraceContext legacy(String packet) {
        return new RtsOperationTraceContext(RtsTraceIds.NONE, 0, -1L, 0,
                RtsTraceInputKind.UNKNOWN, RtsMiningStopOrigin.NONE, packet,
                -1L, System.nanoTime(), -1L, "LEGACY_MISSING");
    }

    public RtsOperationTraceContext withOperation(long nextOperationId, long serverTick) {
        return new RtsOperationTraceContext(traceId, sequence, clientTick, heldMs, inputKind,
                stopOrigin, packet, nextOperationId, receiveNanos, serverTick, traceSource);
    }

    public long traceId() { return traceId; }
    public int sequence() { return sequence; }
    public long clientTick() { return clientTick; }
    public int heldMs() { return heldMs; }
    public RtsTraceInputKind inputKind() { return inputKind; }
    public RtsMiningStopOrigin stopOrigin() { return stopOrigin; }
    public String packet() { return packet; }
    public long operationId() { return operationId; }
    public long receiveNanos() { return receiveNanos; }
    public long receiveServerTick() { return receiveServerTick; }
    public String traceSource() { return traceSource; }
    public boolean traced() { return traceId != RtsTraceIds.NONE; }

    private static String safeToken(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.replaceAll("[^A-Za-z0-9_.:-]", "_");
    }
}
