package com.rtsbuilding.rtsbuilding.server.task.persistence.asset;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;

import java.util.Locale;
import java.util.Objects;

/** 活动外置资产的轻量权威元数据；不持有 blob 内容，也不接触世界、玩家或 Session。 */
public record TaskAssetMetadata(
        TaskAssetId assetId,
        TaskId taskId,
        String kind,
        String sha256,
        long compressedBytes,
        long logicalBytes) {

    public TaskAssetMetadata {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sha256, "sha256");
        if (!kind.matches("[a-z0-9_./-]{1,64}")) {
            throw new IllegalArgumentException("asset kind 必须是稳定的小写标识");
        }
        if (!TaskAssetId.forTask(taskId, kind).equals(assetId)) {
            throw new IllegalArgumentException("assetId 不是由 taskId + kind 确定性派生");
        }
        sha256 = sha256.toLowerCase(Locale.ROOT);
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("asset sha256 必须为 64 位小写十六进制");
        }
        if (compressedBytes <= 0L || logicalBytes <= 0L) {
            throw new IllegalArgumentException("asset 字节数必须为正数");
        }
    }
}
