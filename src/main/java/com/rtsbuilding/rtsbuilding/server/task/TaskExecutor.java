package com.rtsbuilding.rtsbuilding.server.task;

/**
 * 一种任务的类型执行器；执行器不得直接维护第二份任务生命周期。
 *
 * <p>预算是合作式约束。任何循环都必须在每个会产生世界/储存副作用的 unit 前检查
 * {@link TaskBudget#hasTime()}，并且最多执行 {@link TaskBudget#maxUnits()} 个 unit；
 * 不能先超额修改世界再依赖返回值回滚。</p>
 */
@FunctionalInterface
public interface TaskExecutor {
    TaskStepResult execute(TaskRecord task, TaskBudget budget);
}
