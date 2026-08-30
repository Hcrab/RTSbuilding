package com.rtsbuilding.rtsbuilding.server.task.identity;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * 服务端任务的稳定主键。
 *
 * <p>该类型只表达身份，不持有生命周期、玩家对象或执行器引用。阶段 1 可以先把它接入运行时，
 * 阶段 2 再由持久化仓库使用同一个主键，避免两套任务身份并存。</p>
 */
public record TaskId(UUID value) implements Comparable<TaskId> {

    private static final String SUBMISSION_NAMESPACE = "rtsbuilding:task-from-submission:";

    public TaskId {
        Objects.requireNonNull(value, "value");
    }

    /** 为不需要请求级幂等语义的内部任务生成主键。 */
    public static TaskId create() {
        return new TaskId(UUID.randomUUID());
    }

    /** 从存档中的规范 UUID 字符串恢复主键。 */
    public static TaskId parse(String value) {
        return new TaskId(UUID.fromString(Objects.requireNonNull(value, "value")));
    }

    /**
     * 从玩家与提交标识确定性派生任务主键。
     * 同一玩家重发同一 submission 时会得到完全相同的 TaskId，不同玩家则保持隔离。
     */
    public static TaskId fromSubmission(UUID ownerId, SubmissionId submissionId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(submissionId, "submissionId");
        String seed = SUBMISSION_NAMESPACE + ownerId + ':' + submissionId;
        return new TaskId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public int compareTo(TaskId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
