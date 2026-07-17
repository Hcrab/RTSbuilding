package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetManifest;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetMetadata;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
    private static final Set<String> ROOT_V1_FIELDS = Set.of(
            "schema", TASKS, TOMBSTONES, MIGRATIONS);
    private static final Set<String> ROOT_V2_FIELDS = Set.of(
            "schema", TASKS, TOMBSTONES, MIGRATIONS, ASSETS);
    private static final Set<String> ASSET_FIELDS = Set.of(
            "asset_id", "task_id", "kind", "sha256", "compressed_bytes", "logical_bytes");
    private static final Set<String> SNAPSHOT_REQUIRED_FIELDS = Set.of(
            "id", "submission", "owner", "dimension", "type", "state", "revision",
            "created_game_time", "updated_game_time", "total", "cursor", "succeeded", "failed", "payload");
    private static final Set<String> WAIT_FIELDS = Set.of("kind", "value");
    private static final Set<String> TOMBSTONE_FIELDS = Set.of(
            "id", "submission", "owner", "dimension", "revision", "state",
            "completed_game_time", "retained_until");

    public CompoundTag encodeImage(TaskRepository.Image image) {
        if ((long) image.tasks().size() + image.tombstones().size() > MAX_TASKS) {
            throw new TaskCodecException("task 存档超过数量上限");
        }
        if (image.completedMigrations().size() > MAX_MIGRATIONS) {
            throw new TaskCodecException("迁移台账超过数量上限");
        }
        requireImageBudget(image, MAX_IMAGE_ESTIMATED_BYTES);
        CompoundTag root = new CompoundTag();
        root.putInt("schema", CURRENT_SCHEMA);
        ListTag tasks = new ListTag();
        image.tasks().values().stream()
                .sorted(Comparator.comparing(TaskSnapshot::id))
                .map(this::encodeSnapshotUnchecked)
                .forEach(tasks::add);
        root.put(TASKS, tasks);

        ListTag tombstones = new ListTag();
        image.tombstones().values().stream()
                .sorted(Comparator.comparing(TaskTombstone::taskId))
                .map(this::encodeTombstone)
                .forEach(tombstones::add);
        root.put(TOMBSTONES, tombstones);

        ListTag migrations = new ListTag();
        image.completedMigrations().stream().sorted().forEach(migration -> {
            if (migration.isBlank() || migration.length() > 128) {
                throw new TaskCodecException("迁移标识无效");
            }
            NbtStringLimits.requireWritable(migration, "migrationId");
            migrations.add(StringTag.valueOf(migration));
        });
        root.put(MIGRATIONS, migrations);

        ListTag assets = new ListTag();
        image.assets().entries().values().stream()
                .sorted(Comparator.comparing(TaskAssetMetadata::assetId))
                .map(TaskCodec::encodeAsset)
                .forEach(assets::add);
        root.put(ASSETS, assets);
        return root;
    }

    public TaskRepository.Image decodeImage(CompoundTag root) {
        try {
            int schema = requireInt(root, "schema");
            Set<String> expectedRootFields = switch (schema) {
                case 1 -> ROOT_V1_FIELDS;
                case 2 -> ROOT_V2_FIELDS;
                default -> throw new TaskCodecException("不支持的 task schema: " + schema);
            };
            if (!root.keySet().equals(expectedRootFields)) {
                throw new TaskCodecException("task root 缺少字段或包含当前 schema 未知字段");
            }

            ListTag encodedTasks = requireList(root, TASKS, Tag.TAG_COMPOUND);
            ListTag encodedTombstones = requireList(root, TOMBSTONES, Tag.TAG_COMPOUND);
            if ((long) encodedTasks.size() + encodedTombstones.size() > MAX_TASKS) {
                throw new TaskCodecException("task 存档超过数量上限");
            }

            Map<TaskId, TaskSnapshot> tasks = new LinkedHashMap<>();
            long imageBytes = 0L;
            for (int i = 0; i < encodedTasks.size(); i++) {
                TaskSnapshot snapshot = decodeSnapshot(encodedTasks.getCompoundOrEmpty(i));
                imageBytes = addSaturated(imageBytes, estimateSnapshotBytes(snapshot));
                if (imageBytes > MAX_IMAGE_ESTIMATED_BYTES) {
                    throw new TaskCodecException("task 存档超过总量上限");
                }
                if (tasks.putIfAbsent(snapshot.id(), snapshot) != null) {
                    throw new TaskCodecException("重复 TaskId: " + snapshot.id());
                }
            }

            Map<TaskId, TaskTombstone> tombstones = new LinkedHashMap<>();
            for (int i = 0; i < encodedTombstones.size(); i++) {
                TaskTombstone tombstone = decodeTombstone(encodedTombstones.getCompoundOrEmpty(i));
                if (tombstones.putIfAbsent(tombstone.taskId(), tombstone) != null) {
                    throw new TaskCodecException("重复墓碑: " + tombstone.taskId());
                }
                TaskSnapshot task = tasks.get(tombstone.taskId());
                if (task != null) {
                    throw new TaskCodecException("墓碑与仍存活任务冲突: " + tombstone.taskId());
                }
            }
            imageBytes = addSaturated(imageBytes, encodedTombstones.size() * 256L);

            Set<String> migrations = new LinkedHashSet<>();
            ListTag encodedMigrations = requireList(root, MIGRATIONS, Tag.TAG_STRING);
            if (encodedMigrations.size() > MAX_MIGRATIONS) {
                throw new TaskCodecException("迁移台账超过数量上限");
            }
            for (int i = 0; i < encodedMigrations.size(); i++) {
                String migration = encodedMigrations.getStringOr(i, "");
                if (migration.isBlank()) throw new TaskCodecException("迁移标识不能为空");
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
                ListTag encodedAssets = requireList(root, ASSETS, Tag.TAG_COMPOUND);
                if (encodedAssets.size() > TaskAssetManifest.MAX_ASSETS) {
                    throw new TaskCodecException("活动资产数量超过上限");
                }
                Map<TaskAssetId, TaskAssetMetadata> decodedAssets = new LinkedHashMap<>();
                for (int i = 0; i < encodedAssets.size(); i++) {
                    TaskAssetMetadata metadata = decodeAsset(encodedAssets.getCompoundOrEmpty(i));
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

    private static CompoundTag encodeAsset(TaskAssetMetadata metadata) {
        CompoundTag tag = new CompoundTag();
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(tag, "asset_id", metadata.assetId().value());
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(tag, "task_id", metadata.taskId().value());
        tag.putString("kind", metadata.kind());
        tag.putString("sha256", metadata.sha256());
        tag.putLong("compressed_bytes", metadata.compressedBytes());
        tag.putLong("logical_bytes", metadata.logicalBytes());
        return tag;
    }

    private static TaskAssetMetadata decodeAsset(CompoundTag tag) {
        if (!tag.keySet().equals(ASSET_FIELDS)) {
            throw new TaskCodecException("asset metadata 缺少字段或包含未知字段");
        }
        requireUuid(tag, "asset_id");
        requireUuid(tag, "task_id");
        String sha256 = requireString(tag, "sha256");
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new TaskCodecException("asset sha256 必须是 canonical 小写十六进制");
        }
        return new TaskAssetMetadata(
                new TaskAssetId(com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(tag, "asset_id")),
                new TaskId(com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(tag, "task_id")),
                requireString(tag, "kind"),
                sha256,
                requireLong(tag, "compressed_bytes"),
                requireLong(tag, "logical_bytes"));
    }

    public CompoundTag encodeSnapshot(TaskSnapshot snapshot) {
        estimateSnapshotBytes(snapshot);
        return encodeSnapshotUnchecked(snapshot);
    }

    private CompoundTag encodeSnapshotUnchecked(TaskSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(tag, "id", snapshot.id().value());
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(tag, "submission", snapshot.submissionId().value());
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(tag, "owner", snapshot.ownerId());
        tag.putString("dimension", snapshot.dimensionId());
        tag.putString("type", snapshot.type().name());
        tag.putString("state", snapshot.state().name());
        if (snapshot.workflowEntryId() >= 0) tag.putInt("workflow", snapshot.workflowEntryId());
        if (snapshot.waitKey() != null) {
            CompoundTag wait = new CompoundTag();
            wait.putString("kind", snapshot.waitKey().kind());
            wait.putString("value", snapshot.waitKey().value());
            tag.put("wait", wait);
        }
        tag.putLong("revision", snapshot.revision());
        tag.putLong("created_game_time", snapshot.createdGameTime());
        tag.putLong("updated_game_time", snapshot.updatedGameTime());
        tag.putInt("total", snapshot.totalUnits());
        tag.putInt("cursor", snapshot.cursorUnits());
        tag.putInt("succeeded", snapshot.succeededUnits());
        tag.putInt("failed", snapshot.failedUnits());
        tag.put("payload", snapshot.payload());
        return tag;
    }

    public TaskSnapshot decodeSnapshot(CompoundTag tag) {
        Set<String> expected = new LinkedHashSet<>(SNAPSHOT_REQUIRED_FIELDS);
        if (tag.contains("workflow")) expected.add("workflow");
        if (tag.contains("wait")) expected.add("wait");
        if (!tag.keySet().equals(expected)) {
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
        if (tag.contains("wait")) {
            if (!tag.contains("wait")) {
                throw new TaskCodecException("可选字段 wait 的 NBT 类型错误");
            }
            CompoundTag wait = tag.getCompoundOrEmpty("wait");
            if (!wait.keySet().equals(WAIT_FIELDS)) {
                throw new TaskCodecException("wait envelope 缺少字段或包含未知字段");
            }
            waitKey = new TaskWaitKey(requireString(wait, "kind"), requireString(wait, "value"));
        }
        int workflowEntryId = -1;
        if (tag.contains("workflow")) {
            if (!tag.contains("workflow")) {
                throw new TaskCodecException("可选字段 workflow 的 NBT 类型错误");
            }
            workflowEntryId = tag.getIntOr("workflow", 0);
        }
        if (!tag.contains("payload")) {
            throw new TaskCodecException("缺少 CompoundTag 字段: payload");
        }
        CompoundTag payload = tag.getCompoundOrEmpty("payload").copy();
        return new TaskSnapshot(
                new TaskId(com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(tag, "id")),
                new SubmissionId(com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(tag, "submission")),
                com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(tag, "owner"),
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
    public long estimatePayloadBytes(CompoundTag payload) {
        return estimatePayloadBytes(payload, MAX_TASK_PAYLOAD_BYTES, MAX_NBT_NODES);
    }

    /** 大型不可变资产可复用遍历器，但必须显式给出独立的有限字节/节点预算。 */
    public long estimatePayloadBytes(CompoundTag payload, long maxBytes, int maxNodes) {
        if (payload == null) throw new TaskCodecException("payload 不能为空");
        if (maxBytes <= 0L || maxNodes <= 0) throw new IllegalArgumentException("NBT 测量预算必须为正数");
        SizeCounter counter = new SizeCounter(maxBytes, maxNodes);
        measureTag(payload, counter, 0);
        if (counter.exceeded()) {
            throw new TaskCodecException("payload 超过指定字节/节点上限");
        }
        return counter.bytes;
    }

    private static void measureTag(Tag tag, SizeCounter counter, int depth) {
        if (tag == null || counter.exceeded()) return;
        if (depth > MAX_NBT_DEPTH) throw new TaskCodecException("task payload NBT 嵌套过深");
        counter.node();
        switch (tag.getId()) {
            case Tag.TAG_END -> counter.add(1L);
            case Tag.TAG_BYTE -> counter.add(1L);
            case Tag.TAG_SHORT -> counter.add(2L);
            case Tag.TAG_INT, Tag.TAG_FLOAT -> counter.add(4L);
            case Tag.TAG_LONG, Tag.TAG_DOUBLE -> counter.add(8L);
            case Tag.TAG_BYTE_ARRAY -> counter.add(((ByteArrayTag) tag).getAsByteArray().length);
            case Tag.TAG_STRING -> {
                String value = ((StringTag) tag).getAsString();
                int bytes = NbtStringLimits.requireWritable(value, "payload string");
                counter.add(2L + bytes);
            }
            case Tag.TAG_LIST -> {
                ListTag list = (ListTag) tag;
                counter.add(8L);
                for (int i = 0; i < list.size() && !counter.exceeded(); i++) {
                    measureTag(list.get(i), counter, depth + 1);
                }
            }
            case Tag.TAG_COMPOUND -> {
                CompoundTag compound = (CompoundTag) tag;
                counter.add(8L);
                for (String key : compound.keySet()) {
                    int keyBytes = NbtStringLimits.requireWritable(key, "payload key");
                    counter.add(3L + keyBytes);
                    measureTag(compound.get(key), counter, depth + 1);
                    if (counter.exceeded()) break;
                }
            }
            case Tag.TAG_INT_ARRAY -> counter.add(((IntArrayTag) tag).getAsIntArray().length * 4L);
            case Tag.TAG_LONG_ARRAY -> counter.add(((LongArrayTag) tag).getAsLongArray().length * 8L);
            default -> throw new TaskCodecException("未知 NBT 类型: " + tag.getId());
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

    private CompoundTag encodeTombstone(TaskTombstone tombstone) {
        CompoundTag tag = new CompoundTag();
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(tag, "id", tombstone.taskId().value());
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(tag, "submission", tombstone.submissionId().value());
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(tag, "owner", tombstone.ownerId());
        tag.putString("dimension", tombstone.dimensionId());
        tag.putLong("revision", tombstone.revision());
        tag.putString("state", tombstone.terminalState().name());
        tag.putLong("completed_game_time", tombstone.completedGameTime());
        tag.putLong("retained_until", tombstone.retainedUntilGameTime());
        return tag;
    }

    private TaskTombstone decodeTombstone(CompoundTag tag) {
        if (!tag.keySet().equals(TOMBSTONE_FIELDS)) {
            throw new TaskCodecException("tombstone 缺少字段或包含未知字段");
        }
        requireUuid(tag, "id");
        requireUuid(tag, "submission");
        requireUuid(tag, "owner");
        return new TaskTombstone(
                new TaskId(com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(tag, "id")),
                new SubmissionId(com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(tag, "submission")),
                com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(tag, "owner"),
                requireString(tag, "dimension"),
                requireLong(tag, "revision"),
                parseEnum(TaskLifecycleState.class, requireString(tag, "state"), "state"),
                requireLong(tag, "completed_game_time"),
                requireLong(tag, "retained_until"));
    }

    private static ListTag requireList(CompoundTag root, String key, int elementType) {
        Tag value = root.get(key);
        if (!(value instanceof ListTag list)) {
            throw new TaskCodecException("缺少 ListTag 字段: " + key);
        }
        if (!list.isEmpty() && list.getElementType() != elementType) {
            throw new TaskCodecException("ListTag 元素类型错误: " + key);
        }
        return list;
    }

    private static void requireUuid(CompoundTag tag, String key) {
        if (!com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.hasUuid(tag, key)) throw new TaskCodecException("缺少 UUID 字段: " + key);
    }

    private static String requireString(CompoundTag tag, String key) {
        if (!tag.contains(key)) throw new TaskCodecException("缺少字符串字段: " + key);
        String value = tag.getStringOr(key, "");
        if (value.isBlank()) throw new TaskCodecException("字符串字段为空: " + key);
        return value;
    }

    private static int requireInt(CompoundTag tag, String key) {
        if (!tag.contains(key)) throw new TaskCodecException("缺少整数值字段: " + key);
        return tag.getIntOr(key, 0);
    }

    private static long requireLong(CompoundTag tag, String key) {
        if (!tag.contains(key)) throw new TaskCodecException("缺少长整数值字段: " + key);
        return tag.getLongOr(key, 0L);
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
