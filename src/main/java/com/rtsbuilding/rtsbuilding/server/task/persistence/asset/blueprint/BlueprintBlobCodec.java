package com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.NbtStringLimits;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskCodec;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetMetadata;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraftforge.common.util.Constants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/** 蓝图独立 blob 的精确 schema、硬上限和内容哈希校验。 */
public final class BlueprintBlobCodec {
    public static final int CURRENT_SCHEMA = 1;
    public static final long MAX_LOGICAL_BYTES = 128L * 1024L * 1024L;
    public static final long MAX_COMPRESSED_BYTES = 32L * 1024L * 1024L;
    /** NbtAccounter 统计解码对象开销；需为我们自己的 128 MiB 逻辑内容上限留出余量。 */
    public static final long MAX_DECODE_ACCOUNTING_BYTES = 256L * 1024L * 1024L;
    public static final int MAX_NBT_NODES = 2_000_000;
    public static final int MAX_BLOCKS = 1_000_000;
    private static final String HASH_DOMAIN = "RTSBuilding/blueprint-blob";
    private static final int HASH_VERSION = 1;
    private static final String KIND = "blueprint";
    private static final Set<String> EXACT_FIELDS = com.rtsbuilding.rtsbuilding.server.task.Java8Collections.setOf(
            "schema", "asset_id", "task_id", "block_count", "name", "source_name",
            "format", "sha256", "structure");

    private final TaskCodec boundedNbt = new TaskCodec();

    public BlueprintBlobRecord freeze(TaskId taskId, int blockCount, String name,
            String sourceName, String format, NBTTagCompound structure) {
        TaskAssetId assetId = TaskAssetId.forTask(taskId, KIND);
        String safeName = safe(name);
        String safeSourceName = safe(sourceName);
        String safeFormat = safe(format);
        validateLogical(assetId, taskId, blockCount, safeName, safeSourceName, safeFormat, structure);
        return new BlueprintBlobRecord(assetId, taskId, blockCount, safeName, safeSourceName, safeFormat,
                hashContent(assetId, taskId, blockCount, safeName, safeSourceName, safeFormat, structure),
                structure);
    }

    public NBTTagCompound encode(BlueprintBlobRecord record) {
        validateLogical(record);
        String actualHash = hashContent(record);
        if (!actualHash.equals(record.sha256())) {
            throw new BlobCodecException("蓝图 blob 内容与 sha256 不一致");
        }
        NBTTagCompound root = contentTag(record);
        root.setInteger("schema", CURRENT_SCHEMA);
        root.setString("sha256", record.sha256());
        return root;
    }

    public TaskAssetMetadata metadata(BlueprintBlobRecord record, long compressedBytes) {
        validateLogical(record);
        return new TaskAssetMetadata(record.assetId(), record.taskId(), KIND, record.sha256(),
                compressedBytes, logicalBytes(record));
    }

    public long logicalBytes(BlueprintBlobRecord record) {
        validateLogical(record);
        return boundedNbt.estimatePayloadBytes(
                record.structureView(), MAX_LOGICAL_BYTES, MAX_NBT_NODES);
    }

    public BlueprintBlobRecord decode(NBTTagCompound root) {
        try {
            if (!root.getKeySet().equals(EXACT_FIELDS)) {
                throw new BlobCodecException("蓝图 blob 包含缺失或未知字段");
            }
            require(root, "schema", Constants.NBT.TAG_INT);
            if (root.getInteger("schema") != CURRENT_SCHEMA) {
                throw new BlobCodecException("不支持的蓝图 blob schema: " + root.getInteger("schema"));
            }
            require(root, "asset_id", Constants.NBT.TAG_INT_ARRAY);
            require(root, "task_id", Constants.NBT.TAG_INT_ARRAY);
            if (!hasUuid(root, "asset_id") || !hasUuid(root, "task_id")) {
                throw new BlobCodecException("蓝图 blob UUID 字段损坏");
            }
            require(root, "block_count", Constants.NBT.TAG_INT);
            require(root, "name", Constants.NBT.TAG_STRING);
            require(root, "source_name", Constants.NBT.TAG_STRING);
            require(root, "format", Constants.NBT.TAG_STRING);
            require(root, "sha256", Constants.NBT.TAG_STRING);
            require(root, "structure", Constants.NBT.TAG_COMPOUND);
            BlueprintBlobRecord record = new BlueprintBlobRecord(
                    new TaskAssetId(getUuid(root, "asset_id")),
                    new TaskId(getUuid(root, "task_id")),
                    root.getInteger("block_count"),
                    root.getString("name"), root.getString("source_name"), root.getString("format"),
                    root.getString("sha256"), root.getCompoundTag("structure"));
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
            writeCompressed(record, output);
            byte[] bytes = output.toByteArray();
            if (bytes.length > MAX_COMPRESSED_BYTES) throw new BlobCodecException("蓝图 blob 压缩文件超过 32 MiB");
            return bytes;
        } catch (IOException failure) {
            throw new BlobCodecException("编码蓝图 blob 失败", failure);
        }
    }

