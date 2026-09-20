package com.rtsbuilding.rtsbuilding.client.diagnostic;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsDiagnosticLevel;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsStructuredDiagnostics;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsOperationTerminalPayload;
import net.minecraft.client.Minecraft;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Forge 客户端 RTS 操作诊断边界。
 *
 * <p>只记录真实的输入、发包、释放、服务端终态和生命周期重置事件；不参与任何输入门禁、
 * 挖掘状态或网络重试决策。legacy 网络载荷继续保留，新的 traced 载荷负责闭合诊断关联。</p>
 */
public final class RtsClientOperationDiagnostics {
    private static final long CLIENT_TIMEOUT_NANOS = 60_000_000_000L;
    private static final RtsClientOperationTraceTracker TRACKER = new RtsClientOperationTraceTracker();
    private static final AtomicBoolean ENV_RECORDED = new AtomicBoolean();

    private RtsClientOperationDiagnostics() {
    }

    public static TraceStart begin(String operation, RtsTraceInputKind input, String origin,
            String mode, boolean chain, String target, int targetCount) {
        long traceId = RtsTraceIds.nextClientTraceId();
        long now = System.nanoTime();
        TRACKER.start(traceId, operation, now);
        recordEnvironmentOnce();
        info("INPUT_PRESS", traceId, 0,
                "client_tick", clientTick(), "input", input, "origin", origin,
                "mode", mode, "chain", chain, "target", target,
                "target_count", Math.max(0, targetCount));
        info("INTENT_CREATED", traceId, 0,
                "operation", operation, "target_count", Math.max(0, targetCount));
        return new TraceStart(traceId, now, clientTick(), input);
    }

    public static int packetSend(long traceId, String packet, int heldMs,
            RtsTraceInputKind input, RtsMiningStopOrigin stopOrigin, int targetCount) {
        int sequence = TRACKER.nextSequence(traceId, "PACKET_SEND_" + packet, System.nanoTime());
        info("PACKET_SEND", traceId, sequence,
                "packet", packet, "held_ms", Math.max(0, heldMs),
                "input", input, "origin", stopOrigin,
                "target_count", Math.max(0, targetCount));
        return sequence;
    }

    public static int inputRelease(long traceId, int heldMs,
            RtsTraceInputKind input, RtsMiningStopOrigin origin) {
        int sequence = TRACKER.nextSequence(traceId, "INPUT_RELEASE", System.nanoTime());
        info("INPUT_RELEASE", traceId, sequence,
                "held_ms", Math.max(0, heldMs), "input", input, "origin", origin);
        return sequence;
    }

    public static void localRejected(long traceId, String reason) {
        info("INTENT_REJECTED_LOCAL", traceId, 0, "reason", reason);
        finish(traceId, "LOCAL_REJECTED");
    }

    public static void superseded(long traceId) {
        if (traceId != RtsTraceIds.NONE) finish(traceId, "NEW_ACTION_REPLACED");
    }

    public static void serverTerminal(S2CRtsOperationTerminalPayload payload) {
        if (payload == null) return;
        long now = System.nanoTime();
        RtsClientOperationTraceTracker.Completion completion = TRACKER
                .finish(payload.traceId(), payload.outcome(), now)
                .orElse(new RtsClientOperationTraceTracker.Completion(
                        payload.traceId(), "UNKNOWN", payload.outcome(), "UNKNOWN", -1L));
        info("SERVER_TERMINAL_RECEIVED", payload.traceId(), payload.sequence(),
                "outcome", payload.outcome(),
                "reason", payload.reason(),
                "workflow", payload.workflowId(),
                "task", payload.taskId(),
                "completed", payload.completed(),
                "failed", payload.failed(),
                "server_tick", payload.serverTick(),
                "ever_executed", payload.everExecuted(),
                "first_slice_wait_ticks", payload.firstSliceWaitTicks(),
                "elapsed_ms", completion.elapsedMs());
    }

    public static void reset(String reason) {
        for (RtsClientOperationTraceTracker.Completion completion
                : TRACKER.reset(reason, System.nanoTime())) {
            info("TRACE_END", completion.traceId(), 0,
                    "outcome", completion.outcome(),
                    "last_stage", completion.lastStage(),
                    "elapsed_ms", completion.elapsedMs());
        }
    }

    public static void expireTimedOut() {
        for (RtsClientOperationTraceTracker.Completion completion
                : TRACKER.expire(System.nanoTime(), CLIENT_TIMEOUT_NANOS)) {
            info("TRACE_END", completion.traceId(), 0,
                    "outcome", completion.outcome(),
                    "last_stage", completion.lastStage(),
                    "elapsed_ms", completion.elapsedMs());
        }
    }

    static RtsClientOperationTraceTracker trackerForTests() {
        return TRACKER;
    }

    private static void finish(long traceId, String outcome) {
        TRACKER.finish(traceId, outcome, System.nanoTime()).ifPresent(completion ->
                info("TRACE_END", traceId, 0,
                        "outcome", completion.outcome(),
                        "last_stage", completion.lastStage(),
                        "elapsed_ms", completion.elapsedMs()));
    }

    private static void recordEnvironmentOnce() {
        if (!ENV_RECORDED.compareAndSet(false, true)) return;
        info("ENV", 0L, 0,
                "mod", RtsbuildingMod.MODID,
                "minecraft", "1.20.1",
                "loader", "forge");
    }

    private static void info(String event, long traceId, int sequence, Object... fields) {
        if (level() == RtsDiagnosticLevel.OFF) return;
        int extra = fields == null ? 0 : fields.length;
        Object[] values = new Object[8 + extra];
        values[0] = "run";
        values[1] = RtsTraceIds.runId();
        values[2] = "trace";
        values[3] = RtsTraceIds.format(traceId);
        values[4] = "seq";
        values[5] = sequence;
        values[6] = "client_tick";
        values[7] = clientTick();
        if (extra > 0) System.arraycopy(fields, 0, values, 8, extra);
        RtsStructuredDiagnostics.appendClient(event, values);
        RtsbuildingMod.LOGGER.debug("[RTS-TRACE] event={} trace={} seq={}",
                event, RtsTraceIds.format(traceId), sequence);
    }

    private static RtsDiagnosticLevel level() {
        try {
            return Config.CLIENT_DIAGNOSTIC_LEVEL.get();
        } catch (IllegalStateException ignored) {
            return RtsDiagnosticLevel.BASIC;
        }
    }

    private static long clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : -1L;
    }

    public record TraceStart(long traceId, long startedNanos, long clientTick,
            RtsTraceInputKind inputKind) {
    }
}
