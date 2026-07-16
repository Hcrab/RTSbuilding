package com.rtsbuilding.rtsbuilding.server.task.buffer;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * 一次 legacy buffer handoff 的稳定身份。
 *
 * <p>身份仅由 owner、首次入队时间和精确来源指纹派生，刻意不接受维度参数。相同来源在登出、
 * 切维或重启重试时会命中同一身份；内容变化则必须形成另一身份。</p>
 */
public record LegacyBufferMigrationIdentity(UUID value) {
    private static final String DOMAIN = "rtsbuilding:legacy-buffer-handoff:v1";

    public LegacyBufferMigrationIdentity {
        Objects.requireNonNull(value, "value");
    }

    public static LegacyBufferMigrationIdentity derive(
            UUID ownerId, long firstQueuedGameTime, LegacyBufferSourceFingerprint sourceFingerprint) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        if (firstQueuedGameTime < 0L) throw new IllegalArgumentException("firstQueuedGameTime 不能为负数");
        String material = DOMAIN + '\n' + ownerId + '\n' + firstQueuedGameTime
                + '\n' + sourceFingerprint.sha256();
        return new LegacyBufferMigrationIdentity(
                UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8)));
    }
}
