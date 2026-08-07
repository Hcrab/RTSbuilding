package com.rtsbuilding.rtsbuilding.server.diagnostic;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsDiagnosticLevel;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationTraceContext;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsStructuredDiagnostics;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.common.trace.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsOperationTerminalPayload;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.entity.player.EntityPlayerMP;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 把客户端 trace、Pipeline 操作号、Workflow 与真实 durable TaskId 串成有界诊断链。
 *
 * <p>注册表不持有领域 payload，不参与准入或调度。即使容量淘汰，玩法行为也完全不变。</p>
 */
public final class RtsServerTraceRegistry {
    private static final int MAX_TRACES = 1024;
    private static final int MAX_WORKFLOWS = 2048;
    private static final int MAX_TASKS = 2048;
    private static final AtomicLong NEXT_OPERATION_ID = new AtomicLong();
    private static final LinkedHashMap<TraceKey, TraceState> BY_TRACE =
            new LinkedHashMap<TraceKey, TraceState>();
    private static final LinkedHashMap<WorkflowKey, TraceState> BY_WORKFLOW =
            new LinkedHashMap<WorkflowKey, TraceState>();
    private static final LinkedHashMap<String, TraceState> BY_TASK =
            new LinkedHashMap<String, TraceState>();
    private static final LinkedHashMap<TraceKey, Integer> LAST_SEQUENCE =
            new LinkedHashMap<TraceKey, Integer>();

    private RtsServerTraceRegistry() {}

    public static long allocateOperationId() { return NEXT_OPERATION_ID.incrementAndGet(); }

    public static synchronized RtsOperationTraceContext acceptNetwork(EntityPlayerMP player,
            long traceId, int sequence, long clientTick, int heldMs, byte inputKind,
            byte stopOrigin, String packet, long receivedNanos) {
        long serverTick = player == null || player.world == null
                ? -1L : player.world.getTotalWorldTime();
        RtsOperationTraceContext trace = new RtsOperationTraceContext(
                traceId, sequence, clientTick, heldMs,
                RtsTraceInputKind.fromWire(inputKind), RtsMiningStopOrigin.fromWire(stopOrigin),
                packet, allocateOperationId(), receivedNanos, serverTick,
                traceId == RtsTraceIds.NONE ? "LEGACY_MISSING" : "CLIENT");
        TraceState state = stateFor(player, trace);
        if (state != null) {
            state.operationId = trace.operationId();
            state.lastSequence = Math.max(state.lastSequence, sequence);
            state.lastServerTick = serverTick;
        }
        String order = inspectOrder(player, traceId, sequence);
        append("NET_RECEIVE", trace, -1, "-",
                "packet", packet, "player", playerName(player), "client_tick", clientTick,
                "server_tick", serverTick, "held_ms", Math.max(0, heldMs),
                "input", trace.inputKind().name(), "stop_origin", trace.stopOrigin().name(),
                "trace_source", trace.traceSource(), "packet_order", order);
        return trace;
    }

    public static synchronized void bindWorkflow(EntityPlayerMP player,
            RtsOperationTraceContext trace, RtsWorkflowType type, int workflowId, int targets) {
        TraceState state = stateFor(player, trace);
        if (state == null) return;
        boolean first = state.workflowId < 0;
        state.type = type;
        state.workflowId = workflowId;
        state.targets = Math.max(0, targets);
        if (player != null && workflowId >= 0) {
            BY_WORKFLOW.put(new WorkflowKey(player.getUniqueID(), workflowId), state);
            trim(BY_WORKFLOW, MAX_WORKFLOWS);
        }
        if (first) appendState("WORKFLOW_CREATED", state,
                "outcome", "ACCEPTED", "targets", state.targets);
    }

    public static synchronized void bindTask(TaskSnapshot snapshot) {
        if (snapshot == null) return;
        TraceState state = BY_WORKFLOW.get(
                new WorkflowKey(snapshot.ownerId(), snapshot.workflowEntryId()));
        if (state == null) return;
        String taskId = snapshot.id().toString();
        if (taskId.equals(state.taskId)) return;
        state.taskId = taskId;
        state.createdTick = snapshot.createdGameTime();
        state.lastServerTick = snapshot.updatedGameTime();
        BY_TASK.put(taskId, state);
        trim(BY_TASK, MAX_TASKS);
        appendState("TASK_SUBMITTED", state, "task_type", snapshot.type().name(),
                "created_tick", snapshot.createdGameTime(), "total", snapshot.totalUnits());
    }

