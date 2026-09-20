package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.io.BlueprintReaders;
import net.minecraft.core.RegistryAccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 蓝图库唯一的后台读取 worker。
 *
 * <p>worker 只接收主线程捕获的冻结 {@link RegistryAccess}，不触碰
 * {@code Minecraft}、{@code ClientLevel}、GUI 或 OpenGL。每次任务使用独立的
 * 有界结果队列；重载时取消旧任务并替换队列，旧代结果不会堵住新代扫描。</p>
 */
final class BlueprintLibraryLoadWorker {
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new DaemonLowPriorityThreadFactory());

    private BlueprintLibraryLoadWorker() {
    }

    static BlueprintLibraryRepository.LoadHandle submit(
            long generation,
            Path folder,
            RegistryAccess registryAccess,
            ConcurrentMap<String, Long> fileRevisions,
            BlockingQueue<BlueprintLibraryLoadResult> results) {
        Future<?> future = EXECUTOR.submit(() -> run(
                generation, folder, registryAccess, fileRevisions, results));
        return () -> {
            future.cancel(true);
            EXECUTOR.purge();
        };
    }

    private static void run(
            long generation,
            Path folder,
            RegistryAccess registryAccess,
            ConcurrentMap<String, Long> fileRevisions,
            BlockingQueue<BlueprintLibraryLoadResult> results) {
        try {
            Files.createDirectories(folder);
            List<Path> paths;
            try (var stream = Files.list(folder)) {
                paths = stream
                        .filter(Files::isRegularFile)
                        .filter(BlueprintPanelFiles::isBlueprintFile)
                        .sorted(Comparator.comparing(
                                path -> path.getFileName().toString(),
                                String.CASE_INSENSITIVE_ORDER))
                        .limit(512)
                        .toList();
            }

            for (Path path : paths) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                String fileName = path.getFileName().toString();
                // 在读取前记住文件修订号；客户端保存替换同名文件后，旧结果会被丢弃。
                long fileRevision = fileRevisions.getOrDefault(fileName, 0L);
                BlueprintLibraryLoadResult result;
                try {
                    byte[] data = Files.readAllBytes(path);
                    result = BlueprintLibraryLoadResult.entry(
                            generation,
                            path,
                            fileName,
                            fileRevision,
                            BlueprintReaders.parse(data, fileName, registryAccess));
                } catch (Exception ex) {
                    result = BlueprintLibraryLoadResult.error(
                            generation,
                            path,
                            fileName,
                            fileRevision,
                            failureDetail(ex));
                }
                if (!offer(results, result)) {
                    return;
                }
            }
            offer(results, BlueprintLibraryLoadResult.complete(generation, ""));
        } catch (IOException ex) {
            offer(results, BlueprintLibraryLoadResult.complete(
                    generation, failureDetail(ex)));
        } catch (RuntimeException ex) {
            // 目录权限或文件系统 provider 也可能以运行时异常失败；交给客户端显示。
            offer(results, BlueprintLibraryLoadResult.complete(
                    generation, failureDetail(ex)));
        }
    }

    private static boolean offer(
            BlockingQueue<BlueprintLibraryLoadResult> results,
            BlueprintLibraryLoadResult result) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (results.offer(result, 100L, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static String failureDetail(Throwable throwable) {
        Throwable cause = throwable == null ? null
                : throwable.getCause() == null ? throwable : throwable.getCause();
        if (cause == null) {
            return "Unknown error";
        }
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? cause.getClass().getSimpleName()
                : message;
    }

    private static final class DaemonLowPriorityThreadFactory implements ThreadFactory {
        private int sequence;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable,
                    "rtsbuilding-blueprint-loader-" + (++sequence));
            thread.setDaemon(true);
            try {
                thread.setPriority(Thread.MIN_PRIORITY);
            } catch (SecurityException ignored) {
                // 某些安全管理器禁止调优先级，daemon 属性仍保证不会阻塞退出。
            }
            return thread;
        }
    }
}
