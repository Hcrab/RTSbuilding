package com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.Objects;

/** 冻结的蓝图资产；完整 structure 只存在于独立 blob 文件，不得进入普通 TaskSnapshot。 */
public record BlueprintBlobRecord(
        TaskAssetId assetId,
        TaskId taskId,
        int blockCount,
        String name,
        String sourceName,
        String format,
        String sha256,
        CompoundTag structure) {

    public BlueprintBlobRecord {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(structure, "structure");
        if (blockCount <= 0) throw new IllegalArgumentException("blockCount 必须为正数");
        if (name.length() > 256 || sourceName.length() > 512 || format.isBlank() || format.length() > 64) {
            throw new IllegalArgumentException("蓝图 blob 文本元数据越界");
        }
        if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 必须为小写十六进制");
        if (structure.isEmpty()) throw new IllegalArgumentException("蓝图 structure 不能为空");
        sha256 = sha256.toLowerCase(Locale.ROOT);
        structure = structure.copy();
    }

    @Override
    public CompoundTag structure() {
        return structure.copy();
    }

    CompoundTag structureView() {
        return structure;
    }
}
