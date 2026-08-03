package com.rtsbuilding.rtsbuilding.server.diagnostic;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsDiagnosticLevel;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationTraceContext;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsStructuredDiagnostics;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsOperationTerminalPayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.server.level.ServerPlayer;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 把网络 trace、Pipeline op、Workflow 和 durable Task 串成一条有界诊断链。
 *
 * <p>注册表不拥有业务对象，不参与包去重、任务接纳或取消判断。即使状态被容量淘汰，游戏行为也不变。</p>
 */
public final class RtsServerTraceRegistry {
    private static final int MAX_TRACES = 1024;
    private static final int MAX_WORKFLOWS = 2048;
    private static final int MAX_TASKS = 2048;
    private static final AtomicLong NEXT_OPERATION_ID = new AtomicLong();

    private static final LinkedHashMap<TraceKey, TraceState> BY_TRACE = new LinkedHashMap<>();
    private static final LinkedHashMap<WorkflowKey, TraceState> BY_WORKFLOW = new LinkedHashMap<>();
    private static final LinkedHashMap<String, TraceState> BY_TASK = new LinkedHashMap<>();
    private static final Map<TraceKey, OrderState> ORDER = new HashMap<>();

    private RtsServerTraceRegistry() {
    }

    public static long allocateOperationId() {
        return NEXT_OPERATION_ID.incrementAndGet();
    }

    public static synchronized RtsOperationTraceContext acceptNetwork(
            ServerPlayer player,
            RtsTracedPayload payload,
            long clientTick,
            int heldMs,
            byte inputKind,
            byte stopOrigin,
            String packet,
            long receivedNanos,
            long receiveTick) {
        long operationId = allocateOperationId();
        long serverTick = player == null ? receiveTick : player.serverLevel().getGameTime();
        long traceId = payload == null ? RtsTraceIds.NONE : payload.traceId();
        int sequence = payload == null ? 0 : payload.sequence();
        RtsOperationTraceContext trace = new RtsOperationTraceContext(
                traceId, sequence, clientTick, heldMs,
                RtsTraceInputKind.fromWire(inputKind), RtsMiningStopOrigin.fromWire(stopOrigin),
                packet, operationId, receivedNanos, receiveTick,
                traceId == RtsTraceIds.NONE ? "LEGACY_MISSING" : "CLIENT");

        TraceState state = stateFor(player, trace);
        if (state != null) {
            state.lastOperationId = operationId;
            state.lastSequence = Math.max(state.lastSequence, sequence);
            state.lastServerTick = serverTick;
        }
        String order = inspectOrder(player, traceId, sequence);
        long enqueueDelayTicks = receiveTick < 0L ? -1L : Math.max(0L, serverTick - receiveTick);
        infoTrace("NET_RECEIVE", trace,
                "player", playerName(player),
                "packet", packet,
                "server_tick", serverTick,
                "client_tick", clientTick,
                "held_ms", Math.max(0, heldMs),
                "input", trace.inputKind(),
                "stop_origin", trace.stopOrigin(),
                "enqueue_delay_ticks", enqueueDelayTicks,
                "trace_source", trace.traceSource());
        if (order != null) {
            infoTrace("PACKET_ORDER", trace,
                    "player", playerName(player),
                    "outcome", order,
                    "server_tick", serverTick);
        }
        return trace;
    }

    public static synchronized TraceState bindWorkflow(
            ServerPlayer player,
            RtsOperationTraceContext trace,
            RtsWorkflowType type,
            int workflowId,
            int targets) {
        TraceState state = stateFor(player, trace);
        if (state == null) return null;
        boolean firstBinding = state.workflowId < 0;
        state.type = type;
        state.workflowId = workflowId;
        state.targets = Math.max(0, targets);
        state.lastServerTick = player == null ? state.lastServerTick : player.serverLevel().getGameTime();
        if (workflowId >= 0 && player != null) {
            BY_WORKFLOW.put(new WorkflowKey(player.getUUID(), workflowId), state);
            trim(BY_WORKFLOW, MAX_WORKFLOWS);
        }
        if (firstBinding) {
            infoDiag("WORKFLOW_CREATED", state,
                    "outcome", "ACCEPTED",
                    "reason", "NONE",
                    "targets", state.targets);
        }
        return state;
    }

