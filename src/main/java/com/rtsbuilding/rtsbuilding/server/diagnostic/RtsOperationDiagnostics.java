package com.rtsbuilding.rtsbuilding.server.diagnostic;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsDiagnosticLevel;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationTraceContext;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsStructuredDiagnostics;
import com.rtsbuilding.rtsbuilding.common.trace.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.UltimineExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEvent;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RTS 服务端操作的集中式结构化诊断出口。
 *
 * <p>这里只观察网络、Pipeline、Workflow 和 Task 边界，不参与校验或状态迁移。</p>
 */
public final class RtsOperationDiagnostics {
    public static final TypedKey<Long> KEY_OPERATION_ID =
            new TypedKey<Long>("diagnosticOperationId", Long.class);
    public static final TypedKey<RtsOperationTraceContext> KEY_EFFECTIVE_TRACE =
            new TypedKey<RtsOperationTraceContext>("diagnosticTraceContext", RtsOperationTraceContext.class);
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private RtsOperationDiagnostics() {}

    public static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            RtsWorkflowEngine.getInstance().addListener(RtsOperationDiagnostics::onWorkflowEvent);
        }
    }

    /** Pipeline 入口沿用网络分配的 op；内部请求才在这里补一个真实 op。 */
    public static long begin(RtsWorkflowType type, PipelineContext context) {
        RtsOperationTraceContext trace = context.operationTrace();
        long operationId = trace.operationId() >= 0L
                ? trace.operationId() : RtsServerTraceRegistry.allocateOperationId();
        long tick = context.player().world.getTotalWorldTime();
        RtsOperationTraceContext effective = trace.operationId() == operationId
                ? trace : trace.withOperation(operationId, tick);
        context.setData(KEY_OPERATION_ID, Long.valueOf(operationId));
        context.setData(KEY_EFFECTIVE_TRACE, effective);
        log("PIPELINE_BEGIN", type, context, effective, -1, "-",
                "targets", targetCount(context), "outcome", "RECEIVED",
                "reason", "NONE", "stage", "PIPELINE", "server_tick", tick);
        return operationId;
    }

    /** WorkflowStartPipe 创建条目后立即绑定，避免同步快速结束时丢失 trace。 */
    public static void workflowCreated(RtsWorkflowType type, PipelineContext context, int workflowId) {
        RtsServerTraceRegistry.bindWorkflow(context.player(), effectiveTrace(context), type,
                workflowId, targetCount(context));
    }

    public static void pipelineResult(RtsWorkflowType type, PipelineContext context,
            String stage, PipelineResult result, boolean exception) {
        RtsOperationTraceContext trace = effectiveTrace(context);
        int workflowId = workflowId(context);
        if (workflowId >= 0) {
            RtsServerTraceRegistry.bindWorkflow(context.player(), trace, type,
                    workflowId, targetCount(context));
        }
        String taskId = "-";
        if (workflowId >= 0) {
            java.util.Optional<com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot> task =
                    RtsTaskEngine.INSTANCE.diagnosticTaskSnapshot(context.player(), workflowId);
            if (task.isPresent()) {
                RtsServerTraceRegistry.bindTask(task.get());
                taskId = task.get().id().toString();
            }
        }

        if (result instanceof PipelineResult.Success) {
            log("PIPELINE_RESULT", type, context, trace, workflowId, taskId,
                    "targets", targetCount(context), "outcome", "ACCEPTED", "reason", "NONE",
                    "stage", stage, "accepted_level", !"-".equals(taskId) ? "TASK"
                            : workflowId >= 0 ? "WORKFLOW" : "PIPELINE");
            if (workflowId < 0) {
                RtsServerTraceRegistry.terminalWithoutWorkflow(
                        context.player(), trace, type, "COMPLETED", "NO_ASYNC_WORK");
            }
            return;
        }

        boolean skipped = result instanceof PipelineResult.Skip;
        String detail = result instanceof PipelineResult.Failure
                ? ((PipelineResult.Failure) result).message()
                : ((PipelineResult.Skip) result).reason();
        RtsDiagnosticReason reason = RtsDiagnosticReason.classify(stage, detail, skipped, exception);
        String outcome = skipped ? "SKIPPED" : "REJECTED";
        log("PIPELINE_RESULT", type, context, trace, workflowId, taskId,
                "targets", targetCount(context), "outcome", outcome, "reason", reason.name(),
                "stage", stage, "detail", safeDetail(detail));
        if (workflowId >= 0) {
            RtsServerTraceRegistry.workflowTerminal(context.player().getUniqueID(), workflowId,
                    outcome, reason.name(), 0, 0);
        } else {
            RtsServerTraceRegistry.terminalWithoutWorkflow(
                    context.player(), trace, type, outcome, reason.name());
        }
    }

    /** 批量目标筛选只写一次聚合诊断，并恢复原 workflow 的 trace/op。 */
    public static void filteredTargets(EntityPlayerMP player, int workflowId, String mode,
            RtsWorkflowType type, RtsDiagnosticReason reason, int rejectedTargets) {
        if (player == null || rejectedTargets <= 0 || level() == RtsDiagnosticLevel.OFF) return;
        RtsOperationTraceContext trace = RtsServerTraceRegistry.traceForWorkflow(player, workflowId);
        RtsStructuredDiagnostics.appendServer("FILTER",
                "trace", RtsTraceIds.format(trace.traceId()), "seq", trace.sequence(),
                "op", trace.operationId(), "workflow", workflowId, "task", "-",
                "player", player.getUniqueID().toString(), "mode", mode,
                "type", type == null ? "-" : type.name(), "targets", rejectedTargets,
                "outcome", "REJECTED", "reason", reason == null ? "UNKNOWN" : reason.name(),
                "stage", "TARGET_FILTER");
    }

    public static RtsOperationTraceContext effectiveTrace(PipelineContext context) {
        RtsOperationTraceContext value = context.getData(KEY_EFFECTIVE_TRACE);
        return value == null ? context.operationTrace() : value;
    }

    private static void onWorkflowEvent(WorkflowEvent event) {
        if (event == null || event.status() == null) return;
        String outcome;
        String reason;
        if (event.type() == WorkflowEventType.COMPLETED) {
            outcome = event.status().failedBlocks() > 0 ? "PARTIAL" : "COMPLETED";
            reason = event.status().failedBlocks() > 0 ? "PARTIAL_FAILURE" : "NONE";
        } else if (event.type() == WorkflowEventType.CANCELLED) {
            outcome = "CANCELLED";
            reason = "CANCELLED";
        } else if (event.type() == WorkflowEventType.TIMEOUT) {
            outcome = "TIMED_OUT";
            reason = "TIMED_OUT";
        } else {
            return;
        }
        RtsServerTraceRegistry.workflowTerminal(event.playerId(), event.entryId(), outcome, reason,
                event.status().completedBlocks(), event.status().failedBlocks());
    }

    private static void log(String event, RtsWorkflowType type, PipelineContext context,
            RtsOperationTraceContext trace, int workflowId, String taskId, Object... fields) {
        if (level() == RtsDiagnosticLevel.OFF) return;
        Object[] values = new Object[16 + (fields == null ? 0 : fields.length)];
        Object[] base = {"trace", RtsTraceIds.format(trace.traceId()), "seq", trace.sequence(),
                "op", trace.operationId(), "workflow", workflowId, "task", taskId,
                "mode", sessionMode(context), "type", type == null ? "-" : type.name(),
                "trace_source", trace.traceSource()};
        System.arraycopy(base, 0, values, 0, base.length);
        if (fields != null) System.arraycopy(fields, 0, values, base.length, fields.length);
        RtsStructuredDiagnostics.appendServer(event, values);
        RtsbuildingMod.LOGGER.info(
                "[RTS-DIAG] schema=2 event={} trace={} seq={} op={} workflow={} task={} player={} type={}",
                event, RtsTraceIds.format(trace.traceId()), trace.sequence(), trace.operationId(),
                workflowId, taskId, context.player().getGameProfile().getName(), type);
    }

    private static int workflowId(PipelineContext context) {
        Integer value = context.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        return value == null ? -1 : value.intValue();
    }

    private static int targetCount(PipelineContext context) {
        Integer value = context.getArg(WorkflowStartPipe.ARG_TOTAL_BLOCKS);
        if (value != null) return Math.max(0, value.intValue());
        List<?> positions = context.getArg(UltimineExecutePipe.ARG_POSITIONS);
        return positions == null ? 0 : positions.size();
    }

    private static String sessionMode(PipelineContext context) {
        return context.session() == null ? "-" : context.session().mode.name();
    }

    private static String safeDetail(String detail) {
        if (detail == null || detail.trim().isEmpty()) return "-";
        return detail.replace('\r', ' ').replace('\n', ' ').replace('"', '\'');
    }

    private static RtsDiagnosticLevel level() {
        try { return Config.SERVER_DIAGNOSTIC_LEVEL.get(); }
        catch (IllegalStateException ignored) { return RtsDiagnosticLevel.BASIC; }
    }
}
