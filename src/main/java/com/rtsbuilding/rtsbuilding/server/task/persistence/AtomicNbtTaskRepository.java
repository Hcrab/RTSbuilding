package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.data.RtsAtomicNbtStore;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 单文件原子 NBT 的最小 TaskRepository 实现。
 *
 * <p>这是正确性基线，不承诺最终磁盘吞吐。上层只依赖增量 Commit 契约，因此若实际基准证明
 * 全 Root 替换过慢，可以在不改 TaskStore/Codec 的情况下替换为分片 Journal。</p>
 */
public final class AtomicNbtTaskRepository implements TaskRepository {
    private final RtsAtomicNbtStore store;
    private final TaskCodec codec;
    private Image image = Image.empty();
    private boolean loaded;
    private boolean exists;

    /** wiring 层负责从 MinecraftServer 构造原子 Store；Repository 本身不接收世界和玩家对象。 */
    public AtomicNbtTaskRepository(RtsAtomicNbtStore store, TaskCodec codec) {
        this.store = store;
        this.codec = codec;
    }

    @Override
    public synchronized LoadResult load() {
        if (loaded) return exists ? new LoadResult.Found(image) : new LoadResult.Missing();
        try {
            CompoundTag root = store.read();
            if (root.isEmpty()) {
                image = Image.empty();
                exists = false;
            } else {
                image = codec.decodeImage(root);
                exists = true;
            }
            loaded = true;
            return exists ? new LoadResult.Found(image) : new LoadResult.Missing();
        } catch (RuntimeException e) {
            // 不设置 loaded，保留修复文件后重试的能力；绝不把损坏文件视为空镜像。
            return new LoadResult.Failed(e);
        }
    }

    @Override
    public synchronized CommitResult commit(Commit commit) {
        if (!loaded) {
            LoadResult result = load();
            if (result instanceof LoadResult.Failed failed) {
                return new CommitResult.Failed(failed.cause());
            }
        }
        try {
            Image candidate = apply(image, commit);
            CompoundTag encoded = codec.encodeImage(candidate);
            if (!store.write(encoded)) {
                return new CommitResult.Failed(new IOException("原子写入失败: " + store.label()));
            }
            image = candidate;
            exists = true;
            return new CommitResult.Acknowledged(fileSize());
        } catch (RuntimeException e) {
            return new CommitResult.Failed(e);
        }
    }

    private static Image apply(Image current, Commit commit) {
        Map<TaskId, TaskSnapshot> tasks = new LinkedHashMap<>(current.tasks());
        Map<TaskId, TaskTombstone> tombstones = new LinkedHashMap<>(current.tombstones());
        Set<String> migrations = new LinkedHashSet<>(current.completedMigrations());

        for (TaskSnapshot snapshot : commit.upserts()) {
            TaskTombstone tombstone = tombstones.get(snapshot.id());
            if (tombstone != null) {
                throw new IllegalStateException("拒绝复用已有墓碑的 TaskId: " + snapshot.id());
            }
            TaskSnapshot previous = tasks.get(snapshot.id());
            if (previous != null && snapshot.revision() < previous.revision()) {
                throw new IllegalStateException("拒绝回退任务 revision: " + snapshot.id());
            }
            if (previous != null && snapshot.revision() == previous.revision()
                    && !snapshot.equals(previous)) {
                throw new IllegalStateException("同一 revision 出现不同快照: " + snapshot.id());
            }
            tasks.put(snapshot.id(), snapshot);
        }

        for (TaskTombstone tombstone : commit.tombstones()) {
            TaskTombstone previous = tombstones.get(tombstone.taskId());
            if (previous != null && previous.revision() > tombstone.revision()) continue;
            TaskSnapshot task = tasks.get(tombstone.taskId());
            if (task != null && task.revision() >= tombstone.revision()) {
                throw new IllegalStateException("墓碑 revision 必须高于任务快照: " + tombstone.taskId());
            }
            tombstones.put(tombstone.taskId(), tombstone);
            tasks.remove(tombstone.taskId());
        }
        migrations.addAll(commit.completedMigrations());
        return new Image(tasks, tombstones, migrations);
    }

    private long fileSize() {
        try {
            return Files.size(store.path());
        } catch (IOException ignored) {
            return -1L;
        }
    }
}
