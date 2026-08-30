package com.rtsbuilding.rtsbuilding.server.task.persistence;

import java.util.Objects;

/** Command Gateway 提交结果；inserted=false 表示同一活跃 submission 的幂等重发。 */
public record TaskAdmissionResult(TaskSnapshot snapshot, boolean inserted) {
    public TaskAdmissionResult {
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
