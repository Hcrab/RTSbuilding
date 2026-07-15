package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.data.RtsAtomicNbtStore;
import com.rtsbuilding.rtsbuilding.server.data.RtsNbtStore;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetManifest;
import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private final Map<UUID, AtomicPreparedCommit> prepared = new LinkedHashMap<>();
    private final Map<UUID, Image> writtenCandidates = new LinkedHashMap<>();
    private final Map<UUID, PreparedState> preparedStates = new LinkedHashMap<>();
    private final Map<UUID, WriteCompletion> writeCompletions = new LinkedHashMap<>();

    /** wiring 层负责从 MinecraftServer 构造原子 Store；Repository 本身不接收世界和玩家对象。 */
    public AtomicNbtTaskRepository(RtsAtomicNbtStore store, TaskCodec codec) {
        this.store = store;
        this.codec = codec;
    }

    @Override
    public synchronized LoadResult load() {
        if (loaded) return exists ? new LoadResult.Found(image) : new LoadResult.Missing();
        try {
            switch (store.readResult()) {
                case RtsNbtStore.ReadResult.Missing ignored -> {
                    image = Image.empty();
                    exists = false;
                }
                case RtsNbtStore.ReadResult.Found found -> {
                    // Found-empty 仍是存在但损坏的文件，必须让 Codec 因缺少 schema/list 而拒绝。
                    image = codec.decodeImage(found.root());
                    exists = true;
                }
                case RtsNbtStore.ReadResult.Failed failed -> {
                    return new LoadResult.Failed(failed.cause());
                }
            }
            loaded = true;
            return exists ? new LoadResult.Found(image) : new LoadResult.Missing();
        } catch (RuntimeException e) {
            return new LoadResult.Failed(e);
        }
    }

    @Override
    public synchronized PrepareResult prepare(Commit commit) {
        if (!loaded) {
            LoadResult result = load();
            if (result instanceof LoadResult.Failed failed) return new PrepareResult.Failed(failed.cause());
        }
        if (!prepared.isEmpty()) {
            return new PrepareResult.Failed(new IllegalStateException("Atomic repository 只允许一个 in-flight commit"));
        }
        try {
            UUID ticket = UUID.randomUUID();
            AtomicPreparedCommit value = new AtomicPreparedCommit(ticket, commit);
            prepared.put(ticket, value);
            preparedStates.put(ticket, PreparedState.PREPARED);
            return new PrepareResult.Prepared(value);
        } catch (RuntimeException e) {
            return new PrepareResult.Failed(e);
        }
    }

    /** 后台 correctness adapter：全 Root 合并、编码、压缩与原子替换全部发生在这里。 */
    @Override
    public synchronized WriteCompletion writePrepared(PreparedCommit preparedCommit) {
        if (!(preparedCommit instanceof AtomicPreparedCommit atomic)
                || prepared.get(atomic.ticketId()) != atomic) {
            return WriteCompletion.failed(preparedCommit.ticketId(),
                    new IllegalArgumentException("PreparedCommit 不属于当前 Repository"));
        }
        PreparedState state = preparedStates.get(atomic.ticketId());
        if (state == PreparedState.WRITTEN) return writeCompletions.get(atomic.ticketId());
        if (state != PreparedState.PREPARED) {
            return WriteCompletion.failed(atomic.ticketId(),
                    new IllegalStateException("PreparedCommit 已被 writer 领取"));
        }
        preparedStates.put(atomic.ticketId(), PreparedState.WRITING);
        WriteCompletion completion;
        try {
            Image candidate = apply(image, atomic.logicalCommit());
            CompoundTag encoded = codec.encodeImage(candidate);
            if (!store.write(encoded)) {
                completion = WriteCompletion.failed(atomic.ticketId(),
                        new IOException("原子写入失败: " + store.label()));
            } else {
                writtenCandidates.put(atomic.ticketId(), candidate);
                completion = WriteCompletion.succeeded(atomic.ticketId(), fileSize());
            }
        } catch (RuntimeException e) {
            completion = WriteCompletion.failed(atomic.ticketId(), e);
        }
        writeCompletions.put(atomic.ticketId(), completion);
        preparedStates.put(atomic.ticketId(), PreparedState.WRITTEN);
        return completion;
    }

    @Override
    public synchronized AcknowledgeResult acknowledge(WriteCompletion completion) {
        AtomicPreparedCommit pending = prepared.get(completion.ticketId());
        if (pending == null) return new AcknowledgeResult(false, false, completion.failure());
        WriteCompletion canonical = writeCompletions.get(completion.ticketId());
        if (canonical != completion) {
            return new AcknowledgeResult(false, false,
                    new IllegalArgumentException("completion 不是 writer 的规范结果"));
        }
        prepared.remove(completion.ticketId());
        writeCompletions.remove(completion.ticketId());
        preparedStates.remove(completion.ticketId());
        Image candidate = writtenCandidates.remove(completion.ticketId());
        if (!completion.successful()) {
            return new AcknowledgeResult(true, false, completion.failure());
        }
        if (candidate == null) {
            return new AcknowledgeResult(true, false,
                    new IllegalStateException("成功 completion 缺少候选镜像"));
        }
        image = candidate;
        exists = true;
        return new AcknowledgeResult(true, true, null);
    }

    private static Image apply(Image current, Commit commit) {
        Map<TaskId, TaskSnapshot> tasks = new LinkedHashMap<>(current.tasks());
        Map<TaskId, TaskTombstone> tombstones = new LinkedHashMap<>(current.tombstones());
        Set<String> migrations = new LinkedHashSet<>(current.completedMigrations());

        for (TaskId purged : commit.purgedTombstones()) {
            tombstones.remove(purged);
        }
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
            if (previous != null && previous.revision() == tombstone.revision()
                    && !previous.equals(tombstone)) {
                throw new IllegalStateException("同一 revision 出现不同终态回执: " + tombstone.taskId());
            }
            TaskSnapshot task = tasks.get(tombstone.taskId());
            if (task != null && task.revision() >= tombstone.revision()) {
                throw new IllegalStateException("墓碑 revision 必须高于任务快照: " + tombstone.taskId());
            }
            tombstones.put(tombstone.taskId(), tombstone);
            tasks.remove(tombstone.taskId());
        }
        migrations.addAll(commit.completedMigrations());
        TaskAssetManifest assets = current.assets().apply(commit.assetUpserts(), commit.removedAssets());
        assets.requireOwnedBy(tasks.keySet());
        return new Image(tasks, tombstones, migrations, assets);
    }

    private record AtomicPreparedCommit(UUID ticketId, Commit logicalCommit) implements PreparedCommit {
        @Override
        public int recordCount() {
            return logicalCommit.recordCount();
        }
    }

    private enum PreparedState {
        PREPARED,
        WRITING,
        WRITTEN
    }

    private long fileSize() {
        try {
            return Files.size(store.path());
        } catch (IOException ignored) {
            return -1L;
        }
    }
}
