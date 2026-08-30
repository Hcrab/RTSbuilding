package com.rtsbuilding.rtsbuilding.server.task.persistence;

/** submission 或 TaskId 已有 durable receipt，拒绝把已终结操作重新执行。 */
public final class TaskReplayException extends IllegalStateException {
    public TaskReplayException(String message) {
        super(message);
    }
}
