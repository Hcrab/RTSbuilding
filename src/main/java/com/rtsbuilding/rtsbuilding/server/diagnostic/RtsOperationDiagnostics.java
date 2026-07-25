package com.rtsbuilding.rtsbuilding.server.diagnostic;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEvent;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RTS 服务端操作的集中式结构化诊断出口。
 *
 * <p>本类只观察 Pipeline 和 Workflow 的统一边界，不参与校验、任务调度或状态迁移。
 * 日志使用稳定字段与原因代码，使 latest.log 能回答“请求是否到达、在哪一阶段退出、
 * 对应哪个工作流以及最终处理了多少目标”。严禁从每 Tick 或每方块路径调用本类。</p>
 */
public final class RtsOperationDiagnostics {
    public static final TypedKey<Long> KEY_OPERATION_ID =
            new TypedKey<>("diagnosticOperationId", Long.class);

    private static final AtomicLong NEXT_OPERATION_ID = new AtomicLong();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private RtsOperationDiagnostics() {
    }

    /** 幂等安装工作流终态监听器；应在服务端管线注册完成后调用一次。 */
    public static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            RtsWorkflowEngine.getInstance().addListener(RtsOperationDiagnostics::onWorkflowEvent);
        }
    }

    /** 在统一 Pipeline 入口为一次真实服务端请求分配短操作编号。 */
    public static long begin(RtsWorkflowType type, PipelineContext context) {
        long operationId = NEXT_OPERATION_ID.incrementAndGet();
        context.setData(KEY_OPERATION_ID, operationId);
        int targets = targetCount(context);
        if (isKeyOperation(type, targets)) {
            RtsbuildingMod.LOGGER.info(
                    "[RTS-DIAG] event=BEGIN op={} workflow=- player={} mode={} type={} targets={}",
                    operationId,
                    context.player().getGameProfile().getName(),
                    sessionMode(context),
                    type,
                    targets);
        } else {
            RtsbuildingMod.LOGGER.debug(
                    "[RTS-DIAG] event=BEGIN op={} workflow=- player={} mode={} type={} targets={}",
                    operationId,
                    context.player().getGameProfile().getName(),
                    sessionMode(context),
                    type,
                    targets);
        }
        return operationId;
    }

    /** 记录同步 Pipeline 的接纳、拒绝或有意提前退出。 */
    public static void pipelineResult(
            RtsWorkflowType type,
            PipelineContext context,
            String stage,
            PipelineResult result,
            boolean exception) {
        long operationId = operationId(context);
        int workflowId = workflowId(context);
        int targets = targetCount(context);

        if (result instanceof PipelineResult.Success) {
            if (isKeyOperation(type, targets)) {
                RtsbuildingMod.LOGGER.info(
                        "[RTS-DIAG] event=RESULT op={} workflow={} player={} mode={} type={} targets={} "
                                + "outcome=ACCEPTED reason=NONE stage={}",
                        operationId, workflowValue(workflowId),
                        context.player().getGameProfile().getName(), sessionMode(context),
                        type, targets, stage);
            } else {
                RtsbuildingMod.LOGGER.debug(
                        "[RTS-DIAG] event=RESULT op={} workflow={} player={} mode={} type={} targets={} "
                                + "outcome=ACCEPTED reason=NONE stage={}",
                        operationId, workflowValue(workflowId),
                        context.player().getGameProfile().getName(), sessionMode(context),
                        type, targets, stage);
            }
            return;
        }

        boolean skipped = result instanceof PipelineResult.Skip;
        String detail = result instanceof PipelineResult.Failure failure
                ? failure.message()
                : ((PipelineResult.Skip) result).reason();
        RtsDiagnosticReason reason =
                RtsDiagnosticReason.classify(stage, detail, skipped, exception);
        String outcome = skipped ? "SKIPPED" : "REJECTED";
        if (skipped) {
            RtsbuildingMod.LOGGER.info(
                    "[RTS-DIAG] event=RESULT op={} workflow={} player={} mode={} type={} targets={} "
                            + "outcome={} reason={} stage={} detail=\"{}\"",
                    operationId, workflowValue(workflowId),
                    context.player().getGameProfile().getName(), sessionMode(context), type, targets,
                    outcome, reason, stage, safeDetail(detail));
        } else {
            RtsbuildingMod.LOGGER.warn(
                    "[RTS-DIAG] event=RESULT op={} workflow={} player={} mode={} type={} targets={} "
                            + "outcome={} reason={} stage={} detail=\"{}\"",
                    operationId, workflowValue(workflowId),
                    context.player().getGameProfile().getName(), sessionMode(context), type, targets,
                    outcome, reason, stage, safeDetail(detail));
        }
    }

    /**
     * 记录批量目标筛选中的聚合拒绝。调用方必须先聚合数量；本方法不得从逐方块循环调用。
     */
    public static void filteredTargets(
            ServerPlayer player,
            int workflowId,
            String mode,
            RtsWorkflowType type,
            RtsDiagnosticReason reason,
            int rejectedTargets) {
        if (player == null || rejectedTargets <= 0) return;
        RtsbuildingMod.LOGGER.warn(
                "[RTS-DIAG] event=FILTER op=- workflow={} player={} mode={} type={} targets={} "
                        + "outcome=REJECTED reason={} stage=TARGET_FILTER",
                workflowValue(workflowId),
                player.getGameProfile().getName(),
                mode == null || mode.isBlank() ? "-" : mode,
                type == null ? "-" : type,
                rejectedTargets,
                reason);
    }

    private static void onWorkflowEvent(WorkflowEvent event) {
        if (event == null || event.status() == null) return;
        String outcome;
        RtsDiagnosticReason reason;
        if (event.type() == WorkflowEventType.COMPLETED) {
            outcome = event.status().failedBlocks() > 0 ? "PARTIAL" : "COMPLETED";
            reason = event.status().failedBlocks() > 0
                    ? RtsDiagnosticReason.PARTIAL_FAILURE
                    : RtsDiagnosticReason.NONE;
        } else if (event.type() == WorkflowEventType.CANCELLED) {
            outcome = "CANCELLED";
            reason = RtsDiagnosticReason.CANCELLED;
        } else if (event.type() == WorkflowEventType.TIMEOUT) {
            outcome = "TIMED_OUT";
            reason = RtsDiagnosticReason.TIMED_OUT;
        } else {
            return;
        }

        RtsbuildingMod.LOGGER.info(
                "[RTS-DIAG] event=TERMINAL op=- workflow={} player={} mode=- type={} targets={} "
                        + "outcome={} reason={} completed={} failed={}",
                event.entryId(), event.playerId(), event.status().type(),
                event.status().totalBlocks(), outcome, reason,
                event.status().completedBlocks(), event.status().failedBlocks());
    }

    private static long operationId(PipelineContext context) {
        Long value = context.getData(KEY_OPERATION_ID);
        return value == null ? -1L : value;
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

    /** 保持结构化日志单行，避免外部文本破坏字段边界。 */
    private static String safeDetail(String detail) {
        if (detail == null || detail.isBlank()) return "-";
        return detail.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('"', '\'');
    }
}
