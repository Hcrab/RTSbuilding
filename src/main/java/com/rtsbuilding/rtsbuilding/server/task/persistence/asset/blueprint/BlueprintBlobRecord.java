package com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Locale;
import java.util.Objects;

/** 冻结的蓝图资产；完整 structure 只存在于独立 blob 文件，不得进入普通 TaskSnapshot。 */
public final class BlueprintBlobRecord {
    private final TaskAssetId assetId;
    private final TaskId taskId;
    private final int blockCount;
    private final String name;
    private final String sourceName;
    private final String format;
    private final String sha256;
    private final NBTTagCompound structure;

    public BlueprintBlobRecord(TaskAssetId assetId, TaskId taskId, int blockCount,
            String name, String sourceName, String format, String sha256,
            NBTTagCompound structure) {
        this.assetId = Objects.requireNonNull(assetId, "assetId");
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.name = Objects.requireNonNull(name, "name");
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
        this.format = Objects.requireNonNull(format, "format");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(structure, "structure");
        if (blockCount <= 0) throw new IllegalArgumentException("blockCount 必须为正数");
        if (name.length() > 256 || sourceName.length() > 512 || format.trim().isEmpty() || format.length() > 64) {
            throw new IllegalArgumentException("蓝图 blob 文本元数据越界");
        }
        if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 必须为小写十六进制");
        if (structure.hasNoTags()) throw new IllegalArgumentException("蓝图 structure 不能为空");
        this.blockCount = blockCount;
        this.sha256 = sha256.toLowerCase(Locale.ROOT);
        this.structure = structure.copy();
    }

    public TaskAssetId assetId() { return assetId; }
    public TaskId taskId() { return taskId; }
    public int blockCount() { return blockCount; }
    public String name() { return name; }
    public String sourceName() { return sourceName; }
    public String format() { return format; }
    public String sha256() { return sha256; }

    public NBTTagCompound structure() {
        return structure.copy();
    }

    NBTTagCompound structureView() {
        return structure;
    }
}
