package com.rtsbuilding.rtsbuilding.server.task;

/**
 * 单次调度片的结果。
 *
 * <p>{@code processedUnits} 用于扣减本 Tick 的全局执行预算；{@code progressUnits}
 * 只表示任务游标真正向前推进的数量。两者通常相同，但资源不足导致执行回退时，
 * 前者仍需计入本次尝试，后者必须为零。</p>
 */
public record TaskStepResult(int processedUnits, int progressUnits, Outcome outcome, String errorKey) {
    public enum Outcome { CONTINUE, YIELD, COMPLETE, WAIT_RESOURCE, FAIL }

    public TaskStepResult {
        if (processedUnits < 0) throw new IllegalArgumentException("processedUnits < 0");
        if (progressUnits < 0) throw new IllegalArgumentException("progressUnits < 0");
        if (progressUnits > processedUnits) throw new IllegalArgumentException("progressUnits > processedUnits");
        if (outcome != Outcome.FAIL && errorKey != null) {
            throw new IllegalArgumentException("只有失败结果可以携带 errorKey");
        }
    }

    public static TaskStepResult continueWith(int units) {
        return new TaskStepResult(units, units, Outcome.CONTINUE, null);
    }

    public static TaskStepResult continueWith(int processedUnits, int progressUnits) {
        return new TaskStepResult(processedUnits, progressUnits, Outcome.CONTINUE, null);
    }

    public static TaskStepResult yield(int units) {
        return new TaskStepResult(units, units, Outcome.YIELD, null);
    }

    public static TaskStepResult complete(int units) {
        return new TaskStepResult(units, units, Outcome.COMPLETE, null);
    }

    public static TaskStepResult complete(int processedUnits, int progressUnits) {
        return new TaskStepResult(processedUnits, progressUnits, Outcome.COMPLETE, null);
    }

    public static TaskStepResult waitForResource() {
        return new TaskStepResult(0, 0, Outcome.WAIT_RESOURCE, null);
    }

    public static TaskStepResult waitForResource(int processedUnits, int progressUnits) {
        return new TaskStepResult(processedUnits, progressUnits, Outcome.WAIT_RESOURCE, null);
    }

    public static TaskStepResult fail(String errorKey) {
        return new TaskStepResult(0, 0, Outcome.FAIL, errorKey);
    }
}
