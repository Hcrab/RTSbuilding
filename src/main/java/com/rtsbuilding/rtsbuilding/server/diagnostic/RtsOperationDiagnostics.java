package com.rtsbuilding.rtsbuilding.server.diagnostic;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsDiagnosticLevel;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationTraceContext;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsStructuredDiagnostics;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEvent;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RTS 服务端操作的集中式结构化诊断出口。
 *
 * <p>只观察网络、Pipeline 和 Workflow 边界，不参与校验、调度、取消或状态迁移。</p>
 */
public final class RtsOperationDiagnostics {
    public static final TypedKey<Long> KEY_OPERATION_ID =
            new TypedKey<>("diagnosticOperationId", Long.class);
    public static final TypedKey<RtsOperationTraceContext> KEY_EFFECTIVE_TRACE =
            new TypedKey<>("diagnosticTraceContext", RtsOperationTraceContext.class);

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private RtsOperationDiagnostics() {
    }

    public static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            RtsWorkflowEngine.getInstance().addListener(RtsOperationDiagnostics::onWorkflowEvent);
        }
    }

    /** Pipeline 入口保留网络层 op；服务器内部/legacy 请求在这里补一个 op。 */
    public static long begin(RtsWorkflowType type, PipelineContext context) {
        RtsOperationTraceContext trace = context.operationTrace();
        long operationId = trace.operationId() >= 0L
                ? trace.operationId() : RtsServerTraceRegistry.allocateOperationId();
        long serverTick = context.player().getLevel().getGameTime();
        RtsOperationTraceContext effective = trace.operationId() == operationId
                ? trace : trace.withOperation(operationId, serverTick);
        context.setData(KEY_OPERATION_ID, operationId);
        context.setData(KEY_EFFECTIVE_TRACE, effective);
        int targets = targetCount(context);
        log("BEGIN", type, context, effective, -1, "-",
                "targets", targets,
                "outcome", "RECEIVED",
                "reason", "NONE",
                "stage", "PIPELINE",
                "server_tick", serverTick);
        return operationId;
    }

    /** WorkflowStartPipe 创建条目后立即绑定，确保同步快速完成也不会丢 trace。 */
    public static void workflowCreated(RtsWorkflowType type, PipelineContext context, int workflowId) {
        RtsServerTraceRegistry.bindWorkflow(
                context.player(), effectiveTrace(context), type, workflowId, targetCount(context));
    }

    public static void pipelineResult(
            RtsWorkflowType type,
            PipelineContext context,
            String stage,
            PipelineResult result,
            boolean exception) {
        RtsOperationTraceContext trace = effectiveTrace(context);
        int workflowId = workflowId(context);
        if (workflowId >= 0) {
            RtsServerTraceRegistry.bindWorkflow(
                    context.player(), trace, type, workflowId, targetCount(context));
        }
        String taskId = "-";
        if (workflowId >= 0) {
            var task = RtsTaskEngine.INSTANCE.diagnosticTaskSnapshot(context.player(), workflowId);
            if (task.isPresent()) {
                RtsServerTraceRegistry.bindTask(task.get());
                taskId = task.get().id().toString();
            }
        }

        if (result instanceof PipelineResult.Success) {
            String acceptedLevel = !"-".equals(taskId) ? "TASK"
                    : workflowId >= 0 ? "WORKFLOW" : "PIPELINE";
            log("RESULT", type, context, trace, workflowId, taskId,
                    "targets", targetCount(context),
                    "outcome", "ACCEPTED",
                    "reason", "NONE",
                    "stage", stage,
                    "accepted_level", acceptedLevel,
                    "server_tick", context.player().getLevel().getGameTime());
            if (workflowId < 0) {
                RtsServerTraceRegistry.terminalWithoutWorkflow(
                        context.player(), trace, type, "COMPLETED", "NO_ASYNC_WORK");
            }
            return;
        }

        boolean skipped = result instanceof PipelineResult.Skip;
        String detail = result instanceof PipelineResult.Failure failure
                ? failure.message() : ((PipelineResult.Skip) result).reason();
        RtsDiagnosticReason reason = RtsDiagnosticReason.classify(stage, detail, skipped, exception);
        String outcome = skipped ? "SKIPPED" : "REJECTED";
        log("RESULT", type, context, trace, workflowId, taskId,
                "targets", targetCount(context),
                "outcome", outcome,
                "reason", reason,
                "stage", stage,
                "detail", safeDetail(detail),
                "server_tick", context.player().getLevel().getGameTime());
        if (workflowId >= 0) {
            RtsServerTraceRegistry.workflowTerminal(
                    context.player().getUUID(), workflowId, outcome, reason.name(), 0, 0);
        } else {
            RtsServerTraceRegistry.terminalWithoutWorkflow(
                    context.player(), trace, type, outcome, reason.name());
        }
    }

    /** 批量目标筛选中的聚合拒绝；不得从逐方块循环调用。 */
    public static void filteredTargets(
            ServerPlayer player,
            int workflowId,
            String mode,
            RtsWorkflowType type,
            RtsDiagnosticReason reason,
            int rejectedTargets) {
        if (player == null || rejectedTargets <= 0) return;
        if (level() == RtsDiagnosticLevel.OFF) return;
        RtsbuildingMod.LOGGER.warn(
                "[RTS-DIAG] schema=2 side=S run={} event=FILTER trace={} seq=- op=- workflow={} task=- "
                        + "player={} mode={} type={} targets={} outcome=REJECTED reason={} stage=TARGET_FILTER server_tick={}",
                RtsTraceIds.runId(), RtsTraceIds.format(0L), workflowValue(workflowId),
                player.getGameProfile().getName(), safeToken(mode), type == null ? "-" : type,
                rejectedTargets, reason, player.getLevel().getGameTime());
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
            reason = "INTERNAL_FAILURE";
        } else if (event.type() == WorkflowEventType.TIMEOUT) {
            outcome = "TIMED_OUT";
            reason = "TIMEOUT";
        } else {
            return;
        }
        RtsServerTraceRegistry.workflowTerminal(
                event.playerId(), event.entryId(), outcome, reason,
                event.status().completedBlocks(), event.status().failedBlocks());
    }

    private static void log(
            String event,
            RtsWorkflowType type,
            PipelineContext context,
            RtsOperationTraceContext trace,
            int workflowId,
            String taskId,
            Object... fields) {
        if (level() == RtsDiagnosticLevel.OFF) return;
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; fields != null && i + 1 < fields.length; i += 2) {
            suffix.append(' ').append(fields[i]).append('=').append(safeToken(fields[i + 1]));
        }
        RtsbuildingMod.LOGGER.info(
                "[RTS-DIAG] schema=2 side=S run={} event={} trace={} seq={} op={} workflow={} task={} "
                        + "player={} mode={} type={}{}",
                RtsTraceIds.runId(), event, RtsTraceIds.format(trace.traceId()), trace.sequence(),
                operationValue(trace.operationId()), workflowValue(workflowId), taskId,
                context.player().getGameProfile().getName(), sessionMode(context), type, suffix);
        Object[] structured = new Object[(fields == null ? 0 : fields.length) + 16];
        Object[] base = {
                "run", RtsTraceIds.runId(),
                "trace", RtsTraceIds.format(trace.traceId()),
                "seq", trace.sequence(),
                "op", trace.operationId(),
                "workflow", workflowId,
                "task", taskId,
                "mode", sessionMode(context),
                "type", type == null ? "-" : type.name()
        };
        System.arraycopy(base, 0, structured, 0, base.length);
        if (fields != null) System.arraycopy(fields, 0, structured, base.length, fields.length);
        RtsStructuredDiagnostics.appendServer("PIPELINE_" + event, structured);
    }

    private static int workflowId(PipelineContext context) {
        Integer value = context.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        return value == null ? -1 : value;
    }

    private static int targetCount(PipelineContext context) {
        Integer value = context.getArg(WorkflowStartPipe.ARG_TOTAL_BLOCKS);
        return value == null ? 0 : Math.max(0, value);
    }

    private static String sessionMode(PipelineContext context) {
        return context.session() == null ? "-" : context.session().mode.name();
    }

    private static boolean isKeyOperation(RtsWorkflowType type, int targets) {
        if (targets > 1) return true;
        if (type == null) return false;
        return switch (type) {
            case ULTIMINE, AREA_MINE, AREA_DESTROY, PLACE_BATCH, QUICK_BUILD, BLUEPRINT_BUILD -> true;
            case MINE_SINGLE, PLACE_SINGLE, STOP_MINING -> false;
        };
    }

    private static String workflowValue(int workflowId) {
        return workflowId < 0 ? "-" : Integer.toString(workflowId);
    }

    private static String operationValue(long operationId) {
        return operationId < 0L ? "-" : Long.toString(operationId);
    }

    private static String safeDetail(String detail) {
        if (detail == null || detail.isBlank()) return "-";
        return detail.replace('\r', ' ').replace('\n', ' ').replace('"', '\'');
    }

    private static String safeToken(Object value) {
        if (value == null) return "-";
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return "-";
        return text.replace('\r', ' ').replace('\n', ' ').replace('"', '\'').replace(' ', '_');
    }

    private static RtsDiagnosticLevel level() {
        try {
            return Config.SERVER_DIAGNOSTIC_LEVEL.get();
        } catch (IllegalStateException ignored) {
            return RtsDiagnosticLevel.BASIC;
        }
    }
}