    public static synchronized void bindTask(TaskSnapshot snapshot) {
        if (snapshot == null) return;
        TraceState state = BY_WORKFLOW.get(new WorkflowKey(snapshot.ownerId(), snapshot.workflowEntryId()));
        if (state == null) {
            state = recoveredState(snapshot);
            BY_WORKFLOW.put(new WorkflowKey(snapshot.ownerId(), snapshot.workflowEntryId()), state);
            trim(BY_WORKFLOW, MAX_WORKFLOWS);
        }
        String taskId = snapshot.id().toString();
        if (taskId.equals(state.taskId)) return;
        state.taskId = taskId;
        state.createdTick = snapshot.createdGameTime();
        state.lastServerTick = Math.max(state.lastServerTick, snapshot.updatedGameTime());
        BY_TASK.put(taskId, state);
        trim(BY_TASK, MAX_TASKS);
        infoDiag("TASK_SUBMITTED", state,
                "task_type", snapshot.type(),
                "created_tick", snapshot.createdGameTime(),
                "total", snapshot.totalUnits(),
                "cursor", snapshot.cursorUnits());
    }

    public static synchronized void onTaskSlice(
            TaskSnapshot before,
            TaskSnapshot after,
            int processedUnits,
            long sliceNanos,
            int budgetUnits,
            long budgetNanos) {
        if (before == null || after == null) return;
        bindTask(before);
        TraceState state = BY_TASK.get(before.id().toString());
        if (state == null) return;
        long tick = RtsServerHealthDiagnostics.currentServerTick();
        state.lastServerTick = tick;
        if (!state.everExecuted) {
            state.everExecuted = true;
            state.firstSliceTick = tick;
            infoDiag("TASK_FIRST_SLICE", state,
                    "task_type", before.type(),
                    "created_tick", state.createdTick,
                    "first_slice_tick", tick,
                    "submit_to_first_slice_ticks", waitTicks(state.createdTick, tick),
                    "processed_this_slice", processedUnits,
                    "slice_nanos", sliceNanos,
                    "budget_units", budgetUnits,
                    "budget_nanos", budgetNanos);
        }
        state.lastSliceTick = tick;
        maybeLogWait(state, before, after);
        maybeLogProgress(state, after, processedUnits, sliceNanos);
        if (after.state().terminal()) {
            String outcome = switch (after.state()) {
                case COMPLETED -> after.failedUnits() > 0 ? "PARTIAL" : "COMPLETED";
                case CANCELLED -> "CANCELLED";
                case FAILED -> "FAILED";
                default -> after.state().name();
            };
            String reason = after.state() == TaskLifecycleState.CANCELLED
                    ? ("NONE".equals(state.cancelOrigin) ? "TASK_CANCELLED_UNKNOWN" : state.cancelOrigin)
                    : after.state() == TaskLifecycleState.FAILED
                    ? "TASK_FAILED" : after.failedUnits() > 0 ? "PARTIAL_FAILURE" : "NONE";
            terminal(state, outcome, reason, after.succeededUnits(), after.failedUnits(), tick);
        }
    }

    public static synchronized void markCancelOrigin(
            ServerPlayer player, int workflowId, String origin) {
        if (player == null || workflowId < 0) return;
        TraceState state = BY_WORKFLOW.get(new WorkflowKey(player.getUUID(), workflowId));
        if (state == null) return;
        state.cancelOrigin = safe(origin, "INTERNAL_FAILURE");
        infoDiag("TASK_CANCEL_REQUEST", state,
                "cancel_origin", state.cancelOrigin,
                "ever_executed", state.everExecuted,
                "first_slice_tick", state.firstSliceTick,
                "age_ticks", waitTicks(state.createdTick, player.serverLevel().getGameTime()));
    }

