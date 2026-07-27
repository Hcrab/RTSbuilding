package com.rtsbuilding.rtsbuilding.server.task.persistence;

import java.util.Objects;

/** Command Gateway 提交结果；inserted=false 表示同一活跃 submission 的幂等重发。 */
public final class TaskAdmissionResult {
    private final TaskSnapshot snapshot;
    private final boolean inserted;

    public TaskAdmissionResult(TaskSnapshot snapshot, boolean inserted) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.inserted = inserted;
    }

    public TaskSnapshot snapshot() { return snapshot; }
    public boolean inserted() { return inserted; }
}
