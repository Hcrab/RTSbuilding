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
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Forge 蓝图库唯一后台读取 worker。
 *
 * <p>只使用主线程捕获的 RegistryAccess，不访问 Minecraft、ClientLevel、GUI 或 GL；
 * 结果队列有界，取消会中断旧代任务并由仓储代际检查兜底。</p>
 */
final class BlueprintLibraryLoadWorker {
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new LoaderThreadFactory());

    private BlueprintLibraryLoadWorker() {
    }

    static BlueprintLibraryRepository.LoadHandle submit(long generation, Path folder,
            RegistryAccess registryAccess, ConcurrentMap<String, Long> fileRevisions,
            BlockingQueue<BlueprintLibraryLoadResult> results) {
        Future<?> future = EXECUTOR.submit(() -> run(generation, folder, registryAccess,
                fileRevisions, results));
        return () -> {
            future.cancel(true);
            EXECUTOR.purge();
        };
    }

    private static void run(long generation, Path folder, RegistryAccess registryAccess,
            ConcurrentMap<String, Long> fileRevisions,
            BlockingQueue<BlueprintLibraryLoadResult> results) {
        try {
            Files.createDirectories(folder);
            List<Path> paths;
            try (var stream = Files.list(folder)) {
                paths = stream.filter(Files::isRegularFile)
                        .filter(BlueprintPanelFiles::isBlueprintFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                                String.CASE_INSENSITIVE_ORDER))
                        .limit(512)
                        .toList();
            }
            for (Path path : paths) {
                if (Thread.currentThread().isInterrupted()) return;
                String fileName = path.getFileName().toString();
                long revision = fileRevisions.getOrDefault(fileName, 0L);
                BlueprintLibraryLoadResult result;
                try {
                    byte[] data = Files.readAllBytes(path);
                    result = BlueprintLibraryLoadResult.entry(generation, path, fileName,
                            revision, BlueprintReaders.parse(data, fileName, registryAccess));
                } catch (Exception failure) {
                    result = BlueprintLibraryLoadResult.error(generation, path, fileName,
                            revision, failureDetail(failure));
                }
                if (!offer(results, result)) return;
            }
            offer(results, BlueprintLibraryLoadResult.complete(generation, ""));
        } catch (IOException | RuntimeException failure) {
            offer(results, BlueprintLibraryLoadResult.complete(generation, failureDetail(failure)));
        }
    }

    private static boolean offer(BlockingQueue<BlueprintLibraryLoadResult> results,
            BlueprintLibraryLoadResult result) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (results.offer(result, 100L, TimeUnit.MILLISECONDS)) return true;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static String failureDetail(Throwable failure) {
        Throwable cause = failure == null ? null
                : failure.getCause() == null ? failure : failure.getCause();
        if (cause == null) return "Unknown error";
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static final class LoaderThreadFactory implements ThreadFactory {
        private int sequence;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable,
                    "rtsbuilding-forge-blueprint-loader-" + (++sequence));
            thread.setDaemon(true);
            try {
                thread.setPriority(Thread.MIN_PRIORITY);
            } catch (SecurityException ignored) {
                // daemon 属性仍保证退出时不被后台任务阻塞。
            }
            return thread;
        }
    }
}
