package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPersistenceRuntimeTest {

    @Test
    void ownerFlushUsesBackgroundWriterAndLeavesOtherOwnerDirty() {
        RecordingRepository repository = new RecordingRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        TaskSnapshot first = task(firstOwner, 1);
        TaskSnapshot second = task(secondOwner, 2);
        coordinator.submit(first);
        coordinator.submit(second);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(() -> executor);
        runtime.start(coordinator);
        runtime.flushOwner(firstOwner);

        assertTrue(coordinator.hasAcknowledged(first.id(), first.revision()));
        assertFalse(coordinator.hasAcknowledged(second.id(), second.revision()));
        assertFalse(coordinator.isDirty(first.id()));
        assertTrue(coordinator.isDirty(second.id()));
        assertNotEquals(Thread.currentThread(), repository.writeThread,
                "NBT writer 不能回到服务器主线程执行");

        runtime.stop();
        assertTrue(executor.isTerminated());
        assertFalse(runtime.started());
    }

    @Test
    void failedAckKeepsDirtyAndFlushRetriesExactRevision() {
        RecordingRepository repository = new RecordingRepository();
        repository.remainingWriteFailures = 1;
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot snapshot = task(UUID.randomUUID(), 3);
        coordinator.submit(snapshot);
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(Executors::newSingleThreadExecutor);
        runtime.start(coordinator);

        runtime.flushAll(2, Duration.ofSeconds(2));

        assertTrue(coordinator.hasAcknowledged(snapshot.id(), snapshot.revision()));
        assertFalse(coordinator.isDirty(snapshot.id()));
        assertTrue(repository.writeAttempts == 2, "失败写盘必须保脏并重试，而不是伪造 ACK");
        runtime.stop();
    }

    @Test
    void exceptionalWriterMakesRuntimeFailClosedButStopStillResetsAfterThreadTerminates() {
        RecordingRepository broken = new RecordingRepository();
        broken.throwFromWriter = true;
        TaskPersistenceCoordinator coordinator = open(broken);
        coordinator.submit(task(UUID.randomUUID(), 4));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(
                () -> executor, 1, Duration.ofMillis(200));
        runtime.start(coordinator);

        assertThrows(IllegalStateException.class, runtime::stop);
        assertTrue(executor.isTerminated(), "异常路径也必须关闭 writer");
        assertFalse(runtime.started(), "writer 已终止后 singleton 必须允许下一世界重新初始化");

        RecordingRepository healthy = new RecordingRepository();
        assertDoesNotThrow(() -> runtime.start(open(healthy)));
        assertDoesNotThrow(runtime::stop);
    }

    @Test
    void rejectedNonCanonicalAckDoesNotEnterSilentRetryLoop() {
        RecordingRepository repository = new RecordingRepository();
        repository.rejectAck = true;
        TaskPersistenceCoordinator coordinator = open(repository);
        coordinator.submit(task(UUID.randomUUID(), 5));
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(
                Executors::newSingleThreadExecutor, 2, Duration.ofMillis(200));
        runtime.start(coordinator);

        assertThrows(IllegalStateException.class,
                () -> runtime.flushAll(2, Duration.ofMillis(200)));
        assertThrows(IllegalStateException.class, runtime::tick,
                "被拒绝的 completion 必须进入可诊断 fail-closed，而不是永久空转");
        assertThrows(IllegalStateException.class, runtime::stop);
        assertFalse(runtime.started());
    }

    @Test
    void nonInterruptibleOldWriterBlocksRestartUntilProcessExit() throws InterruptedException {
        RecordingRepository repository = new RecordingRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        coordinator.submit(task(UUID.randomUUID(), 6));
        NonInterruptibleExecutor executor = new NonInterruptibleExecutor();
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(
                () -> executor, 1, Duration.ofMillis(20));
        runtime.start(coordinator);

        assertThrows(IllegalStateException.class, runtime::stop);
        assertTrue(runtime.started(), "旧 writer 未终止时不能 reset singleton");
        assertThrows(IllegalStateException.class,
                () -> runtime.start(open(new RecordingRepository())),
                "不得让新世界启动第二个 writer 与旧线程并存");

        executor.release();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }

    private static TaskPersistenceCoordinator open(TaskRepository repository) {
        return TaskPersistenceCoordinator.open(repository, new TaskCodec());
    }

    private static TaskSnapshot task(UUID owner, int workflow) {
        return TaskStoreTest.snapshot(TaskId.create(), SubmissionId.create(), owner, workflow,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld");
    }

    private static final class RecordingRepository implements TaskRepository {
        private final Map<UUID, Prepared> prepared = new java.util.LinkedHashMap<>();
        private final Map<UUID, WriteCompletion> canonical = new java.util.LinkedHashMap<>();
        private int remainingWriteFailures;
        private int writeAttempts;
        private boolean throwFromWriter;
        private boolean rejectAck;
        private Thread writeThread;

        @Override
        public LoadResult load() {
            return new LoadResult.Missing();
        }

        @Override
        public synchronized PrepareResult prepare(Commit commit) {
            Prepared value = new Prepared(UUID.randomUUID(), commit);
            prepared.put(value.ticketId(), value);
            return new PrepareResult.Prepared(value);
        }

        @Override
        public synchronized WriteCompletion writePrepared(PreparedCommit preparedCommit) {
            writeThread = Thread.currentThread();
            writeAttempts++;
            if (throwFromWriter) throw new IllegalStateException("injected exceptional writer");
            Prepared value = prepared.get(preparedCommit.ticketId());
            WriteCompletion completion;
            if (value == null || value != preparedCommit) {
                completion = WriteCompletion.failed(preparedCommit.ticketId(), new IOException("foreign"));
            } else if (remainingWriteFailures-- > 0) {
                completion = WriteCompletion.failed(value.ticketId(), new IOException("injected write failure"));
            } else {
                completion = WriteCompletion.succeeded(value.ticketId(), 128L);
            }
            canonical.put(completion.ticketId(), completion);
            return completion;
        }

        @Override
        public synchronized AcknowledgeResult acknowledge(WriteCompletion completion) {
            if (rejectAck || canonical.get(completion.ticketId()) != completion) {
                return new AcknowledgeResult(false, false, new IOException("non-canonical completion"));
            }
            canonical.remove(completion.ticketId());
            prepared.remove(completion.ticketId());
            return completion.successful()
                    ? new AcknowledgeResult(true, true, null)
                    : new AcknowledgeResult(true, false, completion.failure());
        }

        private record Prepared(UUID ticketId, Commit commit) implements PreparedCommit {
            @Override
            public int recordCount() {
                return commit.recordCount();
            }
        }
    }

    /** 模拟忽略 interrupt 的第三方/文件系统 writer，验证跨世界线程隔离红线。 */
    private static final class NonInterruptibleExecutor extends AbstractExecutorService {
        private final AtomicBoolean shutdown = new AtomicBoolean();
        private final AtomicBoolean released = new AtomicBoolean();
        private final CountDownLatch terminated = new CountDownLatch(1);

        @Override
        public void shutdown() {
            shutdown.set(true);
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown.set(true);
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown.get();
        }

        @Override
        public boolean isTerminated() {
            return terminated.getCount() == 0L;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return terminated.await(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            Thread.ofPlatform().daemon(true).name("NonInterruptibleTaskWriter").start(() -> {
                try {
                    while (!released.get()) {
                        try {
                            Thread.sleep(5L);
                        } catch (InterruptedException ignored) {
                            // 故意忽略，用来模拟无法及时停止的底层 writer。
                        }
                    }
                    command.run();
                } finally {
                    terminated.countDown();
                }
            });
        }

        void release() {
            released.set(true);
        }
    }
}
