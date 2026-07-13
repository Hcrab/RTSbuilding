package com.rtsbuilding.rtsbuilding.server.task;

/** 短任务保持瞬态；长任务、暂停、缺料或错误任务才投影到工作流面板。 */
public enum TaskVisibility {
    TRANSIENT,
    PERSISTENT
}
