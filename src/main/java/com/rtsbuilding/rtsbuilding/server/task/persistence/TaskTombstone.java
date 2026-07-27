package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;

import java.util.Objects;
import java.util.UUID;

/** 删除 payload 后保留的终态回执；在 retention 到期前阻止 TaskId 或 submission 重放。 */
public final class TaskTombstone {
    private final TaskId taskId;
    private final SubmissionId submissionId;
    private final UUID ownerId;
    private final String dimensionId;
    private final long revision;
    private final TaskLifecycleState terminalState;
    private final long completedGameTime;
    private final long retainedUntilGameTime;

    public TaskTombstone(TaskId taskId, SubmissionId submissionId, UUID ownerId,
            String dimensionId, long revision, TaskLifecycleState terminalState,
            long completedGameTime, long retainedUntilGameTime) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.submissionId = Objects.requireNonNull(submissionId, "submissionId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.terminalState = Objects.requireNonNull(terminalState, "terminalState");
        this.revision = revision;
        this.completedGameTime = completedGameTime;
        this.retainedUntilGameTime = retainedUntilGameTime;
        if (dimensionId.trim().isEmpty() || dimensionId.length() > 256) {
            throw new IllegalArgumentException("dimensionId 无效");
        }
        NbtStringLimits.requireWritable(dimensionId, "dimensionId");
        if (!DimensionIdCodec.isCanonical(dimensionId)) {
            throw new IllegalArgumentException("dimensionId 必须是规范整数或 ResourceLocation");
        }
        if (revision < 1L) throw new IllegalArgumentException("revision 必须为正数");
        if (!terminalState.terminal()) throw new IllegalArgumentException("墓碑只能记录终态");
        if (completedGameTime < 0L) throw new IllegalArgumentException("completedGameTime 不能为负数");
        if (retainedUntilGameTime < completedGameTime) {
            throw new IllegalArgumentException("retention 不能早于完成时间");
        }
    }

    public TaskId taskId() { return taskId; }
    public SubmissionId submissionId() { return submissionId; }
    public UUID ownerId() { return ownerId; }
    public String dimensionId() { return dimensionId; }
    public long revision() { return revision; }
    public TaskLifecycleState terminalState() { return terminalState; }
    public long completedGameTime() { return completedGameTime; }
    public long retainedUntilGameTime() { return retainedUntilGameTime; }

    public boolean expiredAt(long gameTime) {
        return gameTime >= retainedUntilGameTime;
    }
}
