package com.rtsbuilding.rtsbuilding.server.task;

/**
 * 单次调度片的结果。
 *
 * <p>{@code processedUnits} 用于扣减本 Tick 的全局执行预算；{@code cursorUnits}
 * 表示领域游标真正向前推进的数量；成功和失败分别记录玩家可见的执行结果。
 * 资源不足导致执行回退时，只有预算消耗增加，后三者都必须为零。</p>
 */
public record TaskStepResult(
        int processedUnits,
        int cursorUnits,
        int succeededUnits,
        int failedUnits,
        Outcome outcome,
        String errorKey) {
    public enum Outcome { CONTINUE, YIELD, NEXT_TICK, COMPLETE, WAIT_RESOURCE, FAIL }

    public TaskStepResult {
        if (processedUnits < 0) throw new IllegalArgumentException("processedUnits < 0");
        if (cursorUnits < 0) throw new IllegalArgumentException("cursorUnits < 0");
        if (succeededUnits < 0) throw new IllegalArgumentException("succeededUnits < 0");
        if (failedUnits < 0) throw new IllegalArgumentException("failedUnits < 0");
        if (cursorUnits > processedUnits) throw new IllegalArgumentException("cursorUnits > processedUnits");
        if ((long) succeededUnits + failedUnits > cursorUnits) {
            throw new IllegalArgumentException("result units exceed cursorUnits");
        }
        if (outcome != Outcome.FAIL && errorKey != null) {
            throw new IllegalArgumentException("只有失败结果可以携带 errorKey");
        }
    }

    public static TaskStepResult continueWith(int units) {
        return new TaskStepResult(units, units, units, 0, Outcome.CONTINUE, null);
    }

    public static TaskStepResult continueWith(
            int processedUnits, int cursorUnits, int succeededUnits, int failedUnits) {
        return new TaskStepResult(
                processedUnits, cursorUnits, succeededUnits, failedUnits, Outcome.CONTINUE, null);
    }

    public static TaskStepResult yield(int units) {
        return new TaskStepResult(units, units, units, 0, Outcome.YIELD, null);
    }

    public static TaskStepResult nextTick(
            int processedUnits, int cursorUnits, int succeededUnits, int failedUnits) {
        return new TaskStepResult(
                processedUnits, cursorUnits, succeededUnits, failedUnits, Outcome.NEXT_TICK, null);
    }

    public static TaskStepResult complete(int units) {
        return new TaskStepResult(units, units, units, 0, Outcome.COMPLETE, null);
    }

    public static TaskStepResult complete(
            int processedUnits, int cursorUnits, int succeededUnits, int failedUnits) {
        return new TaskStepResult(
                processedUnits, cursorUnits, succeededUnits, failedUnits, Outcome.COMPLETE, null);
    }

    public static TaskStepResult waitForResource() {
        return new TaskStepResult(0, 0, 0, 0, Outcome.WAIT_RESOURCE, null);
    }

    public static TaskStepResult waitForResource(
            int processedUnits, int cursorUnits, int succeededUnits, int failedUnits) {
        return new TaskStepResult(
                processedUnits, cursorUnits, succeededUnits, failedUnits, Outcome.WAIT_RESOURCE, null);
    }

    public static TaskStepResult fail(String errorKey) {
        return new TaskStepResult(0, 0, 0, 0, Outcome.FAIL, errorKey);
    }
}
