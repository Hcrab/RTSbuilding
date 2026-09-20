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
 * <p>本类仍然是条目的唯一 owner，但磁盘枚举、读取和格式解析全部交给
 * {@link BlueprintLibraryLoadWorker}。客户端每次调用 {@link #pump(StatusSink)}
 * 只在主线程发布少量结果，并在这里创建带物品预览的 {@link BlueprintEntry}；
 * 因此 render 热路径不会同步解析整库。</p>
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

    /**
     * 可注入的构造器只供离线行为测试使用；生产路径仍使用唯一 daemon worker。
     */
    BlueprintLibraryRepository(
            LoadSubmitter loadSubmitter,
            Supplier<RegistryAccess> registryAccessSupplier) {
        this(loadSubmitter, registryAccessSupplier, BlueprintPanelFiles::blueprintFolder);
    }

    /**
     * 额外的目录 supplier 只为离线测试隔离临时目录，生产路径仍使用实例蓝图目录。
     */
    BlueprintLibraryRepository(
            LoadSubmitter loadSubmitter,
            Supplier<RegistryAccess> registryAccessSupplier,
            Supplier<Path> blueprintFolderSupplier) {
        this.loadSubmitter = loadSubmitter == null
                ? BlueprintLibraryLoadWorker::submit
                : loadSubmitter;
        this.registryAccessSupplier = registryAccessSupplier == null
                ? BlueprintLibraryRepository::captureClientRegistryAccess
                : registryAccessSupplier;
        this.blueprintFolderSupplier = blueprintFolderSupplier == null
                ? BlueprintPanelFiles::blueprintFolder
                : blueprintFolderSupplier;
    }

    void ensureLoaded(StatusSink status) {
        if (loadRequested) return;
        ensureLoaded(status, registryAccessSupplier.get());
    }

    /**
     * 由生产路径捕获注册表后启动任务；第二个重载让离线测试可以不初始化 Minecraft。
     */
    void ensureLoaded(StatusSink status, RegistryAccess registryAccess) {
        if (loadRequested || registryAccess == null) {
            return;
        }
        startLoad(status, registryAccess);
    }

    /**
     * 取消当前代任务并清空条目。真正的文件扫描在下一次 ensureLoaded 中异步启动。
     */
    void reload(StatusSink status) {
        cancelActiveLoad();
        generation++;
        loadRequested = false;
        fileRevisions.clear();
        results = newResultQueue();
        entries.clear();
    }

    /**
     * 客户端换世界或断开连接时取消当前任务，释放整库条目和未发布结果。
     */
    void close() {
        cancelActiveLoad();
        generation++;
        loadRequested = false;
        fileRevisions.clear();
        results = newResultQueue();
        entries.clear();
    }

    /**
     * 在客户端线程有界发布后台结果。删除/重载代次和捕获文件修订号会隔离旧结果。
     * 这里不再次查询磁盘；外部文件变更由下一次玩家重载发现。
     */
    void pump(StatusSink status) {
        for (int count = 0; count < MAX_RESULTS_PER_PUMP; count++) {
            BlueprintLibraryLoadResult result = results.poll();
            if (result == null || result.generation() != generation) {
                if (result == null) {
                    return;
                }
                continue;
            }
            if (result.complete()) {
                activeLoad = null;
                if (!result.scanError().isBlank()) {
                    setStatus(status,
                            S2CBlueprintStatusPayload.ERROR,
                            "screen.rtsbuilding.blueprints.status.folder_failed",
                            result.scanError());
                } else {
                    setStatus(status,
                            S2CBlueprintStatusPayload.INFO,
                            "screen.rtsbuilding.blueprints.status.reloaded",
                            "");
                }
                continue;
            }
            if (!isCurrentFileRevision(result) || result.path() == null) {
                continue;
            }

            // 只在这里创建 ItemStack/预览数据；后台结果本身永远不携带客户端对象。
            BlueprintEntry entry;
            try {
                entry = result.error().isBlank()
                        ? BlueprintEntry.from(result.path(), result.fileName(), result.blueprint(), "")
                        : BlueprintEntry.error(result.path(), result.fileName(), result.error());
            } catch (Exception failure) {
                // 保持旧加载器的逐文件隔离：物品预览/模组名称异常也只生成错误行。
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

    int size() {
        return entries.size();
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    BlueprintEntry get(int index) {
        return entries.get(index);
    }

    List<BlueprintEntry> copyEntries() {
        return List.copyOf(entries);
    }

    boolean contains(BlueprintEntry entry) {
        return entries.contains(entry);
    }

    int indexOf(BlueprintEntry entry) {
        return entries.indexOf(entry);
    }

    BlueprintEntry findByFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        for (BlueprintEntry entry : entries) {
            if (entry.fileName().equals(fileName)) {
                return entry;
            }
        }
        return null;
    }

    int indexOfFileName(String fileName) {
        if (fileName == null) {
            return -1;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).fileName().equals(fileName)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 捕获保存完成后在客户端线程立即写回条目。文件修订号会使已经在后台读取的旧结果
     * 失效，避免同名文件被旧解析结果覆盖。
     */
    void addOrReplace(Path path, RtsBlueprint blueprint) {
        if (path == null || path.getFileName() == null || blueprint == null) {
            return;
        }
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
            activeLoad = loadSubmitter.submit(
                    generation,
                    blueprintFolderSupplier.get(),
                    registryAccess,
                    fileRevisions,
                    results);
            setStatus(status,
                    S2CBlueprintStatusPayload.INFO,
                    "screen.rtsbuilding.blueprints.status.loading",
                    "");
        } catch (RuntimeException ex) {
            activeLoad = null;
            loadRequested = false;
            setStatus(status,
                    S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.folder_failed",
                    failureDetail(ex));
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
        return minecraft == null || minecraft.level == null
                ? null
                : minecraft.level.registryAccess();
    }

    private static void setStatus(
            StatusSink status,
            byte code,
            String messageKey,
            String detail) {
        if (status != null) {
            status.set(code, messageKey, detail);
        }
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

    @FunctionalInterface
    interface LoadSubmitter {
        LoadHandle submit(
                long generation,
                Path folder,
                RegistryAccess registryAccess,
                ConcurrentMap<String, Long> fileRevisions,
                BlockingQueue<BlueprintLibraryLoadResult> results);
    }

    @FunctionalInterface
    interface LoadHandle {
        void cancel();
    }

    @FunctionalInterface
    interface StatusSink {
        void set(byte status, String messageKey, String detail);
    }
}
