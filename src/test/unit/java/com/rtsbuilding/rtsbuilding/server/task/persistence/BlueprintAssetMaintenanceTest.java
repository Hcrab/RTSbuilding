package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetMetadata;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint.AtomicBlueprintBlobRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlueprintAssetMaintenanceTest {

    @Test
    void durableRootRemovalQueuesExactBoundedRuntimeCleanup() {
        AtomicBlueprintBlobRepository repository = mock(AtomicBlueprintBlobRepository.class);
        when(repository.reconcileOrphans()).thenReturn(
                new AtomicBlueprintBlobRepository.ReconcileResult(0, 0, true, false));
        TaskId taskId = TaskId.create();
        TaskAssetId assetId = TaskAssetId.forTask(taskId, "blueprint");
        TaskAssetMetadata metadata = new TaskAssetMetadata(
                assetId, taskId, "blueprint", "a".repeat(64), 128L, 256L);
        BlueprintAssetMaintenance maintenance = new BlueprintAssetMaintenance();
        maintenance.start(repository, Set.of(assetId));

        maintenance.enqueueCleanup(List.of(metadata));

        assertTrue(maintenance.close(Duration.ofSeconds(1)));
        verify(repository).deleteAfterRootRemovalIfMatches(assetId, metadata.sha256());
    }

    @Test
    void nonInterruptibleMaintenancePreventsUnsafeRuntimeReset() throws InterruptedException {
        AtomicBlueprintBlobRepository repository = mock(AtomicBlueprintBlobRepository.class);
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean released = new AtomicBoolean();
        doAnswer(ignored -> {
            started.countDown();
            while (!released.get()) {
                try {
                    Thread.sleep(5L);
                } catch (InterruptedException ignoredInterrupt) {
                    // 模拟无法及时停止的文件系统调用。
                }
            }
            return new AtomicBlueprintBlobRepository.ReconcileResult(0, 0, true, false);
        }).when(repository).reconcileOrphans();

        BlueprintAssetMaintenance maintenance = new BlueprintAssetMaintenance(
                Executors::newSingleThreadExecutor);
        maintenance.start(repository, Set.of());
        assertTrue(started.await(1, TimeUnit.SECONDS));
        assertFalse(maintenance.close(Duration.ofMillis(20)),
                "旧维护线程仍存活时不能把生命周期伪装成已关闭");

        released.set(true);
        assertTrue(maintenance.close(Duration.ofSeconds(1)));
    }
}
