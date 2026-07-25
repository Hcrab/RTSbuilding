package com.rtsbuilding.rtsbuilding.server.task.placement;

import java.util.Objects;

/**
 * 单个 detached placement 调度片的纯值输出。
 * COMPLETE 只表示执行游标结束；调用方仍负责在 TaskStore 终态落定后投影 workflow、历史和 UI 副作用。
 */
public record PlacementSliceResult(
        PlacementTaskState state,
        int processedUnits,
        int cursorUnits,
        int succeededUnits,
        int failedUnits,
        Outcome outcome) {

    public PlacementSliceResult {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(outcome, "outcome");
        if (processedUnits < 0 || cursorUnits < 0 || succeededUnits < 0 || failedUnits < 0) {
            throw new IllegalArgumentException("slice delta 不能为负数");
        }
        if ((long) succeededUnits + failedUnits > cursorUnits) {
            throw new IllegalArgumentException("slice 成功与失败数不能超过 cursor 增量");
        }
    }

    public enum Outcome {
        CONTINUE,
        WAITING_RESOURCE,
        COMPLETE
    }
}
