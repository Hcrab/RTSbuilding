package com.rtsbuilding.rtsbuilding.server.task;

/** 单次调度片的结果。 */
public record TaskStepResult(int processedUnits, Outcome outcome, String errorKey) {
    public enum Outcome { CONTINUE, YIELD, COMPLETE, WAIT_RESOURCE, FAIL }

    public TaskStepResult {
        if (processedUnits < 0) throw new IllegalArgumentException("processedUnits < 0");
        if (outcome != Outcome.FAIL && errorKey != null) {
            throw new IllegalArgumentException("只有失败结果可以携带 errorKey");
        }
    }

    public static TaskStepResult continueWith(int units) {
        return new TaskStepResult(units, Outcome.CONTINUE, null);
    }

    public static TaskStepResult yield(int units) {
        return new TaskStepResult(units, Outcome.YIELD, null);
    }

    public static TaskStepResult complete(int units) {
        return new TaskStepResult(units, Outcome.COMPLETE, null);
    }

    public static TaskStepResult waitForResource() {
        return new TaskStepResult(0, Outcome.WAIT_RESOURCE, null);
    }

    public static TaskStepResult fail(String errorKey) {
        return new TaskStepResult(0, Outcome.FAIL, errorKey);
    }
}
