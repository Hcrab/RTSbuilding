package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
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
    public static final int CURRENT_SCHEMA = 1;
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

    public CompoundTag encodeImage(TaskRepository.Image image) {
        if (image.tasks().size() > MAX_TASKS || image.tombstones().size() > MAX_TASKS) {
            throw new TaskCodecException("task 存档超过数量上限");
        }
        if (image.completedMigrations().size() > MAX_MIGRATIONS) {
            throw new TaskCodecException("迁移台账超过数量上限");
        }
        CompoundTag root = new CompoundTag();
        root.putInt("schema", CURRENT_SCHEMA);
        long imageBytes = 0L;
        for (TaskSnapshot snapshot : image.tasks().values()) {
            imageBytes = addSaturated(imageBytes, estimateSnapshotBytes(snapshot));
            if (imageBytes > MAX_IMAGE_ESTIMATED_BYTES) {
                throw new TaskCodecException("task 存档超过总量上限");
            }
        }

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
        image.completedMigrations().stream().sorted().map(StringTag::valueOf).forEach(migrations::add);
        root.put(MIGRATIONS, migrations);
        return root;
    }

    public TaskRepository.Image decodeImage(CompoundTag root) {
        try {
            int schema = requireInt(root, "schema");
            if (schema != CURRENT_SCHEMA) {
                throw new TaskCodecException("不支持的 task schema: " + schema);
            }

            ListTag encodedTasks = root.getList(TASKS, Tag.TAG_COMPOUND);
            ListTag encodedTombstones = root.getList(TOMBSTONES, Tag.TAG_COMPOUND);
            if (encodedTasks.size() > MAX_TASKS || encodedTombstones.size() > MAX_TASKS) {
                throw new TaskCodecException("task 存档超过数量上限");
            }

            Map<TaskId, TaskSnapshot> tasks = new LinkedHashMap<>();
            long imageBytes = 0L;
            for (int i = 0; i < encodedTasks.size(); i++) {
                TaskSnapshot snapshot = decodeSnapshot(encodedTasks.getCompound(i));
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
                TaskTombstone tombstone = decodeTombstone(encodedTombstones.getCompound(i));
                if (tombstones.putIfAbsent(tombstone.taskId(), tombstone) != null) {
                    throw new TaskCodecException("重复墓碑: " + tombstone.taskId());
                }
                TaskSnapshot task = tasks.get(tombstone.taskId());
                if (task != null) {
                    throw new TaskCodecException("墓碑与仍存活任务冲突: " + tombstone.taskId());
                }
            }

            Set<String> migrations = new LinkedHashSet<>();
            ListTag encodedMigrations = root.getList(MIGRATIONS, Tag.TAG_STRING);
            if (encodedMigrations.size() > MAX_MIGRATIONS) {
                throw new TaskCodecException("迁移台账超过数量上限");
            }
            for (int i = 0; i < encodedMigrations.size(); i++) {
                String migration = encodedMigrations.getString(i);
                if (migration.isBlank()) throw new TaskCodecException("迁移标识不能为空");
                if (migration.length() > 128) throw new TaskCodecException("迁移标识过长");
                migrations.add(migration);
            }
            return new TaskRepository.Image(tasks, tombstones, migrations);
        } catch (TaskCodecException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new TaskCodecException("task 存档字段损坏", e);
        }
    }

    public CompoundTag encodeSnapshot(TaskSnapshot snapshot) {
        estimateSnapshotBytes(snapshot);
        return encodeSnapshotUnchecked(snapshot);
    }

    private CompoundTag encodeSnapshotUnchecked(TaskSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", snapshot.id().value());
        tag.putUUID("submission", snapshot.submissionId().value());
        tag.putUUID("owner", snapshot.ownerId());
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
        requireUuid(tag, "id");
        requireUuid(tag, "submission");
        requireUuid(tag, "owner");
        String dimension = requireString(tag, "dimension");
        TaskType type = parseEnum(TaskType.class, requireString(tag, "type"), "type");
        TaskLifecycleState state = parseEnum(
                TaskLifecycleState.class, requireString(tag, "state"), "state");
        TaskWaitKey waitKey = null;
        if (tag.contains("wait", Tag.TAG_COMPOUND)) {
            CompoundTag wait = tag.getCompound("wait");
            waitKey = new TaskWaitKey(requireString(wait, "kind"), requireString(wait, "value"));
        }
        CompoundTag payload = tag.contains("payload", Tag.TAG_COMPOUND)
                ? tag.getCompound("payload").copy() : new CompoundTag();
        return new TaskSnapshot(
                new TaskId(tag.getUUID("id")),
                new SubmissionId(tag.getUUID("submission")),
                tag.getUUID("owner"),
                dimension,
                type,
                state,
                tag.contains("workflow", Tag.TAG_INT) ? tag.getInt("workflow") : -1,
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
        return addSaturated(256L + snapshot.dimensionId().length() * 2L, counter.bytes);
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
            case Tag.TAG_STRING -> counter.add(((StringTag) tag).getAsString().length() * 2L);
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
                for (String key : compound.getAllKeys()) {
                    counter.add(4L + key.length() * 2L);
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
        tag.putUUID("id", tombstone.taskId().value());
        tag.putLong("revision", tombstone.revision());
        tag.putString("state", tombstone.terminalState().name());
        tag.putLong("completed_game_time", tombstone.completedGameTime());
        return tag;
    }

    private TaskTombstone decodeTombstone(CompoundTag tag) {
        requireUuid(tag, "id");
        return new TaskTombstone(
                new TaskId(tag.getUUID("id")),
                requireLong(tag, "revision"),
                parseEnum(TaskLifecycleState.class, requireString(tag, "state"), "state"),
                requireLong(tag, "completed_game_time"));
    }

    private static void requireUuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) throw new TaskCodecException("缺少 UUID 字段: " + key);
    }

    private static String requireString(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) throw new TaskCodecException("缺少字符串字段: " + key);
        String value = tag.getString(key);
        if (value.isBlank()) throw new TaskCodecException("字符串字段为空: " + key);
        return value;
    }

    private static int requireInt(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT)) throw new TaskCodecException("缺少整数值字段: " + key);
        return tag.getInt(key);
    }

    private static long requireLong(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LONG)) throw new TaskCodecException("缺少长整数值字段: " + key);
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
