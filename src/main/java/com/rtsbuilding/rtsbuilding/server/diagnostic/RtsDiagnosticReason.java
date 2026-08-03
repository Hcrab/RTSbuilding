package com.rtsbuilding.rtsbuilding.server.diagnostic;

import java.util.Locale;

/**
 * 服务端 RTS 操作的稳定诊断原因代码。
 *
 * <p>该枚举面向日志和外部排障工具，不承载玩家提示文案，也不参与玩法判断。
 * 分类器优先使用统一 Pipe 的职责名称，再谨慎读取现有结果文本；这样业务代码无需
 * 到处埋日志，同时未来调整人类可读文案也不会轻易改变常见原因代码。</p>
 */
public enum RtsDiagnosticReason {
    NONE,
    FEATURE_LOCKED,
    STORAGE_SESSION_MISSING,
    PIPELINE_CONTEXT_MISSING,
    WORKFLOW_QUEUE_FULL,
    TOOL_UNAVAILABLE,
    HARVEST_TIER_TOO_LOW,
    TOOL_CANNOT_HARVEST,
    OUTSIDE_SESSION_RANGE,
    TARGET_INACCESSIBLE,
    CLAIM_DENIED,
    TARGET_INVALID,
    TARGET_DUPLICATE,
    TARGET_LIMIT_REACHED,
    TARGET_UNBREAKABLE,
    DESTROY_SPEED_ZERO,
    TASK_SUBMISSION_FAILED,
    BLUEPRINT_DISABLED,
    BLUEPRINT_EMPTY,
    BLUEPRINT_TOO_LARGE,
    INVALID_ANCHOR,
    ADMISSION_QUEUE_FULL,
    PLACED_BLOCK_RECOVERED,
    PIPELINE_EARLY_EXIT,
    PIPELINE_REJECTED,
    PIPE_EXCEPTION,
    PARTIAL_FAILURE,
    CANCELLED,
    TIMED_OUT;

    /**
     * 将现有 Pipe 结果归一化为稳定原因代码。此方法只解释结果，不改变结果语义。
     */
    public static RtsDiagnosticReason classify(
            String stage, String detail, boolean skipped, boolean exception) {
        if (exception) return PIPE_EXCEPTION;

        String pipe = normalized(stage);
        String message = normalized(detail);

        if (skipped) {
            if (message.contains("placed block recovered")) return PLACED_BLOCK_RECOVERED;
            return PIPELINE_EARLY_EXIT;
        }
        if (pipe.contains("progressiongatepipe") || message.contains("feature not unlocked")) {
            return FEATURE_LOCKED;
        }
        if (pipe.contains("sessiondimensionpipe")) return PIPELINE_CONTEXT_MISSING;
        if (pipe.contains("sessionvalidatepipe") || message.contains("no storage session")
                || message.contains("no session in context")) {
            return STORAGE_SESSION_MISSING;
        }
        if (pipe.contains("workflowstartpipe") || message.contains("workflow queue full")) {
            return WORKFLOW_QUEUE_FULL;
        }
        if (pipe.contains("toolborrowpipe") || message.contains("mining tool not available")) {
            return TOOL_UNAVAILABLE;
        }
        if (message.contains("claim protection denied")) return CLAIM_DENIED;
        if (message.contains("cannot access world target")) return TARGET_INACCESSIBLE;
        if (message.contains("taskstore") || message.contains("submit")
                || message.contains("submission")) {
            return TASK_SUBMISSION_FAILED;
        }
        if (message.contains("blueprints disabled")) return BLUEPRINT_DISABLED;
        if (message.contains("blueprint is empty")) return BLUEPRINT_EMPTY;
        if (message.contains("blueprint exceeds")) return BLUEPRINT_TOO_LARGE;
        if (message.contains("invalid anchor")) return INVALID_ANCHOR;
        if (message.contains("admission queue full")) return ADMISSION_QUEUE_FULL;
        return PIPELINE_REJECTED;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
