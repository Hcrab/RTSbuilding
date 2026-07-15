package com.rtsbuilding.rtsbuilding.server.task.persistence.asset;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * root v2 中活动资产的唯一权威目录。
 *
 * <p>物理目录扫描只负责孤儿/临时文件维护，不能替代本 manifest。总配额在构造和每次增量变更时
 * 同步验证，避免合法 commit 在下次启动时反向锁死仓库。</p>
 */
public final class TaskAssetManifest {
    public static final int MAX_ASSETS = 100_000;
    public static final long MAX_COMPRESSED_BYTES = 4L * 1024L * 1024L * 1024L;
    public static final long MAX_LOGICAL_BYTES = 16L * 1024L * 1024L * 1024L;

    private final Map<TaskAssetId, TaskAssetMetadata> entries;
    private final long compressedBytes;
    private final long logicalBytes;

    public TaskAssetManifest(Map<TaskAssetId, TaskAssetMetadata> entries) {
        Objects.requireNonNull(entries, "entries");
        LinkedHashMap<TaskAssetId, TaskAssetMetadata> copy = new LinkedHashMap<>();
        long compressed = 0L;
        long logical = 0L;
        for (Map.Entry<TaskAssetId, TaskAssetMetadata> entry : entries.entrySet()) {
            TaskAssetId id = Objects.requireNonNull(entry.getKey(), "assetId");
            TaskAssetMetadata metadata = Objects.requireNonNull(entry.getValue(), "assetMetadata");
            if (!id.equals(metadata.assetId())) throw new IllegalArgumentException("manifest key 与 assetId 不一致");
            if (copy.putIfAbsent(id, metadata) != null) throw new IllegalArgumentException("重复 assetId: " + id);
            compressed = addBounded(compressed, metadata.compressedBytes(), MAX_COMPRESSED_BYTES,
                    "活动资产压缩总量超过 4 GiB");
            logical = addBounded(logical, metadata.logicalBytes(), MAX_LOGICAL_BYTES,
                    "活动资产逻辑总量超过 16 GiB");
        }
        if (copy.size() > MAX_ASSETS) throw new IllegalArgumentException("活动资产数量超过 100000");
        this.entries = Map.copyOf(copy);
        this.compressedBytes = compressed;
        this.logicalBytes = logical;
    }

    public static TaskAssetManifest empty() {
        return new TaskAssetManifest(Map.of());
    }

    public Map<TaskAssetId, TaskAssetMetadata> entries() {
        return entries;
    }

    public long compressedBytes() {
        return compressedBytes;
    }

    public long logicalBytes() {
        return logicalBytes;
    }

    public TaskAssetManifest apply(
            Collection<TaskAssetMetadata> upserts, Collection<TaskAssetId> removals) {
        Objects.requireNonNull(upserts, "upserts");
        Objects.requireNonNull(removals, "removals");
        Set<TaskAssetId> removed = new LinkedHashSet<>(removals);
        LinkedHashMap<TaskAssetId, TaskAssetMetadata> next = new LinkedHashMap<>(entries);
        for (TaskAssetId assetId : removed) next.remove(Objects.requireNonNull(assetId, "removedAssetId"));
        for (TaskAssetMetadata metadata : upserts) {
            Objects.requireNonNull(metadata, "assetMetadata");
            if (removed.contains(metadata.assetId())) {
                throw new IllegalArgumentException("同一 commit 不能同时删除并写入 assetId: " + metadata.assetId());
            }
            TaskAssetMetadata existing = next.putIfAbsent(metadata.assetId(), metadata);
            if (existing != null && !existing.equals(metadata)) {
                throw new IllegalArgumentException("write-once assetId 已绑定不同 metadata: " + metadata.assetId());
            }
        }
        return new TaskAssetManifest(next);
    }

    public void requireOwnedBy(Set<TaskId> activeTaskIds) {
        Objects.requireNonNull(activeTaskIds, "activeTaskIds");
        for (TaskAssetMetadata metadata : entries.values()) {
            if (!activeTaskIds.contains(metadata.taskId())) {
                throw new IllegalArgumentException("活动资产引用不存在的 task: " + metadata.taskId());
            }
        }
    }

    private static long addBounded(long left, long right, long max, String message) {
        if (right > max - left) throw new IllegalArgumentException(message);
        return left + right;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TaskAssetManifest manifest && entries.equals(manifest.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "TaskAssetManifest[entries=" + entries.size() + ", compressedBytes=" + compressedBytes
                + ", logicalBytes=" + logicalBytes + ']';
    }
}
