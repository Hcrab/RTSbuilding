package com.rtsbuilding.rtsbuilding.client.diagnostic;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsDiagnosticLevel;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsStructuredDiagnostics;
import com.rtsbuilding.rtsbuilding.common.trace.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsRemoteMenuResultPayload;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端请求 trace 的有界状态表。
 *
 * <p>本类负责远程交互/GUI 的细粒度界面阶段；操作级终态同时登记到统一诊断器。
 * 它只记录状态转换，不在渲染或 tick 热路径逐帧写日志；
 * 旧提示包的 traceId 为零，仍可触发兼容预放宽，但不会进入等待表或制造假超时。</p>
 */
public final class RtsClientTraceTracker {
    private static final int MAX_ACTIVE = 128;
    private static final int MAX_RECENT = 256;
    private static final Map<Long, TraceState> ACTIVE = new LinkedHashMap<Long, TraceState>();
    private static final Map<Long, TraceState> RECENT = new LinkedHashMap<Long, TraceState>();
    private static final AtomicLong NEXT_SEQUENCE = new AtomicLong();

    private static long currentRemoteTraceId;

    private RtsClientTraceTracker() {
    }

    public static synchronized long beginRemoteInteraction(
            String source, BlockPos target, String face, int entityId, long distance) {
        long traceId = RtsTraceIds.nextClientTraceId();
        TraceState state = new TraceState(traceId, source, target, entityId, distance);
        ACTIVE.put(traceId, state);
        trim(ACTIVE, MAX_ACTIVE);
        currentRemoteTraceId = traceId;
        RtsClientOperationDiagnostics.registerExisting(traceId, "REMOTE_GUI");
        RtsbuildingMod.LOGGER.info(
                "[RTS-TRACE] side=C event=INTERACT_SENT trace={} kind=REMOTE_GUI source={} target={} entity={} face={} distance={}",
                RtsTraceIds.format(traceId), source, target, entityId, face, distance);
        structured("INTERACT_SENT", state, "source", source, "target", target,
                "entity", entityId, "face", face, "distance", distance);
        return traceId;
    }

    public static synchronized void hintReceived(long traceId, BlockPos target, long distance, int graceTicks) {
        if (!RtsTraceIds.isPresent(traceId)) return;
        TraceState state = state(traceId);
        state.lastStage = "HINT_RECEIVED";
        currentRemoteTraceId = traceId;
        RtsbuildingMod.LOGGER.info(
                "[RTS-TRACE] side=C event=HINT_RECEIVED trace={} kind=REMOTE_GUI target={} distance={} graceTicks={}",
                RtsTraceIds.format(traceId), target, distance, graceTicks);
    }

    public static synchronized void resultReceived(S2CRtsRemoteMenuResultPayload payload) {
        if (payload == null || !RtsTraceIds.isPresent(payload.traceId())) return;
        TraceState state = state(payload.traceId());
        state.lastStage = "RESULT_RECEIVED";
        state.outcome = S2CRtsRemoteMenuResultPayload.outcomeName(payload.outcome());
        state.reason = S2CRtsRemoteMenuResultPayload.reasonName(payload.reason());
        state.expectedWindowId = payload.windowId();
        RtsbuildingMod.LOGGER.info(
                "[RTS-TRACE] side=C event=RESULT_RECEIVED trace={} kind=REMOTE_GUI outcome={} reason={} window={} elapsedMs={}",
                RtsTraceIds.format(payload.traceId()), state.outcome, state.reason,
                payload.windowId(), state.elapsedMillis());
        structured("RESULT_RECEIVED", state, "outcome", state.outcome,
                "reason", state.reason, "window", payload.windowId());
        if (payload.outcome() == S2CRtsRemoteMenuResultPayload.MENU_OPENED) {
            currentRemoteTraceId = payload.traceId();
        } else {
            finish(state, "SERVER_TERMINAL");
        }
    }

    public static synchronized void guiEvent(String event, String screenClass) {
        TraceState state = currentState();
        if (state == null) return;
        state.lastStage = event;
        RtsbuildingMod.LOGGER.info(
                "[RTS-TRACE] side=C event={} trace={} kind=REMOTE_GUI screen={} expectedWindow={} elapsedMs={}",
                event, RtsTraceIds.format(state.traceId), screenClass,
                state.expectedWindowId, state.elapsedMillis());
    }

