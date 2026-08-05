package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetManifest;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetMetadata;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 版本化 durable task NBT 编解码器；未知版本或损坏字段必须 fail closed。 */
public final class TaskCodec {
    public static final int LEGACY_SCHEMA = 1;
    public static final int CURRENT_SCHEMA = 2;
    public static final int MAX_TASKS = 100_000;
    public static final int MAX_MIGRATIONS = 4_096;
    /** 大型 plan 必须外置为引用，不能把任意大 NBT 塞进每 Tick checkpoint。 */
    public static final long MAX_TASK_PAYLOAD_BYTES = 4L * 1024L * 1024L;
    public static final long MAX_IMAGE_ESTIMATED_BYTES = 96L * 1024L * 1024L;
    private static final int MAX_NBT_DEPTH = 64;
    private static final int MAX_NBT_NODES = 100_000;

    private static final String TASKS = "tasks";
    private static final String TOMBSTONES = "tombstones";
    private static final String MIGRATIONS = "completed_migrations";
    private static final String ASSETS = "assets";
    private static final Set<String> ROOT_V1_FIELDS = com.rtsbuilding.rtsbuilding.server.task.Java8Collections.setOf(
            "schema", TASKS, TOMBSTONES, MIGRATIONS);
    private static final Set<String> ROOT_V2_FIELDS = com.rtsbuilding.rtsbuilding.server.task.Java8Collections.setOf(
            "schema", TASKS, TOMBSTONES, MIGRATIONS, ASSETS);
    private static final Set<String> ASSET_FIELDS = com.rtsbuilding.rtsbuilding.server.task.Java8Collections.setOf(
            "asset_id", "task_id", "kind", "sha256", "compressed_bytes", "logical_bytes");
    private static final Set<String> SNAPSHOT_REQUIRED_FIELDS = com.rtsbuilding.rtsbuilding.server.task.Java8Collections.setOf(
            "id", "submission", "owner", "dimension", "type", "state", "revision",
            "created_game_time", "updated_game_time", "total", "cursor", "succeeded", "failed", "payload");
    private static final Set<String> WAIT_FIELDS = com.rtsbuilding.rtsbuilding.server.task.Java8Collections.setOf("kind", "value");
    private static final Set<String> TOMBSTONE_FIELDS = com.rtsbuilding.rtsbuilding.server.task.Java8Collections.setOf(
            "id", "submission", "owner", "dimension", "revision", "state",
            "completed_game_time", "retained_until");

    public NBTTagCompound encodeImage(TaskRepository.Image image) {
        if ((long) image.tasks().size() + image.tombstones().size() > MAX_TASKS) {
            throw new TaskCodecException("task 存档超过数量上限");
        }
        if (image.completedMigrations().size() > MAX_MIGRATIONS) {
            throw new TaskCodecException("迁移台账超过数量上限");
        }
        requireImageBudget(image, MAX_IMAGE_ESTIMATED_BYTES);
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("schema", CURRENT_SCHEMA);
        NBTTagList tasks = new NBTTagList();
        image.tasks().values().stream()
                .sorted(Comparator.comparing(TaskSnapshot::id))
                .map(this::encodeSnapshotUnchecked)
                .forEach(tasks::appendTag);
        root.setTag(TASKS, tasks);

        NBTTagList tombstones = new NBTTagList();
        image.tombstones().values().stream()
                .sorted(Comparator.comparing(TaskTombstone::taskId))
                .map(this::encodeTombstone)
                .forEach(tombstones::appendTag);
        root.setTag(TOMBSTONES, tombstones);

        NBTTagList migrations = new NBTTagList();
        image.completedMigrations().stream().sorted().forEach(migration -> {
            if (migration.trim().isEmpty() || migration.length() > 128) {
                throw new TaskCodecException("迁移标识无效");
            }
            NbtStringLimits.requireWritable(migration, "migrationId");
            migrations.appendTag(new NBTTagString(migration));
        });
        root.setTag(MIGRATIONS, migrations);

        NBTTagList assets = new NBTTagList();
        image.assets().entries().values().stream()
                .sorted(Comparator.comparing(TaskAssetMetadata::assetId))
                .map(TaskCodec::encodeAsset)
                .forEach(assets::appendTag);
        root.setTag(ASSETS, assets);
        return root;
    }

