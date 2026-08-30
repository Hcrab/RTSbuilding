package com.rtsbuilding.rtsbuilding.server.task.identity;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * 一次玩家提交的稳定标识。
 *
 * <p>网络重发、断线恢复和旧存档迁移必须复用同一个标识，不能因为重复进入入口而创建第二个任务。
 * 该值对象不负责持久化或调度；调用方仍需把玩家 UUID 与它组合成完整的幂等键。</p>
 */
public record SubmissionId(UUID value) implements Comparable<SubmissionId> {

    private static final String LEGACY_NAMESPACE = "rtsbuilding:legacy-submission:";

    public SubmissionId {
        Objects.requireNonNull(value, "value");
    }

    /** 为一次全新的用户操作生成提交标识。 */
    public static SubmissionId create() {
        return new SubmissionId(UUID.randomUUID());
    }

    /** 从协议或存档中的规范 UUID 字符串恢复提交标识。 */
    public static SubmissionId parse(String value) {
        return new SubmissionId(UUID.fromString(Objects.requireNonNull(value, "value")));
    }

    /**
     * 为没有提交标识的旧作业生成确定性标识。
     *
     * <p>{@code domain} 用来隔离 placement/mining 等命名空间；{@code legacyIdentity}
     * 必须来自旧记录自身的稳定字段，不能包含当前时间或对象地址。</p>
     */
    public static SubmissionId fromLegacy(UUID ownerId, String domain, String legacyIdentity) {
        Objects.requireNonNull(ownerId, "ownerId");
        String normalizedDomain = requirePart(domain, "domain");
        String normalizedIdentity = requirePart(legacyIdentity, "legacyIdentity");
        String seed = LEGACY_NAMESPACE + ownerId + ':' + normalizedDomain + ':' + normalizedIdentity;
        return new SubmissionId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)));
    }

    private static String requirePart(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return value;
    }

    @Override
    public int compareTo(SubmissionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
