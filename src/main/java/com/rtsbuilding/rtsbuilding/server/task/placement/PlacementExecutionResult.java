package com.rtsbuilding.rtsbuilding.server.task.placement;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationReason;
import java.util.List;

/** 单格执行结果；批处理据此区分资源等待、块加载等待和不可重试失败。 */
public record PlacementExecutionResult(boolean keepGoing, RtsOperationReason reason,
                                       String detail, List<String> missingItems) {
    public PlacementExecutionResult {
        reason = reason == null ? RtsOperationReason.UNKNOWN : reason;
        detail = detail == null ? "" : detail;
        missingItems = missingItems == null ? List.of() : List.copyOf(missingItems);
    }
    public static PlacementExecutionResult success() {
        return new PlacementExecutionResult(true, RtsOperationReason.SUCCESS, "", List.of());
    }
    public static PlacementExecutionResult skipped(String detail) {
        return new PlacementExecutionResult(true, RtsOperationReason.SKIPPED, detail, List.of());
    }
    public static PlacementExecutionResult waiting(RtsOperationReason reason, String detail, List<String> missingItems) {
        return new PlacementExecutionResult(false, reason, detail, missingItems);
    }
    public static PlacementExecutionResult failed(RtsOperationReason reason, String detail) {
        return new PlacementExecutionResult(false, reason, detail, List.of());
    }
}