    /** 为批量筛选等晚于 Pipeline 创建的观察点恢复同一条 trace。 */
    public static synchronized RtsOperationTraceContext traceForWorkflow(
            ServerPlayer player, int workflowId) {
        if (player == null || workflowId < 0) {
            return RtsOperationTraceContext.legacy("TARGET_FILTER");
        }
        TraceState state = BY_WORKFLOW.get(new WorkflowKey(player.getUUID(), workflowId));
        if (state == null) return RtsOperationTraceContext.legacy("TARGET_FILTER");
        return new RtsOperationTraceContext(
                state.traceId, state.lastSequence, -1L, 0,
                RtsTraceInputKind.UNKNOWN, RtsMiningStopOrigin.NONE,
                "TARGET_FILTER", state.lastOperationId, System.nanoTime(),
                player.serverLevel().getGameTime(), "SERVER_CORRELATED");
    }

    /**
     * 记录不经过调度 slice、由外部命令直接终止的 durable task。
     *
     * <p>这里只消费已经完成状态迁移的最终 snapshot，不参与取消本身。</p>
     */
    public static synchronized void externalTaskTerminal(
            TaskSnapshot snapshot, String outcome, String reason) {
        if (snapshot == null || !snapshot.state().terminal()) return;
        TraceState state = BY_TASK.get(snapshot.id().toString());
        if (state == null) return;
        state.lastServerTick = Math.max(state.lastServerTick, snapshot.updatedGameTime());
        terminal(state, outcome, reason,
                snapshot.succeededUnits(), snapshot.failedUnits(), snapshot.updatedGameTime());
    }

    public static synchronized void workflowTerminal(
            UUID playerId,
            int workflowId,
            String outcome,
            String reason,
            int completed,
            int failed) {
        TraceState state = BY_WORKFLOW.get(new WorkflowKey(playerId, workflowId));
        if (state == null) return;
        /*
         * Durable executor 会先投影 Workflow 完成，再把最终 snapshot 交回调度器。
         * 已绑定 Task 时必须由 onTaskSlice 使用最终 snapshot 发唯一终态，否则会提前
         * 发出旧计数，随后又把同一任务误识别为“恢复任务”。这只调整诊断观察顺序，
         * 不改变 Workflow 或 Task 的业务完成时机。
         */
        if (!"-".equals(state.taskId)) {
            if (!state.workflowTerminalDeferred) {
                state.workflowTerminalDeferred = true;
                state.lastServerTick = RtsServerHealthDiagnostics.currentServerTick();
                infoDiag("WORKFLOW_TERMINAL_DEFERRED", state,
                        "outcome", safe(outcome, "UNKNOWN"),
                        "reason", safe(reason, "UNKNOWN"),
                        "completed", Math.max(0, completed),
                        "failed", Math.max(0, failed),
                        "waiting_for", "FINAL_TASK_SNAPSHOT");
            }
            return;
        }
        String terminalReason = "CANCELLED".equalsIgnoreCase(outcome)
                && !"NONE".equals(state.cancelOrigin)
                ? state.cancelOrigin : reason;
        terminal(state, outcome, terminalReason, completed, failed,
                RtsServerHealthDiagnostics.currentServerTick());
    }

    public static synchronized void terminalWithoutWorkflow(
            ServerPlayer player,
            RtsOperationTraceContext trace,
            RtsWorkflowType type,
            String outcome,
            String reason) {
        TraceState state = stateFor(player, trace);
        if (state == null) return;
        if (type == RtsWorkflowType.STOP_MINING
                && state.workflowId >= 0
                && !state.terminalSent) {
            infoDiag("STOP_ACCEPTED", state,
                    "stop_origin", trace.stopOrigin(),
                    "outcome", "WAITING_FOR_TASK_TERMINAL");
            return;
        }
        state.type = type;
        terminal(state, outcome, reason, 0, 0,
                player == null ? -1L : player.serverLevel().getGameTime());
    }

