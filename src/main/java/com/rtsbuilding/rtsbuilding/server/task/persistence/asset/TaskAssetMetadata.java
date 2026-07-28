package com.rtsbuilding.rtsbuilding.server.task.persistence.asset;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;

import java.util.Locale;
import java.util.Objects;

/** 活动外置资产的轻量权威元数据；不持有 blob 内容，也不接触世界、玩家或 Session。 */
public final class TaskAssetMetadata {
    private final TaskAssetId assetId;
    private final TaskId taskId;
    private final String kind;
    private final String sha256;
    private final long compressedBytes;
    private final long logicalBytes;

    public TaskAssetMetadata(TaskAssetId assetId, TaskId taskId, String kind,
            String sha256, long compressedBytes, long logicalBytes) {
        this.assetId = Objects.requireNonNull(assetId, "assetId");
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.kind = Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sha256, "sha256");
        if (!kind.matches("[a-z0-9_./-]{1,64}")) {
            throw new IllegalArgumentException("asset kind 必须是稳定的小写标识");
        }
        if (!TaskAssetId.forTask(taskId, kind).equals(assetId)) {
            throw new IllegalArgumentException("assetId 不是由 taskId + kind 确定性派生");
        }
        String normalizedHash = sha256.toLowerCase(Locale.ROOT);
        if (!normalizedHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("asset sha256 必须为 64 位小写十六进制");
        }
        if (compressedBytes <= 0L || logicalBytes <= 0L) {
            throw new IllegalArgumentException("asset 字节数必须为正数");
        }
        this.sha256 = normalizedHash;
        this.compressedBytes = compressedBytes;
        this.logicalBytes = logicalBytes;
    }

    public TaskAssetId assetId() { return assetId; }
    public TaskId taskId() { return taskId; }
    public String kind() { return kind; }
    public String sha256() { return sha256; }
    public long compressedBytes() { return compressedBytes; }
    public long logicalBytes() { return logicalBytes; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TaskAssetMetadata)) return false;
        TaskAssetMetadata that = (TaskAssetMetadata) other;
        return compressedBytes == that.compressedBytes
                && logicalBytes == that.logicalBytes
                && assetId.equals(that.assetId)
                && taskId.equals(that.taskId)
                && kind.equals(that.kind)
                && sha256.equals(that.sha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetId, taskId, kind, sha256, compressedBytes, logicalBytes);
    }
}
