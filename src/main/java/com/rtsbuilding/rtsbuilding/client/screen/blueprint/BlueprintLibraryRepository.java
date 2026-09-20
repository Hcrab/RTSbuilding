package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * 管理本地蓝图库的异步扫描、解析、排序与文件名索引。
 *
 * <p>worker 只返回不可变蓝图和错误；ItemStack、行模型与状态文字均在客户端 pump
 * 有界发布，避免输入/render 回调同步读整库。文件名修订与 generation 同时隔离同名
 * 重保存、删除、换世界和后台旧结果晚到。</p>
 */
final class BlueprintLibraryRepository {
    private static final int RESULT_QUEUE_CAPACITY = 2;
    private static final int MAX_RESULTS_PER_PUMP = 1;
    private static final Comparator<BlueprintEntry> ENTRY_ORDER =
            Comparator.comparing(BlueprintEntry::fileName, String.CASE_INSENSITIVE_ORDER);

    private final List<BlueprintEntry> entries = new ArrayList<>();
    private final LoadSubmitter loadSubmitter;
    private final Supplier<RegistryAccess> registryAccessSupplier;
    private final Supplier<Path> blueprintFolderSupplier;
    private final ConcurrentMap<String, Long> fileRevisions = new ConcurrentHashMap<>();
    private BlockingQueue<BlueprintLibraryLoadResult> results = newResultQueue();
    private LoadHandle activeLoad;
    private long generation;
    private boolean loadRequested;

    BlueprintLibraryRepository() {
        this(BlueprintLibraryLoadWorker::submit,
                BlueprintLibraryRepository::captureClientRegistryAccess);
    }

    BlueprintLibraryRepository(LoadSubmitter loadSubmitter,
            Supplier<RegistryAccess> registryAccessSupplier) {
        this(loadSubmitter, registryAccessSupplier, BlueprintPanelFiles::blueprintFolder);
    }

    BlueprintLibraryRepository(LoadSubmitter loadSubmitter,
            Supplier<RegistryAccess> registryAccessSupplier,
            Supplier<Path> blueprintFolderSupplier) {
        this.loadSubmitter = loadSubmitter == null ? BlueprintLibraryLoadWorker::submit : loadSubmitter;
        this.registryAccessSupplier = registryAccessSupplier == null
                ? BlueprintLibraryRepository::captureClientRegistryAccess
                : registryAccessSupplier;
        this.blueprintFolderSupplier = blueprintFolderSupplier == null
                ? BlueprintPanelFiles::blueprintFolder : blueprintFolderSupplier;
    }

    void ensureLoaded(StatusSink status) {
        if (!loadRequested) ensureLoaded(status, registryAccessSupplier.get());
    }

    /** 生产路径捕获注册表后启动 worker；重载不会在调用线程解析文件。 */
    void ensureLoaded(StatusSink status, RegistryAccess registryAccess) {
        if (loadRequested || registryAccess == null) return;
        startLoad(status, registryAccess);
    }

    void reload(StatusSink status) {
        cancelActiveLoad();
        generation++;
        loadRequested = false;
        fileRevisions.clear();
        results = newResultQueue();
        entries.clear();
    }

    void close() {
        cancelActiveLoad();
        generation++;
        loadRequested = false;
        fileRevisions.clear();
        results = newResultQueue();
        entries.clear();
    }

