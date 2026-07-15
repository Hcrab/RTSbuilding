package com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.NbtStringLimits;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskCodec;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** 蓝图独立 blob 的精确 schema、硬上限和内容哈希校验。 */
public final class BlueprintBlobCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final long MAX_LOGICAL_BYTES = 128L * 1024L * 1024L;
    public static final long MAX_COMPRESSED_BYTES = 32L * 1024L * 1024L;
    /** NbtAccounter 统计解码对象开销；需为我们自己的 128 MiB 逻辑内容上限留出余量。 */
    public static final long MAX_DECODE_ACCOUNTING_BYTES = 256L * 1024L * 1024L;
    public static final int MAX_NBT_NODES = 2_000_000;
    public static final int MAX_BLOCKS = 1_000_000;
    private static final byte[] HASH_DOMAIN = "RTSBuilding/blueprint-blob".getBytes(StandardCharsets.UTF_8);
    private static final int HASH_VERSION = 1;
    private static final String KIND = "blueprint";
    private static final Set<String> EXACT_FIELDS = Set.of(
            "schema", "asset_id", "task_id", "block_count", "name", "source_name",
            "format", "sha256", "structure");

    private final TaskCodec boundedNbt = new TaskCodec();

    public BlueprintBlobRecord freeze(TaskId taskId, int blockCount, String name,
            String sourceName, String format, CompoundTag structure) {
        TaskAssetId assetId = TaskAssetId.forTask(taskId, KIND);
        String safeName = safe(name);
        String safeSourceName = safe(sourceName);
        String safeFormat = safe(format);
        BlueprintBlobRecord draft = new BlueprintBlobRecord(
                assetId, taskId, blockCount, safeName, safeSourceName, safeFormat,
                "0".repeat(64), structure);
        validateLogical(draft);
        return new BlueprintBlobRecord(assetId, taskId, blockCount, safeName, safeSourceName, safeFormat,
                hashContent(draft), structure);
    }

    public CompoundTag encode(BlueprintBlobRecord record) {
        validateLogical(record);
        String actualHash = hashContent(record);
        if (!actualHash.equals(record.sha256())) {
            throw new BlobCodecException("蓝图 blob 内容与 sha256 不一致");
        }
        CompoundTag root = contentTag(record);
        root.putInt("schema", CURRENT_SCHEMA);
        root.putString("sha256", record.sha256());
        return root;
    }

    public BlueprintBlobRecord decode(CompoundTag root) {
        try {
            if (!root.getAllKeys().equals(EXACT_FIELDS)) {
                throw new BlobCodecException("蓝图 blob 包含缺失或未知字段");
            }
            require(root, "schema", Tag.TAG_INT);
            if (root.getInt("schema") != CURRENT_SCHEMA) {
                throw new BlobCodecException("不支持的蓝图 blob schema: " + root.getInt("schema"));
            }
            require(root, "asset_id", Tag.TAG_INT_ARRAY);
            require(root, "task_id", Tag.TAG_INT_ARRAY);
            if (!root.hasUUID("asset_id") || !root.hasUUID("task_id")) {
                throw new BlobCodecException("蓝图 blob UUID 字段损坏");
            }
            require(root, "block_count", Tag.TAG_INT);
            require(root, "name", Tag.TAG_STRING);
            require(root, "source_name", Tag.TAG_STRING);
            require(root, "format", Tag.TAG_STRING);
            require(root, "sha256", Tag.TAG_STRING);
            require(root, "structure", Tag.TAG_COMPOUND);
            BlueprintBlobRecord record = new BlueprintBlobRecord(
                    new TaskAssetId(root.getUUID("asset_id")),
                    new TaskId(root.getUUID("task_id")),
                    root.getInt("block_count"),
                    root.getString("name"), root.getString("source_name"), root.getString("format"),
                    root.getString("sha256"), root.getCompound("structure"));
            validateLogical(record);
            if (!hashContent(record).equals(record.sha256())) {
                throw new BlobCodecException("蓝图 blob sha256 校验失败");
            }
            return record;
        } catch (BlobCodecException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new BlobCodecException("蓝图 blob 字段损坏", failure);
        }
    }

    public byte[] encodeCompressed(BlueprintBlobRecord record) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(encode(record), output);
            byte[] bytes = output.toByteArray();
            if (bytes.length > MAX_COMPRESSED_BYTES) throw new BlobCodecException("蓝图 blob 压缩文件超过 32 MiB");
            return bytes;
        } catch (IOException failure) {
            throw new BlobCodecException("编码蓝图 blob 失败", failure);
        }
    }

    /** 与磁盘 load 共用相同的解码及上限路径，供原子发布前做字节级预检。 */
    public BlueprintBlobRecord decodeCompressed(byte[] compressed) {
        if (compressed == null || compressed.length == 0 || compressed.length > MAX_COMPRESSED_BYTES) {
            throw new BlobCodecException("蓝图 blob 压缩文件大小越界");
        }
        return decodeCompressed(new ByteArrayInputStream(compressed), compressed.length);
    }

    BlueprintBlobRecord decodeCompressed(InputStream input, long compressedBytes) {
        if (compressedBytes <= 0L || compressedBytes > MAX_COMPRESSED_BYTES) {
            throw new BlobCodecException("蓝图 blob 压缩文件大小越界: " + compressedBytes);
        }
        try {
            CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.create(MAX_DECODE_ACCOUNTING_BYTES));
            if (root == null) throw new BlobCodecException("蓝图 blob NBT 根标签为空");
            return decode(root);
        } catch (IOException failure) {
            throw new BlobCodecException("解码蓝图 blob 失败", failure);
        }
    }

    private void validateLogical(BlueprintBlobRecord record) {
        if (record.blockCount() <= 0 || record.blockCount() > MAX_BLOCKS) {
            throw new BlobCodecException("蓝图 blob 方块数越界");
        }
        NbtStringLimits.requireWritable(record.name(), "blob name");
        NbtStringLimits.requireWritable(record.sourceName(), "blob sourceName");
        NbtStringLimits.requireWritable(record.format(), "blob format");
        try {
            BlueprintFormat.valueOf(record.format());
        } catch (IllegalArgumentException failure) {
            throw new BlobCodecException("不支持的蓝图格式: " + record.format(), failure);
        }
        if (!TaskAssetId.forTask(record.taskId(), KIND).equals(record.assetId())) {
            throw new BlobCodecException("蓝图 blob ID 不是由 TaskId 确定性派生");
        }
        boundedNbt.estimatePayloadBytes(record.structureView(), MAX_LOGICAL_BYTES, MAX_NBT_NODES);
    }

    private static CompoundTag contentTag(BlueprintBlobRecord record) {
        CompoundTag content = new CompoundTag();
        content.putUUID("asset_id", record.assetId().value());
        content.putUUID("task_id", record.taskId().value());
        content.putInt("block_count", record.blockCount());
        content.putString("name", record.name());
        content.putString("source_name", record.sourceName());
        content.putString("format", record.format());
        content.put("structure", record.structureView());
        return content;
    }

    private static String hashContent(BlueprintBlobRecord record) {
        return hashContent(record.assetId(), record.taskId(), record.blockCount(), record.name(),
                record.sourceName(), record.format(), record.structureView());
    }

    private static String hashContent(TaskAssetId assetId, TaskId taskId, int blockCount, String name,
            String sourceName, String format, CompoundTag structure) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            putBytes(digest, HASH_DOMAIN);
            putInt(digest, HASH_VERSION);
            CompoundTag content = new CompoundTag();
            content.putUUID("asset_id", assetId.value());
            content.putUUID("task_id", taskId.value());
            content.putInt("block_count", blockCount);
            content.putString("name", name);
            content.putString("source_name", sourceName);
            content.putString("format", format);
            content.put("structure", structure);
            hashTag(digest, content);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new BlobCodecException("计算蓝图 blob canonical hash 失败", failure);
        }
    }

    /** Compound key 排序，List 保序，所有数字显式大端编码，避免 hash 依赖压缩器或插入顺序。 */
    private static void hashTag(MessageDigest digest, Tag tag) {
        digest.update(tag.getId());
        switch (tag.getId()) {
            case Tag.TAG_END -> { }
            case Tag.TAG_BYTE -> digest.update(((NumericTag) tag).getAsByte());
            case Tag.TAG_SHORT -> putShort(digest, ((NumericTag) tag).getAsShort());
            case Tag.TAG_INT -> putInt(digest, ((NumericTag) tag).getAsInt());
            case Tag.TAG_LONG -> putLong(digest, ((NumericTag) tag).getAsLong());
            case Tag.TAG_FLOAT -> putInt(digest, Float.floatToRawIntBits(((NumericTag) tag).getAsFloat()));
            case Tag.TAG_DOUBLE -> putLong(digest, Double.doubleToRawLongBits(((NumericTag) tag).getAsDouble()));
            case Tag.TAG_BYTE_ARRAY -> {
                byte[] values = ((ByteArrayTag) tag).getAsByteArray();
                putInt(digest, values.length);
                digest.update(values);
            }
            case Tag.TAG_STRING -> putBytes(digest,
                    ((StringTag) tag).getAsString().getBytes(StandardCharsets.UTF_8));
            case Tag.TAG_LIST -> {
                ListTag list = (ListTag) tag;
                putInt(digest, list.size());
                for (Tag element : list) hashTag(digest, element);
            }
            case Tag.TAG_COMPOUND -> {
                CompoundTag compound = (CompoundTag) tag;
                List<String> keys = new ArrayList<>(compound.getAllKeys());
                keys.sort(String::compareTo);
                putInt(digest, keys.size());
                for (String key : keys) {
                    putBytes(digest, key.getBytes(StandardCharsets.UTF_8));
                    Tag value = compound.get(key);
                    if (value == null) throw new BlobCodecException("Compound key 缺失值: " + key);
                    hashTag(digest, value);
                }
            }
            case Tag.TAG_INT_ARRAY -> {
                int[] values = ((IntArrayTag) tag).getAsIntArray();
                putInt(digest, values.length);
                for (int value : values) putInt(digest, value);
            }
            case Tag.TAG_LONG_ARRAY -> {
                long[] values = ((LongArrayTag) tag).getAsLongArray();
                putInt(digest, values.length);
                for (long value : values) putLong(digest, value);
            }
            default -> throw new BlobCodecException("不支持参与 canonical hash 的 NBT 类型: " + tag.getId());
        }
    }

    private static void putBytes(MessageDigest digest, byte[] values) {
        putInt(digest, values.length);
        digest.update(values);
    }

    private static void putShort(MessageDigest digest, short value) {
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void putInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void putLong(MessageDigest digest, long value) {
        putInt(digest, (int) (value >>> 32));
        putInt(digest, (int) value);
    }

    private static void require(CompoundTag root, String key, int type) {
        if (!root.contains(key, type)) throw new BlobCodecException("缺少或错误的 blob 字段: " + key);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class BlobCodecException extends IllegalArgumentException {
        public BlobCodecException(String message) { super(message); }
        public BlobCodecException(String message, Throwable cause) { super(message, cause); }
    }
}
