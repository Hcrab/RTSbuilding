package com.rtsbuilding.rtsbuilding.client.diagnostic;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsDiagnosticLevel;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsStructuredDiagnostics;
import com.rtsbuilding.rtsbuilding.common.trace.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsOperationTerminalPayload;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端 traced 操作的有界生命周期表。
 *
 * <p>它只生成因果身份、序号与终态诊断，不控制按键、界面或操作是否继续。</p>
 */
public final class RtsClientOperationDiagnostics {
    private static final int MAX_ACTIVE = 128;
    private static final LinkedHashMap<Long, State> ACTIVE = new LinkedHashMap<Long, State>();

    private RtsClientOperationDiagnostics() {}

    public static synchronized long begin(String operation) {
        long traceId = RtsTraceIds.nextClientTraceId();
        registerExisting(traceId, operation);
        return traceId;
    }

    /** 让已有的远程 GUI trace 进入同一张终态回执表，避免生成第二个身份。 */
    public static synchronized void registerExisting(long traceId, String operation) {
        if (!RtsTraceIds.isPresent(traceId) || ACTIVE.containsKey(traceId)) return;
        long tick = clientTick();
        ACTIVE.put(traceId, new State(operation, System.nanoTime(), 1));
        trim();
        append("INPUT_PRESS", traceId, 0, operation, "client_tick", tick,
                "outcome", "SENT", "stage", "INPUT_PRESS");
    }

    /** INPUT_PRESS 固定为 seq=0，后续同一 trace 的包从 1 单调递增。 */
    public static synchronized int nextSequence(long traceId, String stage) {
        State state = ACTIVE.get(traceId);
        if (state == null) return 0;
        int value = state.nextSequence;
        if (state.nextSequence < Integer.MAX_VALUE) state.nextSequence++;
        append("INPUT_BOUNDARY", traceId, value, state.operation,
                "client_tick", clientTick(), "stage", stage, "outcome", "SENT");
        return value;
    }

    /** 由 S2C 聚合终态回执结束 trace，留下服务端真实 workflow/task 身份。 */
    public static synchronized void terminal(S2CRtsOperationTerminalPayload payload) {
        if (payload == null) return;
        State state = ACTIVE.remove(payload.traceId());
        String operation = state == null ? "RECOVERED" : state.operation;
        long elapsedMs = state == null ? -1L
                : Math.max(0L, (System.nanoTime() - state.startedNanos) / 1_000_000L);
        append("TERMINAL", payload.traceId(), payload.sequence(), operation,
                "workflow", payload.workflowId(), "task", payload.taskId(),
                "outcome", payload.outcome(), "reason", payload.reason(),
                "completed", payload.completed(), "failed", payload.failed(),
                "server_tick", payload.serverTick(), "ever_executed", payload.everExecuted(),
                "first_slice_wait_ticks", payload.firstSliceWaitTicks(), "elapsed_ms", elapsedMs);
    }

    private static void append(String event, long traceId, int sequence, String operation,
            Object... fields) {
        if (level() == RtsDiagnosticLevel.OFF) return;
        Object[] values = new Object[8 + (fields == null ? 0 : fields.length)];
        Object[] base = {"trace", RtsTraceIds.format(traceId), "seq", sequence,
                "op", -1, "kind", operation == null ? "UNKNOWN" : operation};
        System.arraycopy(base, 0, values, 0, base.length);
        if (fields != null) System.arraycopy(fields, 0, values, base.length, fields.length);
        RtsStructuredDiagnostics.appendClient(event, values);
    }

    private static long clientTick() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null || minecraft.world == null ? -1L : minecraft.world.getTotalWorldTime();
    }

    private static RtsDiagnosticLevel level() {
        try { return Config.CLIENT_DIAGNOSTIC_LEVEL.get(); }
        catch (IllegalStateException ignored) { return RtsDiagnosticLevel.BASIC; }
    }

    private static void trim() {
        while (ACTIVE.size() > MAX_ACTIVE) {
            Map.Entry<Long, State> oldest = ACTIVE.entrySet().iterator().next();
            ACTIVE.remove(oldest.getKey());
        }
    }

    private static final class State {
        private final String operation;
        private final long startedNanos;
        private int nextSequence;

        private State(String operation, long startedNanos, int nextSequence) {
            this.operation = operation == null ? "UNKNOWN" : operation;
            this.startedNanos = startedNanos;
            this.nextSequence = nextSequence;
        }
    }
}
