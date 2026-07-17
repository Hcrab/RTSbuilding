package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/** 删除 payload 后保留的终态回执；在 retention 到期前阻止 TaskId 或 submission 重放。 */
public record TaskTombstone(TaskId taskId, SubmissionId submissionId, UUID ownerId,
                            String dimensionId, long revision, TaskLifecycleState terminalState,
                            long completedGameTime, long retainedUntilGameTime) {
    public TaskTombstone {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(submissionId, "submissionId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(terminalState, "terminalState");
        if (dimensionId.isBlank() || dimensionId.length() > 256) {
            throw new IllegalArgumentException("dimensionId 无效");
        }
        NbtStringLimits.requireWritable(dimensionId, "dimensionId");
        Identifier dimension = Identifier.tryParse(dimensionId);
        if (dimension == null || !dimension.toString().equals(dimensionId)) {
            throw new IllegalArgumentException("dimensionId 必须是规范 Identifier");
        }
        if (revision < 1L) throw new IllegalArgumentException("revision 必须为正数");
        if (!terminalState.terminal()) throw new IllegalArgumentException("墓碑只能记录终态");
        if (completedGameTime < 0L) throw new IllegalArgumentException("completedGameTime 不能为负数");
        if (retainedUntilGameTime < completedGameTime) {
            throw new IllegalArgumentException("retention 不能早于完成时间");
        }
    }

    public boolean expiredAt(long gameTime) {
        return gameTime >= retainedUntilGameTime;
    }
}