    public TaskRepository.Image decodeImage(NBTTagCompound root) {
        try {
            int schema = requireInt(root, "schema");
            Set<String> expectedRootFields;
            if (schema == LEGACY_SCHEMA) expectedRootFields = ROOT_V1_FIELDS;
            else if (schema == CURRENT_SCHEMA) expectedRootFields = ROOT_V2_FIELDS;
            else throw new TaskCodecException("不支持的 task schema: " + schema);
            if (!root.func_150296_c().equals(expectedRootFields)) {
                throw new TaskCodecException("task root 缺少字段或包含当前 schema 未知字段");
            }

            NBTTagList encodedTasks = requireList(root, TASKS, Constants.NBT.TAG_COMPOUND);
            NBTTagList encodedTombstones = requireList(root, TOMBSTONES, Constants.NBT.TAG_COMPOUND);
            if ((long) encodedTasks.tagCount() + encodedTombstones.tagCount() > MAX_TASKS) {
                throw new TaskCodecException("task 存档超过数量上限");
            }

            Map<TaskId, TaskSnapshot> tasks = new LinkedHashMap<>();
            long imageBytes = 0L;
            for (int i = 0; i < encodedTasks.tagCount(); i++) {
                TaskSnapshot snapshot = decodeSnapshot(encodedTasks.getCompoundTagAt(i));
                imageBytes = addSaturated(imageBytes, estimateSnapshotBytes(snapshot));
                if (imageBytes > MAX_IMAGE_ESTIMATED_BYTES) {
                    throw new TaskCodecException("task 存档超过总量上限");
                }
                if (tasks.putIfAbsent(snapshot.id(), snapshot) != null) {
                    throw new TaskCodecException("重复 TaskId: " + snapshot.id());
                }
            }

            Map<TaskId, TaskTombstone> tombstones = new LinkedHashMap<>();
            for (int i = 0; i < encodedTombstones.tagCount(); i++) {
                TaskTombstone tombstone = decodeTombstone(encodedTombstones.getCompoundTagAt(i));
                if (tombstones.putIfAbsent(tombstone.taskId(), tombstone) != null) {
                    throw new TaskCodecException("重复墓碑: " + tombstone.taskId());
                }
                TaskSnapshot task = tasks.get(tombstone.taskId());
                if (task != null) {
                    throw new TaskCodecException("墓碑与仍存活任务冲突: " + tombstone.taskId());
                }
            }
            imageBytes = addSaturated(imageBytes, encodedTombstones.tagCount() * 256L);

            Set<String> migrations = new LinkedHashSet<>();
            NBTTagList encodedMigrations = requireList(root, MIGRATIONS, Constants.NBT.TAG_STRING);
            if (encodedMigrations.tagCount() > MAX_MIGRATIONS) {
                throw new TaskCodecException("迁移台账超过数量上限");
            }
            for (int i = 0; i < encodedMigrations.tagCount(); i++) {
                String migration = encodedMigrations.getStringTagAt(i);
                if (migration.trim().isEmpty()) throw new TaskCodecException("迁移标识不能为空");
                if (migration.length() > 128) throw new TaskCodecException("迁移标识过长");
                NbtStringLimits.requireWritable(migration, "migrationId");
                if (!migrations.add(migration)) {
                    throw new TaskCodecException("重复迁移标识: " + migration);
                }
                imageBytes = addSaturated(
                        imageBytes, NbtStringLimits.modifiedUtfBytes(migration) + 8L);
            }
            if (imageBytes > MAX_IMAGE_ESTIMATED_BYTES) {
                throw new TaskCodecException("task 存档超过总量上限");
            }
            TaskAssetManifest assets = TaskAssetManifest.empty();
            if (schema == 2) {
                NBTTagList encodedAssets = requireList(root, ASSETS, Constants.NBT.TAG_COMPOUND);
                if (encodedAssets.tagCount() > TaskAssetManifest.MAX_ASSETS) {
                    throw new TaskCodecException("活动资产数量超过上限");
                }
                Map<TaskAssetId, TaskAssetMetadata> decodedAssets = new LinkedHashMap<>();
                for (int i = 0; i < encodedAssets.tagCount(); i++) {
                    TaskAssetMetadata metadata = decodeAsset(encodedAssets.getCompoundTagAt(i));
                    if (decodedAssets.putIfAbsent(metadata.assetId(), metadata) != null) {
                        throw new TaskCodecException("重复 assetId: " + metadata.assetId());
                    }
                }
                assets = new TaskAssetManifest(decodedAssets);
                assets.requireOwnedBy(tasks.keySet());
            }
            TaskRepository.Image image = new TaskRepository.Image(tasks, tombstones, migrations, assets);
            requireImageBudget(image, MAX_IMAGE_ESTIMATED_BYTES);
            return image;
        } catch (TaskCodecException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new TaskCodecException("task 存档字段损坏", e);
        }
    }

