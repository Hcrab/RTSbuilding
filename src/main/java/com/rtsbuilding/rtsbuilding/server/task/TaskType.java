package com.rtsbuilding.rtsbuilding.server.task;

/** 服务端任务的稳定类别；UI 与持久化只投影这些类别，不拥有执行状态。 */
public enum TaskType {
    PLACEMENT,
    DESTRUCTION,
    MINING,
    BLUEPRINT,
    BUFFER_DRAIN
}