    public static synchronized void reset() {
        BY_TRACE.clear();
        BY_WORKFLOW.clear();
        BY_TASK.clear();
        ORDER.clear();
    }

    private static void maybeLogWait(TraceState state, TaskSnapshot before, TaskSnapshot after) {
        String wait = after.waitKey() == null
                ? "NONE" : safe(after.waitKey().kind() + ':' + after.waitKey().value(), "UNKNOWN");
        if (wait.equals(state.lastWaitReason)) return;
        state.lastWaitReason = wait;
        if (!"NONE".equals(wait)) {
            infoDiag("TASK_WAIT", state,
                    "wait_reason", wait,
                    "task_state", after.state(),
                    "cursor", after.cursorUnits(),
                    "succeeded", after.succeededUnits(),
                    "failed", after.failedUnits());
        } else if (before.waitKey() != null) {
            infoDiag("TASK_RESUME", state,
                    "previous_wait_reason", before.waitKey().kind() + ':' + before.waitKey().value(),
                    "cursor", after.cursorUnits());
        }
    }

    private static void maybeLogProgress(
            TraceState state, TaskSnapshot snapshot, int processedUnits, long sliceNanos) {
        if (processedUnits <= 0) return;
        long now = System.nanoTime();
        int percent = snapshot.totalUnits() <= 0 ? 0
                : (int) Math.min(100L, ((long) snapshot.cursorUnits() * 100L) / snapshot.totalUnits());
        boolean threshold = crossedThreshold(state.lastProgressPercent, percent);
        boolean verboseSample = level() == RtsDiagnosticLevel.VERBOSE
                && now - state.lastProgressLogNanos >= 1_000_000_000L;
        if (!threshold && !verboseSample) return;
        state.lastProgressNanos = now;
        state.lastProgressLogNanos = now;
        state.lastProgressPercent = percent;
        infoDiag("TASK_PROGRESS", state,
                "total", snapshot.totalUnits(),
                "cursor", snapshot.cursorUnits(),
                "succeeded", snapshot.succeededUnits(),
                "failed", snapshot.failedUnits(),
                "processed_this_slice", processedUnits,
                "slice_nanos", sliceNanos);
    }

    private static boolean crossedThreshold(int previous, int current) {
        int[] thresholds = {10, 25, 50, 75, 100};
        for (int threshold : thresholds) {
            if (previous < threshold && current >= threshold) return true;
        }
        return false;
    }

    private static void terminal(
            TraceState state,
            String outcome,
            String reason,
            int completed,
            int failed,
            long serverTick) {
        if (state.terminalSent) return;
        state.terminalSent = true;
        state.lastServerTick = serverTick;
        long recentGap = RtsServerHealthDiagnostics.recentGapMs(serverTick);
        infoDiag("TERMINAL", state,
                "outcome", safe(outcome, "UNKNOWN"),
                "reason", safe(reason, "UNKNOWN"),
                "completed", Math.max(0, completed),
                "failed", Math.max(0, failed),
                "ever_executed", state.everExecuted,
                "first_slice_tick", state.firstSliceTick,
                "submit_to_first_slice_ticks", waitTicks(state.createdTick, state.firstSliceTick),
                "submit_to_terminal_ticks", waitTicks(state.createdTick, serverTick),
                "server_stall_overlap", recentGap > 0L,
                "recent_tick_gap_ms", recentGap);

        ServerPlayer player = state.player.get();
        if (state.traceId != RtsTraceIds.NONE && player != null) {
            int ackSequence = state.lastSequence == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE : state.lastSequence + 1;
            RtsClientboundPackets.sendToPlayer(player, new S2CRtsOperationTerminalPayload(
                    state.traceId, ackSequence,
                    safe(outcome, "UNKNOWN"), safe(reason, "UNKNOWN"),
                    state.workflowId, state.taskId,
                    Math.max(0, completed), Math.max(0, failed), serverTick,
                    state.everExecuted, waitTicks(state.createdTick, state.firstSliceTick)));
        }
        if (state.ownerId != null && state.workflowId >= 0) {
            BY_WORKFLOW.remove(new WorkflowKey(state.ownerId, state.workflowId));
        }
        if (!"-".equals(state.taskId)) BY_TASK.remove(state.taskId);
    }

