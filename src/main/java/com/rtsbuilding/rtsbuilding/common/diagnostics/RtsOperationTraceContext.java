package com.rtsbuilding.rtsbuilding.common.diagnostics;

/**
 * 网络入口到 Pipeline 的不可变诊断上下文。
 *
 * <p>字段只描述请求来源和接收时间，不包含物品 NBT、库存、完整目标坐标或任何业务开关。</p>
 */
public record RtsOperationTraceContext(
        long traceId,
        int sequence,
        long clientTick,
        int heldMs,
        RtsTraceInputKind inputKind,
        RtsMiningStopOrigin stopOrigin,
        String packet,
        long operationId,
        long receiveNanos,
        long receiveServerTick,
        String traceSource) {

    public RtsOperationTraceContext {
        heldMs = Math.max(0, heldMs);
        inputKind = inputKind == null ? RtsTraceInputKind.UNKNOWN : inputKind;
        stopOrigin = stopOrigin == null ? RtsMiningStopOrigin.NONE : stopOrigin;
        packet = safeToken(packet, "UNKNOWN");
        traceSource = safeToken(traceSource, traceId == RtsTraceIds.NONE ? "LEGACY_MISSING" : "CLIENT");
    }

    public static RtsOperationTraceContext legacy(String packet) {
        return new RtsOperationTraceContext(
                RtsTraceIds.NONE, 0, -1L, 0,
                RtsTraceInputKind.UNKNOWN, RtsMiningStopOrigin.NONE,
                packet, -1L, System.nanoTime(), -1L, "LEGACY_MISSING");
    }

    public RtsOperationTraceContext withOperation(long nextOperationId, long serverTick) {
        return new RtsOperationTraceContext(
                traceId, sequence, clientTick, heldMs, inputKind, stopOrigin,
                packet, nextOperationId, receiveNanos, serverTick, traceSource);
    }

    public boolean traced() {
        return traceId != RtsTraceIds.NONE;
    }

    private static String safeToken(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.replaceAll("[^A-Za-z0-9_.:-]", "_");
    }
}