    /** 后台磁盘写入入口；不关闭调用方持有的流，也不在内存中构造第二份压缩数组。 */
    public void writeCompressed(BlueprintBlobRecord record, OutputStream output) throws IOException {
        Objects.requireNonNull(output, "output");
        CompressedStreamTools.writeCompressed(encode(record), new FilterOutputStream(output) {
            @Override
            public void close() throws IOException {
                flush();
            }
        });
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
            // 1.12 的 compressed 便捷入口没有大小跟踪参数，显式解开 gzip 后走受限 DataInput。
            DataInputStream data = new DataInputStream(new GZIPInputStream(input));
            NBTTagCompound root = CompressedStreamTools.read(
                    data, new NBTSizeTracker(MAX_DECODE_ACCOUNTING_BYTES));
            if (root == null) throw new BlobCodecException("蓝图 blob NBT 根标签为空");
            return decode(root);
        } catch (IOException failure) {
            throw new BlobCodecException("解码蓝图 blob 失败", failure);
        }
    }

    private void validateLogical(BlueprintBlobRecord record) {
        validateLogical(record.assetId(), record.taskId(), record.blockCount(), record.name(),
                record.sourceName(), record.format(), record.structureView());
    }

    private void validateLogical(TaskAssetId assetId, TaskId taskId, int blockCount, String name,
            String sourceName, String format, NBTTagCompound structure) {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(structure, "structure");
        if (blockCount <= 0 || blockCount > MAX_BLOCKS) {
            throw new BlobCodecException("蓝图 blob 方块数越界");
        }
        NbtStringLimits.requireWritable(name, "blob name");
        NbtStringLimits.requireWritable(sourceName, "blob sourceName");
        NbtStringLimits.requireWritable(format, "blob format");
        try {
            BlueprintFormat.valueOf(format);
        } catch (IllegalArgumentException failure) {
            throw new BlobCodecException("不支持的蓝图格式: " + format, failure);
        }
        if (!TaskAssetId.forTask(taskId, KIND).equals(assetId)) {
            throw new BlobCodecException("蓝图 blob ID 不是由 TaskId 确定性派生");
        }
        boundedNbt.estimatePayloadBytes(structure, MAX_LOGICAL_BYTES, MAX_NBT_NODES);
    }

    private static NBTTagCompound contentTag(BlueprintBlobRecord record) {
        NBTTagCompound content = new NBTTagCompound();
        setUuid(content, "asset_id", record.assetId().value());
        setUuid(content, "task_id", record.taskId().value());
        content.setInteger("block_count", record.blockCount());
        content.setString("name", record.name());
        content.setString("source_name", record.sourceName());
        content.setString("format", record.format());
        content.setTag("structure", record.structureView());
        return content;
    }

    private static String hashContent(BlueprintBlobRecord record) {
        return hashContent(record.assetId(), record.taskId(), record.blockCount(), record.name(),
                record.sourceName(), record.format(), record.structureView());
    }

    private static String hashContent(TaskAssetId assetId, TaskId taskId, int blockCount, String name,
            String sourceName, String format, NBTTagCompound structure) {
        NBTTagCompound content = new NBTTagCompound();
        setUuid(content, "asset_id", assetId.value());
        setUuid(content, "task_id", taskId.value());
        content.setInteger("block_count", blockCount);
        content.setString("name", name);
        content.setString("source_name", sourceName);
        content.setString("format", format);
        content.setTag("structure", structure);
        return CanonicalNbtHasher.sha256(HASH_DOMAIN, HASH_VERSION, content);
    }

    private static void require(NBTTagCompound root, String key, int type) {
        if (!root.hasKey(key, type)) throw new BlobCodecException("缺少或错误的 blob 字段: " + key);
    }

    /** 与主线现代 NBT UUID 的单字段 int-array 表示保持一致，避免 1.12 Most/Least 展开改变 schema/hash。 */
    private static void setUuid(NBTTagCompound tag, String key, UUID value) {
        long most = value.getMostSignificantBits();
        long least = value.getLeastSignificantBits();
        tag.setIntArray(key, new int[] {
                (int) (most >>> 32), (int) most, (int) (least >>> 32), (int) least
        });
    }

    private static boolean hasUuid(NBTTagCompound tag, String key) {
        return tag.hasKey(key, Constants.NBT.TAG_INT_ARRAY) && tag.getIntArray(key).length == 4;
    }

    private static UUID getUuid(NBTTagCompound tag, String key) {
        if (!hasUuid(tag, key)) throw new BlobCodecException("蓝图 blob UUID 字段损坏: " + key);
        int[] values = tag.getIntArray(key);
        long most = ((long) values[0] << 32) | (values[1] & 0xffffffffL);
        long least = ((long) values[2] << 32) | (values[3] & 0xffffffffL);
        return new UUID(most, least);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class BlobCodecException extends IllegalArgumentException {
        public BlobCodecException(String message) { super(message); }
        public BlobCodecException(String message, Throwable cause) { super(message, cause); }
    }
}
