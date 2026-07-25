package com.rtsbuilding.rtsbuilding.server.task.persistence.asset;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * durable task 外置不可变资产的稳定主键。
 *
 * <p>阶段 1 使用每任务独占资产，不共享、不做引用计数。相同 TaskId 与 kind 必须得到相同 ID，确保网络重试、
 * legacy 重迁移和 blob→manifest 崩溃恢复不会创建第二份身份。</p>
 */
public record TaskAssetId(UUID value) implements Comparable<TaskAssetId> {
    private static final String NAMESPACE = "rtsbuilding:task-asset:v1:";

    public TaskAssetId {
        Objects.requireNonNull(value, "value");
    }

    public static TaskAssetId forTask(TaskId taskId, String kind) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(kind, "kind");
        if (kind.isBlank() || kind.length() > 64) throw new IllegalArgumentException("asset kind 无效");
        String seed = NAMESPACE + kind + ':' + taskId;
        return new TaskAssetId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)));
    }

    public static TaskAssetId parse(String value) {
        return new TaskAssetId(UUID.fromString(Objects.requireNonNull(value, "value")));
    }

    @Override
    public int compareTo(TaskAssetId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
