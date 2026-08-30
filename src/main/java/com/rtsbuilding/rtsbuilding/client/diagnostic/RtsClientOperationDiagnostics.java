package com.rtsbuilding.rtsbuilding.client.diagnostic;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsDiagnosticLevel;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsStructuredDiagnostics;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsOperationTerminalPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** 客户端 RTS 操作边界的统一日志出口。 */
public final class RtsClientOperationDiagnostics {
    private static final long CLIENT_TIMEOUT_NANOS = 60_000_000_000L;
    private static final RtsClientOperationTraceTracker TRACKER =
            new RtsClientOperationTraceTracker();
    private static final AtomicBoolean ENV_RECORDED = new AtomicBoolean();

    private RtsClientOperationDiagnostics() {
    }

    public static TraceStart begin(
            String operation,
            RtsTraceInputKind input,
            String origin,
            String mode,
            boolean chain,
            String target,
            int targetCount) {
        long traceId = RtsTraceIds.nextClientTraceId();
        long now = System.nanoTime();
        TRACKER.start(traceId, operation, now);
        recordEnvironmentOnce();
        long tick = clientTick();
        info("INPUT_PRESS", traceId, 0,
                "client_tick", tick,
                "input", input,
                "binding", input == RtsTraceInputKind.MOUSE ? "MOUSE" : "KEYSYM",
                "physical", input == RtsTraceInputKind.MOUSE ? "MOUSE_LEFT" : "ACTION_BREAK",
                "origin", origin,
                "screen", screenName(),
                "mode", mode,
                "chain", chain,
                "target", target,
                "held_ms", 0);
        info("INTENT_CREATED", traceId, 0,
                "client_tick", tick,
                "operation", operation,
                "target_count", Math.max(0, targetCount));
        return new TraceStart(traceId, now, tick, input);
    }

    public static int packetSend(
            long traceId,
            String packet,
            int heldMs,
            RtsTraceInputKind input,
            RtsMiningStopOrigin stopOrigin,
            int targetCount) {
        int sequence = TRACKER.nextSequence(traceId, "PACKET_SEND_" + packet, System.nanoTime());
        info("PACKET_SEND", traceId, sequence,
                "packet", packet,
                "client_tick", clientTick(),
                "held_ms", Math.max(0, heldMs),
                "target_count", Math.max(0, targetCount),
                "input", input,
                "origin", stopOrigin);
        return sequence;
    }

    public static int inputRelease(
            long traceId,
            int heldMs,
            RtsTraceInputKind input,
            RtsMiningStopOrigin origin) {
        int sequence = TRACKER.nextSequence(traceId, "INPUT_RELEASE", System.nanoTime());
        info("INPUT_RELEASE", traceId, sequence,
                "client_tick", clientTick(),
                "input", input,
                "origin", origin,
                "held_ms", Math.max(0, heldMs));
        return sequence;
    }

    public static void localRejected(long traceId, String reason) {
        info("INTENT_REJECTED_LOCAL", traceId, 0, "reason", reason);
        finish(traceId, "LOCAL_REJECTED");
    }

    /** 新输入覆盖本地活动意图时只结束诊断记录，不参与任何玩法取消判定。 */
    public static void superseded(long traceId) {
        if (traceId == RtsTraceIds.NONE) return;
        finish(traceId, "NEW_ACTION_REPLACED");
    }

    public static void serverTerminal(S2CRtsOperationTerminalPayload payload) {
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
        String version = ModList.get().getModContainerById(RtsbuildingMod.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        info("ENV", 0L, 0,
                "mod", version,
                "minecraft", "1.21.1",
                "loader", "neoforge",
                "locale", Locale.getDefault().toLanguageTag());
    }

    private static void info(String event, long traceId, int sequence, Object... fields) {
        if (level() == RtsDiagnosticLevel.OFF) return;
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; fields != null && i + 1 < fields.length; i += 2) {
            suffix.append(' ').append(fields[i]).append('=').append(safe(fields[i + 1]));
        }
        RtsbuildingMod.LOGGER.info(
                "[RTS-TRACE] schema=2 side=C run={} event={} trace={} seq={}{}",
                RtsTraceIds.runId(), event, RtsTraceIds.format(traceId), sequence, suffix);
        Object[] jsonFields = new Object[(fields == null ? 0 : fields.length) + 6];
        jsonFields[0] = "run";
        jsonFields[1] = RtsTraceIds.runId();
        jsonFields[2] = "trace";
        jsonFields[3] = RtsTraceIds.format(traceId);
        jsonFields[4] = "seq";
        jsonFields[5] = sequence;
        if (fields != null) System.arraycopy(fields, 0, jsonFields, 6, fields.length);
        RtsStructuredDiagnostics.appendClient(event, jsonFields);
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

    private static String screenName() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.screen == null
                ? "NONE" : minecraft.screen.getClass().getSimpleName();
    }

    private static String safe(Object value) {
        if (value == null) return "-";
        return String.valueOf(value).replace('\r', ' ').replace('\n', ' ')
                .replace('"', '\'').replace(' ', '_');
    }

    public record TraceStart(
            long traceId,
            long startedNanos,
            long clientTick,
            RtsTraceInputKind inputKind) {
    }
}
