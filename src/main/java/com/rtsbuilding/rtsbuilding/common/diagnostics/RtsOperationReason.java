package com.rtsbuilding.rtsbuilding.common.diagnostics;

import java.util.Locale;

/**
 * 后台操作在任务、工作流、诊断和网络之间共享的稳定原因代码。
 * 数值一旦发布不得按枚举顺序复用；未知数值统一降级为 UNKNOWN，不能猜成缺材料。
 */
public enum RtsOperationReason {
    UNKNOWN(0), SUCCESS(1), SKIPPED(2), RESOURCE_MISSING(3), TOOL_MISSING(4),
    CHUNK_UNLOADED(5), CONFIG_DISABLED(6), PERMISSION_DENIED(7), EXECUTION_ERROR(8),
    MANUAL_PAUSED(9), QUEUE_FULL(10), CANCELLED(11), REPLACED(12);

    private final int wireId;

    RtsOperationReason(int wireId) { this.wireId = wireId; }
    public int wireId() { return wireId; }

    public static RtsOperationReason fromWireId(int wireId) {
        for (RtsOperationReason value : values()) if (value.wireId == wireId) return value;
        return UNKNOWN;
    }

    public String diagnosticId() { return name().toLowerCase(Locale.ROOT); }
}