    private static TraceState stateFor(ServerPlayer player, RtsOperationTraceContext trace) {
        if (trace == null) return null;
        UUID playerId = player == null ? null : player.getUUID();
        if (trace.traceId() == RtsTraceIds.NONE || playerId == null) {
            return new TraceState(playerId, player, trace);
        }
        TraceKey key = new TraceKey(playerId, trace.traceId());
        TraceState state = BY_TRACE.get(key);
        if (state == null) {
            state = new TraceState(playerId, player, trace);
            BY_TRACE.put(key, state);
            trim(BY_TRACE, MAX_TRACES);
        }
        return state;
    }

    private static TraceState recoveredState(TaskSnapshot snapshot) {
        TraceState state = new TraceState(snapshot.ownerId(), null,
                RtsOperationTraceContext.legacy("PERSIST_RESTORE"));
        state.workflowId = snapshot.workflowEntryId();
        state.type = switch (snapshot.type()) {
            case MINING -> RtsWorkflowType.AREA_MINE;
            case DESTRUCTION -> RtsWorkflowType.AREA_DESTROY;
            default -> null;
        };
        infoDiag("WORKFLOW_RESTORED", state,
                "task", snapshot.id(),
                "task_type", snapshot.type(),
                "restore_decision", snapshot.state().terminal() ? "KEEP_VISIBLE_ONLY" : "RESUME");
        return state;
    }

    private static String inspectOrder(ServerPlayer player, long traceId, int sequence) {
        if (player == null || traceId == RtsTraceIds.NONE) return null;
        TraceKey key = new TraceKey(player.getUUID(), traceId);
        OrderState order = ORDER.computeIfAbsent(key, ignored -> new OrderState());
        String outcome = null;
        if (order.seen) {
            // seq 覆盖客户端本地 INPUT_RELEASE 等边界，因此两个 C2S 包之间允许跳号；
            // 服务端只判定重复和倒序，避免把正常的本地事件误报成丢包。
            if (sequence == order.lastSequence) outcome = "DUPLICATE";
            else if (sequence < order.lastSequence) outcome = "STALE";
        }
        if (!order.seen || sequence > order.lastSequence) order.lastSequence = sequence;
        order.seen = true;
        if (outcome != null) order.anomalies++;
        if (ORDER.size() > MAX_TRACES) ORDER.remove(ORDER.keySet().iterator().next());
        return outcome == null || shouldReport(order.anomalies) ? outcome : null;
    }

    private static boolean shouldReport(long count) {
        return count <= 1L || (count & (count - 1L)) == 0L;
    }

    private static void infoTrace(String event, RtsOperationTraceContext trace, Object... fields) {
        if (level() == RtsDiagnosticLevel.OFF) return;
        String suffix = suffix(fields);
        RtsbuildingMod.LOGGER.info(
                "[RTS-TRACE] schema=2 side=S run={} event={} trace={} seq={} op={}{}",
                RtsTraceIds.runId(), event, RtsTraceIds.format(trace.traceId()), trace.sequence(),
                value(trace.operationId()), suffix);
        appendStructured("TRACE_" + event, trace.traceId(), trace.sequence(), trace.operationId(), fields);
    }