    public static synchronized long menuInstalled(int windowId, String menuClass, String screenClass) {
        TraceState state = findForWindow(windowId);
        if (state == null) return RtsTraceIds.NONE;
        state.installedWindowId = windowId;
        state.lastStage = "MENU_INSTALLED";
        currentRemoteTraceId = state.traceId;
        String match = state.expectedWindowId < 0 || state.expectedWindowId == windowId ? "MATCH" : "MISMATCH";
        RtsbuildingMod.LOGGER.info(
                "[RTS-TRACE] side=C event=MENU_INSTALLED trace={} kind=REMOTE_GUI window={} expectedWindow={} windowMatch={} menu={} screen={} elapsedMs={}",
                RtsTraceIds.format(state.traceId), windowId, state.expectedWindowId, match,
                menuClass, screenClass, state.elapsedMillis());
        return state.traceId;
    }

    public static synchronized void builderHandoff(int windowId, String menuClass) {
        TraceState state = findForWindow(windowId);
        if (state == null) return;
        state.lastStage = "BUILDER_HANDOFF";
        RtsbuildingMod.LOGGER.info(
                "[RTS-TRACE] side=C event=BUILDER_HANDOFF trace={} kind=REMOTE_GUI window={} menu={} elapsedMs={}",
                RtsTraceIds.format(state.traceId), windowId, menuClass, state.elapsedMillis());
    }

    public static synchronized void hintTimeout(String menuClass, String screenClass) {
        TraceState state = currentState();
        if (state == null) return;
        state.outcome = "CLIENT_TIMEOUT";
        state.reason = "NO_STABLE_MENU";
        RtsbuildingMod.LOGGER.warn(
                "[RTS-TRACE] side=C event=HINT_TIMEOUT trace={} kind=REMOTE_GUI lastStage={} menu={} screen={} expectedWindow={} elapsedMs={}",
                RtsTraceIds.format(state.traceId), state.lastStage, menuClass, screenClass,
                state.expectedWindowId, state.elapsedMillis());
        structured("HINT_TIMEOUT", state, "outcome", state.outcome,
                "reason", state.reason, "menu", menuClass, "screen", screenClass);
        finish(state, "CLIENT_TIMEOUT");
    }

    public static synchronized void screenMissing(int windowId, String menuClass, int recoveryTicks) {
        TraceState state = findForWindow(windowId);
        if (state == null) return;
        state.lastStage = "SCREEN_MISSING";
        RtsbuildingMod.LOGGER.warn(
                "[RTS-TRACE] side=C event=SCREEN_MISSING trace={} kind=REMOTE_GUI window={} menu={} recoveryTicks={} elapsedMs={}",
                RtsTraceIds.format(state.traceId), windowId, menuClass, recoveryTicks, state.elapsedMillis());
    }

    public static synchronized void screenlessRecovery(int windowId, String menuClass) {
        TraceState state = findForWindow(windowId);
        if (state == null) return;
        state.outcome = "CLIENT_RECOVERY";
        state.reason = "SCREEN_MISSING";
        RtsbuildingMod.LOGGER.warn(
                "[RTS-TRACE] side=C event=SCREENLESS_RECOVERY trace={} kind=REMOTE_GUI action=SAFE_CLOSE window={} menu={} elapsedMs={}",
                RtsTraceIds.format(state.traceId), windowId, menuClass, state.elapsedMillis());
        finish(state, "SCREENLESS_RECOVERY");
    }

    public static synchronized void menuClosed(int windowId, String menuClass, String nextScreen) {
        TraceState state = findForWindow(windowId);
        if (state == null) return;
        state.lastStage = "MENU_CLOSED";
        RtsbuildingMod.LOGGER.info(
                "[RTS-TRACE] side=C event=MENU_CLOSED trace={} kind=REMOTE_GUI window={} menu={} nextScreen={} elapsedMs={}",
                RtsTraceIds.format(state.traceId), windowId, menuClass, nextScreen, state.elapsedMillis());
        finish(state, "MENU_CLOSED");
    }

