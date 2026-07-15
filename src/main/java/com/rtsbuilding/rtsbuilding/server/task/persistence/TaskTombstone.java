package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;

import java.util.Objects;

/** 删除 payload 前必须先持久化的终态墓碑，防止重启后复活旧任务。 */
public record TaskTombstone(TaskId taskId, long revision, TaskLifecycleState terminalState,
                            long completedGameTime) {
    public TaskTombstone {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(terminalState, "terminalState");
        if (revision < 1L) throw new IllegalArgumentException("revision 必须为正数");
        if (!terminalState.terminal()) throw new IllegalArgumentException("墓碑只能记录终态");
        if (completedGameTime < 0L) throw new IllegalArgumentException("completedGameTime 不能为负数");
    }
}