    /** durable 调度器的聚合切片观察点：绑定真实 TaskId，并在终态发送唯一回执。 */
    public static synchronized void onTaskSlice(TaskSnapshot before, TaskSnapshot after,
            int processedUnits, long sliceNanos, int budgetUnits, long budgetNanos) {
        if (before == null || after == null) return;
        bindTask(before);
        TraceState state = BY_TASK.get(before.id().toString());
        if (state == null) return;
        state.lastServerTick = after.updatedGameTime();
        if (!state.everExecuted && processedUnits > 0) {
            state.everExecuted = true;
            state.firstSliceTick = after.updatedGameTime();
            appendState("TASK_FIRST_SLICE", state, "processed", processedUnits,
                    "slice_nanos", sliceNanos, "budget_units", budgetUnits,
                    "budget_nanos", budgetNanos);
        }
        if (processedUnits > 0 && level() == RtsDiagnosticLevel.VERBOSE) {
            appendState("TASK_PROGRESS", state, "cursor", after.cursorUnits(),
                    "succeeded", after.succeededUnits(), "failed", after.failedUnits());
        }
        if (after.state().terminal()) {
            String outcome = after.state() == TaskLifecycleState.COMPLETED
                    ? (after.failedUnits() > 0 ? "PARTIAL" : "COMPLETED")
                    : after.state() == TaskLifecycleState.CANCELLED ? "CANCELLED" : "FAILED";
            String reason = after.state() == TaskLifecycleState.FAILED ? "TASK_FAILED"
                    : after.failedUnits() > 0 ? "PARTIAL_FAILURE" : "NONE";
            terminal(state, outcome, reason, after.succeededUnits(), after.failedUnits(),
                    after.updatedGameTime());
        }
    }

    public static synchronized RtsOperationTraceContext traceForWorkflow(
            EntityPlayerMP player, int workflowId) {
        if (player == null || workflowId < 0) return RtsOperationTraceContext.legacy("TARGET_FILTER");
        TraceState state = BY_WORKFLOW.get(new WorkflowKey(player.getUniqueID(), workflowId));
        if (state == null) return RtsOperationTraceContext.legacy("TARGET_FILTER");
        return new RtsOperationTraceContext(state.traceId, state.lastSequence, -1L, 0,
                RtsTraceInputKind.UNKNOWN, RtsMiningStopOrigin.NONE, "TARGET_FILTER",
                state.operationId, System.nanoTime(), player.world.getTotalWorldTime(),
                "SERVER_CORRELATED");
    }

    public static synchronized void workflowTerminal(UUID playerId, int workflowId,
            String outcome, String reason, int completed, int failed) {
        TraceState state = BY_WORKFLOW.get(new WorkflowKey(playerId, workflowId));
        if (state == null || state.terminalSent) return;
        // durable Task 已绑定时等待调度器的最终快照，避免发出旧计数和占位 TaskId。
        if (!"-".equals(state.taskId)) {
            appendState("WORKFLOW_TERMINAL_DEFERRED", state,
                    "outcome", outcome, "reason", reason, "waiting_for", "FINAL_TASK_SNAPSHOT");
            return;
        }
        terminal(state, outcome, reason, completed, failed, state.lastServerTick);
    }

    public static synchronized void terminalWithoutWorkflow(EntityPlayerMP player,
            RtsOperationTraceContext trace, RtsWorkflowType type, String outcome, String reason) {
        TraceState state = stateFor(player, trace);
        if (state == null) return;
        state.type = type;
        terminal(state, outcome, reason, 0, 0,
                player == null || player.world == null ? -1L : player.world.getTotalWorldTime());
    }

    private static void terminal(TraceState state, String outcome, String reason,
            int completed, int failed, long serverTick) {
        if (state.terminalSent) return;
        state.terminalSent = true;
        appendState("TERMINAL", state, "outcome", safe(outcome), "reason", safe(reason),
                "completed", Math.max(0, completed), "failed", Math.max(0, failed),
                "ever_executed", state.everExecuted, "server_tick", serverTick);
        EntityPlayerMP player = state.player.get();
        if (RtsTraceIds.isPresent(state.traceId) && player != null) {
            int ack = state.lastSequence == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE : state.lastSequence + 1;
            RtsPayloadRegistrar.sendToPlayer(player, new S2CRtsOperationTerminalPayload(
                    state.traceId, ack, safe(outcome), safe(reason), state.workflowId,
                    state.taskId, completed, failed, serverTick, state.everExecuted,
                    waitTicks(state.createdTick, state.firstSliceTick)));
        }
        if (state.ownerId != null && state.workflowId >= 0) {
            BY_WORKFLOW.remove(new WorkflowKey(state.ownerId, state.workflowId));
        }
        if (!"-".equals(state.taskId)) BY_TASK.remove(state.taskId);
        if (state.ownerId != null && RtsTraceIds.isPresent(state.traceId)) {
            LAST_SEQUENCE.remove(new TraceKey(state.ownerId, state.traceId));
        }
    }

    private static TraceState stateFor(EntityPlayerMP player, RtsOperationTraceContext trace) {
        if (trace == null) return null;
        UUID owner = player == null ? null : player.getUniqueID();
        if (!trace.traced() || owner == null) return new TraceState(owner, player, trace);
        TraceKey key = new TraceKey(owner, trace.traceId());
        TraceState state = BY_TRACE.get(key);
        if (state == null) {
            state = new TraceState(owner, player, trace);
            BY_TRACE.put(key, state);
            trim(BY_TRACE, MAX_TRACES);
        }
        return state;
    }