    public static synchronized void openFailed(String menuClass, String screenClass, Throwable failure) {
        TraceState state = currentState();
        String trace = state == null ? "-" : RtsTraceIds.format(state.traceId);
        RtsbuildingMod.LOGGER.error(
                "[RTS-TRACE] side=C event=OPEN_FAILED trace={} kind=REMOTE_GUI menu={} screen={} failure={} action=SAFE_CLOSE",
                trace, menuClass, screenClass,
                failure == null ? "unknown" : failure.getClass().getName(), failure);
        if (state != null) {
            state.outcome = "CLIENT_FAILED";
            state.reason = failure == null ? "UNKNOWN" : failure.getClass().getSimpleName();
            finish(state, "OPEN_FAILED");
        }
    }

    public static synchronized long currentRemoteTraceId() {
        return currentState() == null ? RtsTraceIds.NONE : currentRemoteTraceId;
    }

    public static synchronized void reset(String reason) {
        if (!ACTIVE.isEmpty()) {
            RtsbuildingMod.LOGGER.info(
                    "[RTS-TRACE] side=C event=TRACKER_RESET trace=- kind=REMOTE_GUI active={} reason={}",
                    ACTIVE.size(), reason);
        }
        ACTIVE.clear();
        currentRemoteTraceId = RtsTraceIds.NONE;
    }

    private static TraceState state(long traceId) {
        TraceState state = ACTIVE.get(traceId);
        if (state == null) {
            state = new TraceState(traceId, "UNKNOWN", null, -1, -1L);
            ACTIVE.put(traceId, state);
            trim(ACTIVE, MAX_ACTIVE);
        }
        return state;
    }

    private static TraceState currentState() {
        if (!RtsTraceIds.isPresent(currentRemoteTraceId)) return null;
        return ACTIVE.get(currentRemoteTraceId);
    }

    private static TraceState findForWindow(int windowId) {
        for (TraceState state : ACTIVE.values()) {
            if (state.expectedWindowId == windowId || state.installedWindowId == windowId) {
                return state;
            }
        }
        return currentState();
    }

    private static void finish(TraceState state, String finalStage) {
        state.lastStage = finalStage;
        ACTIVE.remove(state.traceId);
        RECENT.put(state.traceId, state);
        trim(RECENT, MAX_RECENT);
        if (currentRemoteTraceId == state.traceId) {
            currentRemoteTraceId = RtsTraceIds.NONE;
        }
    }

    private static void structured(String event, TraceState state, Object... fields) {
        if (state == null || level() == RtsDiagnosticLevel.OFF) return;
        Object[] values = new Object[12 + (fields == null ? 0 : fields.length)];
        Object[] base = {"trace", RtsTraceIds.format(state.traceId), "seq", state.sequence,
                "op", -1, "workflow", -1, "task", "-", "kind", "REMOTE_GUI"};
        System.arraycopy(base, 0, values, 0, base.length);
        if (fields != null) System.arraycopy(fields, 0, values, base.length, fields.length);
        RtsStructuredDiagnostics.appendClient(event, values);
    }

    private static RtsDiagnosticLevel level() {
        try { return Config.CLIENT_DIAGNOSTIC_LEVEL.get(); }
        catch (IllegalStateException ignored) { return RtsDiagnosticLevel.BASIC; }
    }

    private static void trim(Map<Long, TraceState> values, int limit) {
        while (values.size() > limit) {
            Long oldest = values.keySet().iterator().next();
            values.remove(oldest);
        }
    }

    private static final class TraceState {
        private final long traceId;
        private final long sequence;
        private final long createdAtNanos = System.nanoTime();
        @SuppressWarnings("unused") private final String source;
        @SuppressWarnings("unused") private final BlockPos target;
        @SuppressWarnings("unused") private final int entityId;
        @SuppressWarnings("unused") private final long distance;
        private String lastStage = "INTERACT_SENT";
        private String outcome = "PENDING";
        private String reason = "NONE";
        private int expectedWindowId = -1;
        private int installedWindowId = -1;

        private TraceState(long traceId, String source, BlockPos target, int entityId, long distance) {
            this.traceId = traceId;
            this.sequence = NEXT_SEQUENCE.incrementAndGet();
            this.source = source;
            this.target = target;
            this.entityId = entityId;
            this.distance = distance;
        }

        private long elapsedMillis() {
            return Math.max(0L, (System.nanoTime() - createdAtNanos) / 1_000_000L);
        }
    }
}
