package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;

import java.util.List;
import java.util.function.Consumer;

/**
 * 蓝图库工作区的唯一可变 owner：负责仓储、选择、搜索、滚动和文件任务结果落地。
 *
 * <p>仓储结果会分帧发布，所以选择不能再只保存一个会随排序变化的数组下标。
 * 这里以文件名作为稳定身份；重载/导入的延后选择也只记住文件名，等对应行真正发布后
 * 再静默选中，避免旧条目、错误行或排序变化把预览指向别的文件。</p>
 *
 * <p>它不拥有捕获、旋转、虚影、弹窗或 Minecraft 绘制。选择变化通过回调交给这些工作区，
 * 从而避免 {@link BlueprintPanel} 再保存一套仓储索引和文件操作顺序。</p>
 */
final class BlueprintLibrarySession {
    private final BlueprintLibraryRepository repository;
    private final BlueprintLibraryRepository.StatusSink status;
    private final Consumer<BlueprintEntry> selectionChanged;
    private String selectedFileName = "";
    private String pendingSelectionFileName = "";
    private int scrollRows;
    private String query = "";
    private boolean searchFocused;

    BlueprintLibrarySession(BlueprintLibraryRepository.StatusSink status,
            Consumer<BlueprintEntry> selectionChanged) {
        this(new BlueprintLibraryRepository(), status, selectionChanged);
    }

    /** 可注入仓储构造器供离线行为测试使用；生产面板使用默认仓储。 */
    BlueprintLibrarySession(BlueprintLibraryRepository repository,
            BlueprintLibraryRepository.StatusSink status,
            Consumer<BlueprintEntry> selectionChanged) {
        this.repository = repository == null ? new BlueprintLibraryRepository() : repository;
        this.status = status;
        this.selectionChanged = selectionChanged == null ? ignored -> { } : selectionChanged;
    }

    void ensureLoaded() {
        repository.ensureLoaded(status);
        applyPendingSelection();
        BlueprintRotationDefaults.ensureLoaded();
    }

    void reload() {
        BlueprintRotationDefaults.ensureLoaded();
        selectedFileName = "";
        pendingSelectionFileName = "";
        scrollRows = 0;
        selectionChanged.accept(null);
        repository.reload(status);
    }

    /** 客户端离开世界时可由生命周期 owner 调用。 */
    void close() {
        selectedFileName = "";
        pendingSelectionFileName = "";
        selectionChanged.accept(null);
        repository.close();
    }

    int size() {
        ensureLoaded();
        return repository.size();
    }

    List<BlueprintEntry> entries() {
        ensureLoaded();
        return repository.copyEntries();
    }

    BlueprintEntry selectedEntry() {
        if (selectedFileName.isBlank()) {
            return null;
        }
        return repository.findByFileName(selectedFileName);
    }

    int selectedIndex() {
        return repository.indexOfFileName(selectedFileName);
    }

    String query() {
        return query;
    }

    boolean searchFocused() {
        return searchFocused;
    }

    int scrollRows() {
        return scrollRows;
    }

    void setSearchFocused(boolean focused) {
        searchFocused = focused;
    }

    void applyViewState(String query, boolean focused, int scrollRows) {
        String value = query == null ? "" : query;
        this.query = value.substring(0, Math.min(96, value.length()));
        this.searchFocused = focused;
        this.scrollRows = Math.max(0, scrollRows);
    }

    void selectRelative(int delta) {
        ensureLoaded();
        if (repository.isEmpty() || delta == 0) {
            return;
        }
        int currentIndex = repository.indexOfFileName(selectedFileName);
        int start = currentIndex >= 0 ? currentIndex : 0;
        for (int step = 1; step <= repository.size(); step++) {
            int index = Math.floorMod(start + delta * step, repository.size());
            BlueprintEntry entry = repository.get(index);
            if (entry.error().isBlank()) {
                pendingSelectionFileName = "";
                select(entry);
                return;
            }
        }
    }

    boolean selectByFileName(String fileName) {
        ensureLoaded();
        pendingSelectionFileName = "";
        BlueprintEntry entry = entryByFileNameWithoutPump(fileName);
        if (entry == null) {
            return false;
        }
        select(entry);
        return true;
    }

