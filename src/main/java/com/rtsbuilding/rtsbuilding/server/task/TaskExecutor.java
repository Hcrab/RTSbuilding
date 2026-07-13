package com.rtsbuilding.rtsbuilding.server.task;

/** 一种任务的类型执行器；执行器不得直接维护第二份任务生命周期。 */
@FunctionalInterface
public interface TaskExecutor {
    TaskStepResult execute(TaskRecord task, TaskBudget budget);
}