    /** 每 tick 最多发布一条结果；普通列表查询不会隐式消费后台队列。 */
    void pump(StatusSink status) {
        for (int count = 0; count < MAX_RESULTS_PER_PUMP; count++) {
            BlueprintLibraryLoadResult result = results.poll();
            if (result == null || result.generation() != generation) {
                if (result == null) return;
                continue;
            }
            if (result.complete()) {
                activeLoad = null;
                if (!result.scanError().isBlank()) {
                    setStatus(status, S2CBlueprintStatusPayload.ERROR,
                            "screen.rtsbuilding.blueprints.status.folder_failed", result.scanError());
                } else {
                    setStatus(status, S2CBlueprintStatusPayload.INFO,
                            "screen.rtsbuilding.blueprints.status.reloaded", "");
                }
                continue;
            }
            if (!isCurrentFileRevision(result) || result.path() == null) continue;

            BlueprintEntry entry;
            try {
                entry = result.error().isBlank()
                        ? BlueprintEntry.from(result.path(), result.fileName(), result.blueprint(), "")
                        : BlueprintEntry.error(result.path(), result.fileName(), result.error());
            } catch (Exception failure) {
                entry = BlueprintEntry.error(result.path(), result.fileName(), failureDetail(failure));
            }
            if (result.materialAnalysis() != null) {
                BlueprintMaterialInspector.rememberAnalysis(result.blueprint(), result.materialAnalysis());
            }
            entries.removeIf(existing -> existing.fileName().equals(result.fileName()));
            entries.add(entry);
            entries.sort(ENTRY_ORDER);
        }
    }

    int size() { return entries.size(); }
    boolean isEmpty() { return entries.isEmpty(); }
    BlueprintEntry get(int index) { return entries.get(index); }
    List<BlueprintEntry> copyEntries() { return List.copyOf(entries); }
    boolean contains(BlueprintEntry entry) { return entries.contains(entry); }
    int indexOf(BlueprintEntry entry) { return entries.indexOf(entry); }

    BlueprintEntry findByFileName(String fileName) {
        if (fileName == null) return null;
        for (BlueprintEntry entry : entries) {
            if (entry.fileName().equals(fileName)) return entry;
        }
        return null;
    }

    int indexOfFileName(String fileName) {
        if (fileName == null) return -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).fileName().equals(fileName)) return i;
        }
        return -1;
    }

    /** 捕获保存回写条目时推进修订，使已排队的同名旧结果失效。 */
    void addOrReplace(Path path, RtsBlueprint blueprint) {
        if (path == null || path.getFileName() == null || blueprint == null) return;
        String fileName = path.getFileName().toString();
        fileRevisions.merge(fileName, 1L, Long::sum);
        entries.removeIf(entry -> entry.fileName().equals(fileName));
        entries.add(BlueprintEntry.from(path, fileName, blueprint, ""));
        entries.sort(ENTRY_ORDER);
    }

    private void startLoad(StatusSink status, RegistryAccess registryAccess) {
        loadRequested = true;
        results = newResultQueue();
        try {
            activeLoad = loadSubmitter.submit(generation, blueprintFolderSupplier.get(), registryAccess,
                    fileRevisions, results);
            setStatus(status, S2CBlueprintStatusPayload.INFO,
                    "screen.rtsbuilding.blueprints.status.loading", "");
        } catch (RuntimeException failure) {
            activeLoad = null;
            loadRequested = false;
            setStatus(status, S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.folder_failed", failureDetail(failure));
        }
    }

    private boolean isCurrentFileRevision(BlueprintLibraryLoadResult result) {
        return fileRevisions.getOrDefault(result.fileName(), 0L) == result.fileRevision();
    }

    private void cancelActiveLoad() {
        if (activeLoad != null) {
            activeLoad.cancel();
            activeLoad = null;
        }
    }

    private static BlockingQueue<BlueprintLibraryLoadResult> newResultQueue() {
        return new ArrayBlockingQueue<>(RESULT_QUEUE_CAPACITY);
    }

    private static RegistryAccess captureClientRegistryAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
    }

    private static void setStatus(StatusSink status, byte code, String key, String detail) {
        if (status != null) status.set(code, key, detail);
    }

    private static String failureDetail(Throwable throwable) {
        Throwable cause = throwable == null ? null
                : throwable.getCause() == null ? throwable : throwable.getCause();
        if (cause == null) return "Unknown error";
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    interface LoadSubmitter {
        LoadHandle submit(long generation, Path folder, RegistryAccess registryAccess,
                ConcurrentMap<String, Long> fileRevisions,
                BlockingQueue<BlueprintLibraryLoadResult> results);
    }

    @FunctionalInterface
    interface LoadHandle { void cancel(); }

    @FunctionalInterface
    interface StatusSink { void set(byte status, String messageKey, String detail); }
}