    boolean saveAs(String fileName) {
        BlueprintEntry entry = entryByFileName(fileName);
        if (entry == null || !entry.error().isBlank()) {
            return false;
        }
        applyFileOperation(BlueprintLibraryFileOperations.saveAs(entry));
        return true;
    }

    boolean delete(String fileName) {
        BlueprintEntry entry = entryByFileName(fileName);
        if (entry == null) {
            status.set(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.delete_target_changed", "");
            return false;
        }
        applyFileOperation(BlueprintLibraryFileOperations.delete(entry));
        return true;
    }

    BlueprintEntry entryByFileName(String fileName) {
        ensureLoaded();
        return entryByFileNameWithoutPump(fileName);
    }

    boolean contains(BlueprintEntry entry) {
        return repository.contains(entry);
    }

    void rename(BlueprintEntry entry, String requestedName) {
        if (entry == null || !repository.contains(entry)) {
            status.set(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.no_selection", "");
            return;
        }
        applyFileOperation(BlueprintLibraryFileOperations.rename(entry, requestedName));
    }

    void applyFileOperation(BlueprintLibraryFileOperations.Result result) {
        if (result == null) {
            return;
        }
        if (result.reload()) {
            reload();
        }
        if (!result.selectedFileName().isBlank()) {
            if (result.selectionMode() == BlueprintLibraryFileOperations.SelectionMode.FULL) {
                BlueprintEntry entry = entryByFileName(result.selectedFileName());
                if (entry != null) {
                    pendingSelectionFileName = "";
                    select(entry);
                } else {
                    pendingSelectionFileName = result.selectedFileName();
                }
            } else if (result.selectionMode()
                    == BlueprintLibraryFileOperations.SelectionMode.INDEX_ONLY) {
                pendingSelectionFileName = result.selectedFileName();
                applyPendingSelection();
            }
        }
        if (result.status() != null && !result.messageKey().isBlank()) {
            status.set(result.status(), result.messageKey(), result.detail());
        }
    }

    BlueprintCaptureSaveCoordinator.Completion pollCaptureSave(
            BlueprintCaptureController capture) {
        BlueprintCaptureSaveCoordinator.Completion completion =
                BlueprintCaptureSaveCoordinator.poll(capture, repository);
        if (completion != null && !completion.selectedFileName().isBlank()) {
            selectIndexByFileName(completion.selectedFileName());
        }
        return completion;
    }

    void clearSelection() {
        selectedFileName = "";
        pendingSelectionFileName = "";
        selectionChanged.accept(null);
    }

    /** 发布只由客户端 tick 驱动，普通 GUI/预览查询不能隐式解析或一次消费多批结果。 */
    void tick() {
        repository.pump(status);
        applyPendingSelection();
    }

    private void applyPendingSelection() {
        if (pendingSelectionFileName.isBlank()) {
            return;
        }
        BlueprintEntry entry = entryByFileNameWithoutPump(pendingSelectionFileName);
        if (entry == null) {
            return;
        }
        pendingSelectionFileName = "";
        selectSilently(entry);
    }

    private BlueprintEntry entryByFileNameWithoutPump(String fileName) {
        return repository.findByFileName(fileName);
    }

    private void selectIndexByFileName(String fileName) {
        BlueprintEntry entry = entryByFileNameWithoutPump(fileName);
        if (entry != null) {
            pendingSelectionFileName = "";
            selectSilently(entry);
        } else {
            pendingSelectionFileName = fileName;
        }
    }

    private void select(BlueprintEntry entry) {
        selectSilently(entry);
        status.set(
                entry.error().isBlank()
                        ? S2CBlueprintStatusPayload.INFO
                        : S2CBlueprintStatusPayload.ERROR,
                entry.error().isBlank()
                        ? "screen.rtsbuilding.blueprints.status.selected"
                        : "screen.rtsbuilding.blueprints.status.parse_failed",
                entry.error().isBlank() ? entry.name() : entry.error());
    }

    private void selectSilently(BlueprintEntry entry) {
        selectedFileName = entry == null ? "" : entry.fileName();
        selectionChanged.accept(entry);
    }
}