    private static NBTTagCompound encodeAsset(TaskAssetMetadata metadata) {
        NBTTagCompound tag = new NBTTagCompound();
        setUuid(tag, "asset_id", metadata.assetId().value());
        setUuid(tag, "task_id", metadata.taskId().value());
        tag.setString("kind", metadata.kind());
        tag.setString("sha256", metadata.sha256());
        tag.setLong("compressed_bytes", metadata.compressedBytes());
        tag.setLong("logical_bytes", metadata.logicalBytes());
        return tag;
    }

    private static TaskAssetMetadata decodeAsset(NBTTagCompound tag) {
        if (!tag.func_150296_c().equals(ASSET_FIELDS)) {
            throw new TaskCodecException("asset metadata 缺少字段或包含未知字段");
        }
        requireUuid(tag, "asset_id");
        requireUuid(tag, "task_id");
        String sha256 = requireString(tag, "sha256");
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new TaskCodecException("asset sha256 必须是 canonical 小写十六进制");
        }
        return new TaskAssetMetadata(
                new TaskAssetId(getUuid(tag, "asset_id")),
                new TaskId(getUuid(tag, "task_id")),
                requireString(tag, "kind"),
                sha256,
                requireLong(tag, "compressed_bytes"),
                requireLong(tag, "logical_bytes"));
    }

    public NBTTagCompound encodeSnapshot(TaskSnapshot snapshot) {
        estimateSnapshotBytes(snapshot);
        return encodeSnapshotUnchecked(snapshot);
    }

    private NBTTagCompound encodeSnapshotUnchecked(TaskSnapshot snapshot) {
        NBTTagCompound tag = new NBTTagCompound();
        setUuid(tag, "id", snapshot.id().value());
        setUuid(tag, "submission", snapshot.submissionId().value());
        setUuid(tag, "owner", snapshot.ownerId());
        tag.setString("dimension", snapshot.dimensionId());
        tag.setString("type", snapshot.type().name());
        tag.setString("state", snapshot.state().name());
        if (snapshot.workflowEntryId() >= 0) tag.setInteger("workflow", snapshot.workflowEntryId());
        if (snapshot.waitKey() != null) {
            NBTTagCompound wait = new NBTTagCompound();
            wait.setString("kind", snapshot.waitKey().kind());
            wait.setString("value", snapshot.waitKey().value());
            tag.setTag("wait", wait);
        }
        tag.setLong("revision", snapshot.revision());
        tag.setLong("created_game_time", snapshot.createdGameTime());
        tag.setLong("updated_game_time", snapshot.updatedGameTime());
        tag.setInteger("total", snapshot.totalUnits());
        tag.setInteger("cursor", snapshot.cursorUnits());
        tag.setInteger("succeeded", snapshot.succeededUnits());
        tag.setInteger("failed", snapshot.failedUnits());
        tag.setTag("payload", snapshot.payload());
        return tag;
    }

    public TaskSnapshot decodeSnapshot(NBTTagCompound tag) {
        Set<String> expected = new LinkedHashSet<>(SNAPSHOT_REQUIRED_FIELDS);
        if (tag.hasKey("workflow")) expected.add("workflow");
        if (tag.hasKey("wait")) expected.add("wait");
        if (!tag.func_150296_c().equals(expected)) {
            throw new TaskCodecException("task snapshot 缺少字段或包含未知字段");
        }
        requireUuid(tag, "id");
        requireUuid(tag, "submission");
        requireUuid(tag, "owner");
        String dimension = requireString(tag, "dimension");
        TaskType type = parseEnum(TaskType.class, requireString(tag, "type"), "type");
        TaskLifecycleState state = parseEnum(
                TaskLifecycleState.class, requireString(tag, "state"), "state");
        TaskWaitKey waitKey = null;
        if (tag.hasKey("wait")) {
            if (!tag.hasKey("wait", Constants.NBT.TAG_COMPOUND)) {
                throw new TaskCodecException("可选字段 wait 的 NBT 类型错误");
            }
            NBTTagCompound wait = tag.getCompoundTag("wait");
            if (!wait.func_150296_c().equals(WAIT_FIELDS)) {
                throw new TaskCodecException("wait envelope 缺少字段或包含未知字段");
            }
            waitKey = new TaskWaitKey(requireString(wait, "kind"), requireString(wait, "value"));
        }
        int workflowEntryId = -1;
        if (tag.hasKey("workflow")) {
            if (!tag.hasKey("workflow", Constants.NBT.TAG_INT)) {
                throw new TaskCodecException("可选字段 workflow 的 NBT 类型错误");
            }
            workflowEntryId = tag.getInteger("workflow");
        }
        if (!tag.hasKey("payload", Constants.NBT.TAG_COMPOUND)) {
            throw new TaskCodecException("缺少 NBTTagCompound 字段: payload");
        }
        NBTTagCompound payload = com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.copyCompound(
                tag.getCompoundTag("payload"));
        return new TaskSnapshot(
                new TaskId(getUuid(tag, "id")),
                new SubmissionId(getUuid(tag, "submission")),
                getUuid(tag, "owner"),
                dimension,
                type,
                state,
                workflowEntryId,
                waitKey,
                requireLong(tag, "revision"),
                requireLong(tag, "created_game_time"),
                requireLong(tag, "updated_game_time"),
                requireInt(tag, "total"),
                requireInt(tag, "cursor"),
                requireInt(tag, "succeeded"),
                requireInt(tag, "failed"),
                payload);
    }

    public long estimateSnapshotBytes(TaskSnapshot snapshot) {
        // 结构化遍历在硬上限处立即停止，不构造 payload.toString() 巨型临时字符串。
        SizeCounter counter = new SizeCounter(MAX_TASK_PAYLOAD_BYTES, MAX_NBT_NODES);
        measureTag(snapshot.payloadView(), counter, 0);
        if (counter.exceeded()) {
            throw new TaskCodecException("单个 task payload 超过 4 MiB/100000 节点上限");
        }
        long metadataBytes = 256L + NbtStringLimits.modifiedUtfBytes(snapshot.dimensionId());
        if (snapshot.waitKey() != null) {
            metadataBytes = addSaturated(metadataBytes,
                    8L + NbtStringLimits.modifiedUtfBytes(snapshot.waitKey().kind())
                            + NbtStringLimits.modifiedUtfBytes(snapshot.waitKey().value()));
        }
        return addSaturated(metadataBytes, counter.bytes);
    }

    /** 编码与解码共享同一套根镜像预算；外置资产正文由 blob 仓库单独计费。 */
    long estimateImageBytes(TaskRepository.Image image) {
        long bytes = 128L;
        for (TaskSnapshot snapshot : image.tasks().values()) {
            bytes = addSaturated(bytes, estimateSnapshotBytes(snapshot));
        }
        for (TaskTombstone tombstone : image.tombstones().values()) {
            bytes = addSaturated(bytes,
                    256L + NbtStringLimits.modifiedUtfBytes(tombstone.dimensionId()));
        }
        for (String migration : image.completedMigrations()) {
            bytes = addSaturated(bytes, 8L + NbtStringLimits.modifiedUtfBytes(migration));
        }
        for (TaskAssetMetadata asset : image.assets().entries().values()) {
            bytes = addSaturated(bytes, 256L
                    + NbtStringLimits.modifiedUtfBytes(asset.kind())
                    + NbtStringLimits.modifiedUtfBytes(asset.sha256()));
        }
        return bytes;
    }

    void requireImageBudget(TaskRepository.Image image, long maxBytes) {
        if (maxBytes <= 0L) throw new IllegalArgumentException("根镜像预算必须为正数");
        if (estimateImageBytes(image) > maxBytes) {
            throw new TaskCodecException("task 存档超过总量上限");
        }
    }

    /** 外置资产 codec 复用相同的深度、节点与 Modified UTF 校验；默认入口沿用 TaskSnapshot 预算。 */
    public long estimatePayloadBytes(NBTTagCompound payload) {
        return estimatePayloadBytes(payload, MAX_TASK_PAYLOAD_BYTES, MAX_NBT_NODES);
    }

    /** 大型不可变资产可复用遍历器，但必须显式给出独立的有限字节/节点预算。 */
    public long estimatePayloadBytes(NBTTagCompound payload, long maxBytes, int maxNodes) {
        if (payload == null) throw new TaskCodecException("payload 不能为空");
        if (maxBytes <= 0L || maxNodes <= 0) throw new IllegalArgumentException("NBT 测量预算必须为正数");
        SizeCounter counter = new SizeCounter(maxBytes, maxNodes);
        measureTag(payload, counter, 0);
        if (counter.exceeded()) {
            throw new TaskCodecException("payload 超过指定字节/节点上限");
        }
        return counter.bytes;
    }

    private static void measureTag(NBTBase tag, SizeCounter counter, int depth) {
        if (tag == null || counter.exceeded()) return;
        if (depth > MAX_NBT_DEPTH) throw new TaskCodecException("task payload NBT 嵌套过深");
        counter.node();
        switch (tag.getId()) {
            case Constants.NBT.TAG_END: counter.add(1L); break;
            case Constants.NBT.TAG_BYTE: counter.add(1L); break;
            case Constants.NBT.TAG_SHORT: counter.add(2L); break;
            case Constants.NBT.TAG_INT:
            case Constants.NBT.TAG_FLOAT: counter.add(4L); break;
            case Constants.NBT.TAG_LONG:
            case Constants.NBT.TAG_DOUBLE: counter.add(8L); break;
            case Constants.NBT.TAG_BYTE_ARRAY:
                counter.add(((NBTTagByteArray) tag).func_150292_c().length);
                break;
            case Constants.NBT.TAG_STRING: {
                String value = ((NBTTagString) tag).func_150285_a_();
                int bytes = NbtStringLimits.requireWritable(value, "payload string");
                counter.add(2L + bytes);
                break;
            }
            case Constants.NBT.TAG_LIST: {
                NBTTagList list = (NBTTagList) tag;
                counter.add(8L);
                for (int i = 0; i < list.tagCount() && !counter.exceeded(); i++) {
                    measureTag(com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.listElement(
                            list, i), counter, depth + 1);
                }
                break;
            }
            case Constants.NBT.TAG_COMPOUND: {
                NBTTagCompound compound = (NBTTagCompound) tag;
                counter.add(8L);
                for (String key : compound.func_150296_c()) {
                    int keyBytes = NbtStringLimits.requireWritable(key, "payload key");
                    counter.add(3L + keyBytes);
                    measureTag(compound.getTag(key), counter, depth + 1);
                    if (counter.exceeded()) break;
                }
                break;
            }
            case Constants.NBT.TAG_INT_ARRAY:
                counter.add(((NBTTagIntArray) tag).func_150302_c().length * 4L);
                break;
            default:
                // 原生 1.12.2 只有 0..11；尤其没有 long-array。未知扩展必须 fail closed。
                throw new TaskCodecException("未知 NBT 类型: " + tag.getId());
        }
    }

    private static long addSaturated(long left, long right) {
        if (right > Long.MAX_VALUE - left) return Long.MAX_VALUE;
        return left + right;
    }

    private static final class SizeCounter {
        private final long maxBytes;
        private final int maxNodes;
        private long bytes;
        private int nodes;

        private SizeCounter(long maxBytes, int maxNodes) {
            this.maxBytes = maxBytes;
            this.maxNodes = maxNodes;
        }

        private void add(long amount) {
            bytes = addSaturated(bytes, Math.max(0L, amount));
        }

        private void node() {
            nodes++;
        }

        private boolean exceeded() {
            return bytes > maxBytes || nodes > maxNodes;
        }
    }

    private NBTTagCompound encodeTombstone(TaskTombstone tombstone) {
        NBTTagCompound tag = new NBTTagCompound();
        setUuid(tag, "id", tombstone.taskId().value());
        setUuid(tag, "submission", tombstone.submissionId().value());
        setUuid(tag, "owner", tombstone.ownerId());
        tag.setString("dimension", tombstone.dimensionId());
        tag.setLong("revision", tombstone.revision());
        tag.setString("state", tombstone.terminalState().name());
        tag.setLong("completed_game_time", tombstone.completedGameTime());
        tag.setLong("retained_until", tombstone.retainedUntilGameTime());
        return tag;
    }

    private TaskTombstone decodeTombstone(NBTTagCompound tag) {
        if (!tag.func_150296_c().equals(TOMBSTONE_FIELDS)) {
            throw new TaskCodecException("tombstone 缺少字段或包含未知字段");
        }
        requireUuid(tag, "id");
        requireUuid(tag, "submission");
        requireUuid(tag, "owner");
        return new TaskTombstone(
                new TaskId(getUuid(tag, "id")),
                new SubmissionId(getUuid(tag, "submission")),
                getUuid(tag, "owner"),
                requireString(tag, "dimension"),
                requireLong(tag, "revision"),
                parseEnum(TaskLifecycleState.class, requireString(tag, "state"), "state"),
                requireLong(tag, "completed_game_time"),
                requireLong(tag, "retained_until"));
    }

    private static NBTTagList requireList(NBTTagCompound root, String key, int elementType) {
        NBTBase value = root.getTag(key);
        if (!(value instanceof NBTTagList)) {
            throw new TaskCodecException("缺少 ListTag 字段: " + key);
        }
        NBTTagList list = (NBTTagList) value;
        if (!com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.isEmpty(list)
                && com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.listElementType(list) != elementType) {
            throw new TaskCodecException("ListTag 元素类型错误: " + key);
        }
        return list;
    }

    private static void requireUuid(NBTTagCompound tag, String key) {
        if (!hasUuid(tag, key)) throw new TaskCodecException("缺少 UUID 字段: " + key);
    }

    /** 保持主线 schema 的单字段 4-int UUID；不能使用 1.12 会展开 Most/Least 字段的 setUniqueId。 */
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
        if (!hasUuid(tag, key)) throw new TaskCodecException("UUID 字段损坏: " + key);
        int[] values = tag.getIntArray(key);
        long most = ((long) values[0] << 32) | (values[1] & 0xffffffffL);
        long least = ((long) values[2] << 32) | (values[3] & 0xffffffffL);
        return new UUID(most, least);
    }

    private static String requireString(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_STRING)) throw new TaskCodecException("缺少字符串字段: " + key);
        String value = tag.getString(key);
        if (value.trim().isEmpty()) throw new TaskCodecException("字符串字段为空: " + key);
        return value;
    }

    private static int requireInt(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_INT)) throw new TaskCodecException("缺少整数值字段: " + key);
        return tag.getInteger(key);
    }

    private static long requireLong(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_LONG)) throw new TaskCodecException("缺少长整数值字段: " + key);
        return tag.getLong(key);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new TaskCodecException("未知 " + field + ": " + value, e);
        }
    }

    public static final class TaskCodecException extends IllegalArgumentException {
        public TaskCodecException(String message) {
            super(message);
        }

        public TaskCodecException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
