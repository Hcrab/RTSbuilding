package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetMetadata;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint.AtomicBlueprintBlobRepository;

import java.time.Duration;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 蓝图 blob 的启动维护线程。
 *
 * <p>本类只处理已经不被启动 root 引用的孤儿文件；它不拥有任务状态，也不参与 root writer。
 * 独立低优先级线程避免大量目录 I/O 阻塞正常 checkpoint。维护失败时仓库保持只读，绝不为了
 * 恢复写入而猜测删除 live 或未知文件。</p>
 */
final class BlueprintAssetMaintenance {
    private final Supplier<ExecutorService> executorFactory;
    private ExecutorService executor;
    private CompletableFuture<AtomicBlueprintBlobRepository.ReconcileResult> future;
    private AtomicBlueprintBlobRepository repository;

    BlueprintAssetMaintenance() {
        this(() -> new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(64), runnable -> {
                    Thread thread = Thread.ofPlatform()
                            .daemon(true)
                            .name("RTSBuilding-AssetMaintenance")
                            .unstarted(runnable);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy()));
    }

    BlueprintAssetMaintenance(Supplier<ExecutorService> executorFactory) {
        this.executorFactory = Objects.requireNonNull(executorFactory, "executorFactory");
    }

    /** root 与全部 live blob 已校验成功后才能调用。 */
    void start(AtomicBlueprintBlobRepository repository, Set<TaskAssetId> startupLiveAssets) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(startupLiveAssets, "startupLiveAssets");
        if (executor != null) throw new IllegalStateException("asset maintenance 已经启动");
        repository.beginReconciliation(startupLiveAssets);
        executor = Objects.requireNonNull(executorFactory.get(), "executorFactory 返回 null");
        this.repository = repository;
        future = CompletableFuture.supplyAsync(repository::reconcileOrphans, executor);
    }

    /** root 已 durable 移除 metadata 后，才允许把精确 assetId+hash 投递到有界物理回收队列。 */
    void enqueueCleanup(List<TaskAssetMetadata> removedAssets) {
        Objects.requireNonNull(removedAssets, "removedAssets");
        if (removedAssets.isEmpty() || executor == null || repository == null) return;
        List<TaskAssetMetadata> batch = List.copyOf(removedAssets);
        try {
            executor.execute(() -> {
                for (TaskAssetMetadata metadata : batch) {
                    try {
                        repository.deleteAfterRootRemovalIfMatches(
                                metadata.assetId(), metadata.sha256());
                    } catch (RuntimeException failure) {
                        // Root 已不再引用该文件；删除失败只留下安全 orphan，下一次启动继续回收。
                        RtsbuildingMod.LOGGER.warn("运行期回收 task asset 失败，将留待下次启动: {}",
                                metadata.assetId(), failure);
                    }
                }
            });
        } catch (RejectedExecutionException saturated) {
            RtsbuildingMod.LOGGER.warn(
                    "task asset 回收队列已满，{} 个 orphan 将留待下次启动", batch.size());
        }
    }

    /** 主线程低成本收割诊断结果；不等待维护 I/O。 */
    void poll() {
        if (future == null || !future.isDone()) return;
        try {
            AtomicBlueprintBlobRepository.ReconcileResult result = future.join();
            if (result.complete() && !result.failed()) {
                RtsbuildingMod.LOGGER.info("task asset 启动维护完成：扫描 {}，回收 orphan {}",
                        result.visitedEntries(), result.deletedOrphans());
            } else {
                RtsbuildingMod.LOGGER.warn(
                        "task asset 启动维护未完成，蓝图资产接纳保持只读：扫描 {}，回收 {}，failed={} ",
                        result.visitedEntries(), result.deletedOrphans(), result.failed());
            }
        } catch (RuntimeException failure) {
            RtsbuildingMod.LOGGER.error("task asset 启动维护异常，蓝图资产接纳保持只读", failure);
        } finally {
            future = null;
        }
    }

    boolean close(Duration timeout) {
        if (executor == null) return true;
        executor.shutdown();
        boolean terminated = false;
        try {
            terminated = executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!terminated) {
                executor.shutdownNow();
                terminated = executor.awaitTermination(
                        Math.min(1_000L, timeout.toMillis()), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        if (terminated) {
            executor = null;
            future = null;
            repository = null;
        }
        return terminated;
    }

    boolean running() {
        return future != null && !future.isDone();
    }
}
