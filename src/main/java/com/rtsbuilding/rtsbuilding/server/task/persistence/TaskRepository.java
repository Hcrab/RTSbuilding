package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * durable task 的持久化端口。
 *
 * <p>一次 {@link Commit} 必须满足全有或全无：任务快照、墓碑与迁移完成标记不能部分成功。
 * 实现可以使用单一原子 NBT、分片 Journal 或异步 writer；TaskStore 与业务执行器不依赖具体磁盘布局。</p>
 */
public interface TaskRepository {

    LoadResult load();

    CommitResult commit(Commit commit);

    /** 已持久化的完整逻辑镜像；集合在构造时做防御性复制。 */
    record Image(Map<TaskId, TaskSnapshot> tasks,
                 Map<TaskId, TaskTombstone> tombstones,
                 Set<String> completedMigrations) {
        public Image {
            tasks = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(tasks, "tasks")));
            tombstones = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(tombstones, "tombstones")));
            completedMigrations = Set.copyOf(
                    new LinkedHashSet<>(Objects.requireNonNull(completedMigrations, "completedMigrations")));
        }

        public static Image empty() {
            return new Image(Map.of(), Map.of(), Set.of());
        }
    }

    /** 一个必须原子提交的增量批次。 */
    record Commit(List<TaskSnapshot> upserts,
                  List<TaskTombstone> tombstones,
                  Set<String> completedMigrations) {
        public Commit {
            upserts = List.copyOf(new ArrayList<>(Objects.requireNonNull(upserts, "upserts")));
            tombstones = List.copyOf(new ArrayList<>(Objects.requireNonNull(tombstones, "tombstones")));
            completedMigrations = Set.copyOf(
                    new LinkedHashSet<>(Objects.requireNonNull(completedMigrations, "completedMigrations")));
            if (upserts.isEmpty() && tombstones.isEmpty() && completedMigrations.isEmpty()) {
                throw new IllegalArgumentException("不能提交空批次");
            }
        }

        public static Commit upserts(Collection<TaskSnapshot> snapshots) {
            return new Commit(List.copyOf(snapshots), List.of(), Set.of());
        }
    }

    sealed interface LoadResult permits LoadResult.Found, LoadResult.Missing, LoadResult.Failed {
        record Found(Image image) implements LoadResult {
            public Found {
                Objects.requireNonNull(image, "image");
            }
        }

        record Missing() implements LoadResult {
        }

        record Failed(Throwable cause) implements LoadResult {
            public Failed {
                Objects.requireNonNull(cause, "cause");
            }
        }
    }

    sealed interface CommitResult permits CommitResult.Acknowledged, CommitResult.Failed {
        /** 实际写入字节数未知时使用 -1；revision 的确认由上层按提交内容完成。 */
        record Acknowledged(long bytesWritten) implements CommitResult {
            public Acknowledged {
                if (bytesWritten < -1L) throw new IllegalArgumentException("bytesWritten 无效");
            }
        }

        record Failed(Throwable cause) implements CommitResult {
            public Failed {
                Objects.requireNonNull(cause, "cause");
            }
        }
    }
}