    private static String inspectOrder(EntityPlayerMP player, long traceId, int sequence) {
        if (player == null || !RtsTraceIds.isPresent(traceId)) return "LEGACY";
        TraceKey key = new TraceKey(player.getUniqueID(), traceId);
        Integer previous = LAST_SEQUENCE.get(key);
        if (previous == null || sequence > previous.intValue()) {
            LAST_SEQUENCE.put(key, Integer.valueOf(sequence));
        }
        trim(LAST_SEQUENCE, MAX_TRACES);
        if (previous == null || sequence > previous.intValue()) return "IN_ORDER";
        return sequence == previous.intValue() ? "DUPLICATE" : "OUT_OF_ORDER";
    }

    /** 服务器停服时释放全部诊断关联；不触碰任何业务任务。 */
    public static synchronized void reset() {
        BY_TRACE.clear();
        BY_WORKFLOW.clear();
        BY_TASK.clear();
        LAST_SEQUENCE.clear();
    }

    private static void appendState(String event, TraceState state, Object... fields) {
        RtsOperationTraceContext trace = new RtsOperationTraceContext(
                state.traceId, state.lastSequence, -1L, 0, RtsTraceInputKind.UNKNOWN,
                RtsMiningStopOrigin.NONE, "SERVER_STATE", state.operationId,
                System.nanoTime(), state.lastServerTick, "SERVER_CORRELATED");
        append(event, trace, state.workflowId, state.taskId, fields);
    }

    private static void append(String event, RtsOperationTraceContext trace,
            int workflowId, String taskId, Object... fields) {
        if (level() == RtsDiagnosticLevel.OFF) return;
        Object[] values = new Object[14 + (fields == null ? 0 : fields.length)];
        Object[] base = {"run", RtsTraceIds.runId(), "trace", RtsTraceIds.format(trace.traceId()),
                "seq", trace.sequence(), "op", trace.operationId(), "workflow", workflowId,
                "task", taskId, "source", trace.traceSource()};
        System.arraycopy(base, 0, values, 0, base.length);
        if (fields != null) System.arraycopy(fields, 0, values, base.length, fields.length);
        RtsStructuredDiagnostics.appendServer(event, values);
    }

    private static RtsDiagnosticLevel level() {
        try { return Config.SERVER_DIAGNOSTIC_LEVEL.get(); }
        catch (IllegalStateException ignored) { return RtsDiagnosticLevel.BASIC; }
    }

    private static String playerName(EntityPlayerMP player) {
        return player == null ? "-" : player.getGameProfile().getName();
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "UNKNOWN"
                : value.replaceAll("[^A-Za-z0-9_.:-]", "_");
    }

    private static long waitTicks(long start, long end) {
        return start < 0L || end < start ? -1L : end - start;
    }

    private static <K, V> void trim(LinkedHashMap<K, V> values, int limit) {
        while (values.size() > limit) values.remove(values.keySet().iterator().next());
    }

    private static final class TraceState {
        private final UUID ownerId;
        private final WeakReference<EntityPlayerMP> player;
        private final long traceId;
        private long operationId;
        private int lastSequence;
        private int workflowId = -1;
        private String taskId = "-";
        private RtsWorkflowType type;
        private int targets;
        private long createdTick = -1L;
        private long firstSliceTick = -1L;
        private long lastServerTick = -1L;
        private boolean everExecuted;
        private boolean terminalSent;

        private TraceState(UUID ownerId, EntityPlayerMP player, RtsOperationTraceContext trace) {
            this.ownerId = ownerId;
            this.player = new WeakReference<EntityPlayerMP>(player);
            this.traceId = trace.traceId();
            this.operationId = trace.operationId();
            this.lastSequence = trace.sequence();
            this.lastServerTick = trace.receiveServerTick();
        }
    }

    private static final class TraceKey {
        private final UUID owner;
        private final long trace;
        private TraceKey(UUID owner, long trace) { this.owner = owner; this.trace = trace; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TraceKey)) return false;
            TraceKey that = (TraceKey) other;
            return trace == that.trace && owner.equals(that.owner);
        }
        @Override public int hashCode() { return 31 * owner.hashCode() + (int) (trace ^ (trace >>> 32)); }
    }

    private static final class WorkflowKey {
        private final UUID owner;
        private final int workflow;
        private WorkflowKey(UUID owner, int workflow) { this.owner = owner; this.workflow = workflow; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof WorkflowKey)) return false;
            WorkflowKey that = (WorkflowKey) other;
            return workflow == that.workflow && owner.equals(that.owner);
        }
        @Override public int hashCode() { return 31 * owner.hashCode() + workflow; }
    }
}