    private static void infoDiag(String event, TraceState state, Object... fields) {
        if (level() == RtsDiagnosticLevel.OFF) return;
        String suffix = suffix(fields);
        RtsbuildingMod.LOGGER.info(
                "[RTS-DIAG] schema=2 side=S run={} event={} trace={} seq={} op={} workflow={} task={} type={} server_tick={}{}",
                RtsTraceIds.runId(), event, RtsTraceIds.format(state.traceId), state.lastSequence,
                value(state.lastOperationId), value(state.workflowId), state.taskId,
                state.type == null ? "-" : state.type, value(state.lastServerTick), suffix);
        appendStructured(event, state.traceId, state.lastSequence, state.lastOperationId,
                concat(new Object[]{"workflow", state.workflowId, "task", state.taskId,
                        "type", state.type, "server_tick", state.lastServerTick}, fields));
    }

    private static void appendStructured(
            String event, long traceId, int sequence, long operationId, Object... fields) {
        Object[] prefix = {"run", RtsTraceIds.runId(), "trace", RtsTraceIds.format(traceId),
                "seq", sequence, "op", operationId};
        RtsStructuredDiagnostics.appendServer(event, concat(prefix, fields));
    }

    private static Object[] concat(Object[] first, Object[] second) {
        Object[] result = new Object[first.length + (second == null ? 0 : second.length)];
        System.arraycopy(first, 0, result, 0, first.length);
        if (second != null) System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static String suffix(Object... fields) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; fields != null && i + 1 < fields.length; i += 2) {
            result.append(' ').append(fields[i]).append('=').append(safe(fields[i + 1], "-"));
        }
        return result.toString();
    }

    private static String safe(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return fallback;
        return text.replace('\r', ' ').replace('\n', ' ').replace('"', '\'').replace(' ', '_');
    }

    private static String playerName(ServerPlayer player) {
        return player == null ? "-" : safe(player.getGameProfile().getName(), "-");
    }

    private static String value(long value) {
        return value < 0L ? "-" : Long.toString(value);
    }

    private static long waitTicks(long start, long end) {
        return start < 0L || end < 0L ? -1L : Math.max(0L, end - start);
    }

    private static <K, V> void trim(LinkedHashMap<K, V> map, int limit) {
        while (map.size() > limit) map.remove(map.entrySet().iterator().next().getKey());
    }

    private static RtsDiagnosticLevel level() {
        try {
            return Config.SERVER_DIAGNOSTIC_LEVEL.get();
        } catch (IllegalStateException ignored) {
            return RtsDiagnosticLevel.BASIC;
        }
    }

    private record TraceKey(UUID playerId, long traceId) {
    }

    private record WorkflowKey(UUID playerId, int workflowId) {
    }

    private static final class OrderState {
        private boolean seen;
        private int lastSequence;
        private long anomalies;
    }

    static final class TraceState {
        private final UUID ownerId;
        private final WeakReference<ServerPlayer> player;
        private final long traceId;
        private long lastOperationId;
        private int lastSequence;
        private int workflowId = -1;
        private String taskId = "-";
        private RtsWorkflowType type;
        private int targets;
        private long createdTick = -1L;
        private long firstSliceTick = -1L;
        private long lastSliceTick = -1L;
        private long lastServerTick = -1L;
        private long lastProgressNanos;
        private long lastProgressLogNanos;
        private int lastProgressPercent;
        private String lastWaitReason = "NONE";
        private String cancelOrigin = "NONE";
        private boolean everExecuted;
        private boolean terminalSent;
        private boolean workflowTerminalDeferred;

        private TraceState(UUID ownerId, ServerPlayer player, RtsOperationTraceContext trace) {
            this.ownerId = ownerId;
            this.player = new WeakReference<>(player);
            this.traceId = trace.traceId();
            this.lastOperationId = trace.operationId();
            this.lastSequence = trace.sequence();
            this.lastServerTick = trace.receiveServerTick();
        }
    }
}
