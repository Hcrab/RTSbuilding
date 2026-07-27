package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;

import java.util.List;
import java.util.function.Consumer;

/**
 * 蓝图库工作区的唯一可变 owner：负责仓储、选择、搜索、滚动和文件任务结果落地。
 *
 * <p>它不拥有捕获、旋转、虚影、弹窗或 Minecraft 绘制。选择变化通过回调交给这些工作区，
 * 从而避免 {@link BlueprintPanel} 再保存一套仓储索引和文件操作顺序。</p>
 */
final class BlueprintLibrarySession {
    private final BlueprintLibraryRepository repository = new BlueprintLibraryRepository();
    private final BlueprintLibraryRepository.StatusSink status;
    private final Consumer<BlueprintEntry> selectionChanged;
    private int selectedIndex = -1;
    private int scrollRows;
    private String query = "";
    private boolean searchFocused;

    BlueprintLibrarySession(BlueprintLibraryRepository.StatusSink status,
            Consumer<BlueprintEntry> selectionChanged) {
        this.status = status;
        this.selectionChanged = selectionChanged;
    }

    void ensureLoaded() {
        repository.ensureLoaded(status);
        BlueprintRotationDefaults.ensureLoaded();
    }

    void reload() {
        BlueprintRotationDefaults.ensureLoaded();
        selectedIndex = -1;
        scrollRows = 0;
        selectionChanged.accept(null);
        repository.reload(status);
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
        return selectedIndex >= 0 && selectedIndex < repository.size()
                ? repository.get(selectedIndex)
                : null;
    }

    int selectedIndex() {
        return selectedIndex;
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
        int start = selectedIndex >= 0 && selectedIndex < repository.size()
                ? selectedIndex
                : 0;
        for (int step = 1; step <= repository.size(); step++) {
            int index = Math.floorMod(start + delta * step, repository.size());
            BlueprintEntry entry = repository.get(index);
            if (entry.error().trim().isEmpty()) {
                select(entry);
                return;
            }
        }
    }

    boolean selectByFileName(String fileName) {
        BlueprintEntry entry = entryByFileName(fileName);
        if (entry == null) {
            return false;
        }
        select(entry);
        return true;
    }

    boolean saveAs(String fileName) {
        BlueprintEntry entry = entryByFileName(fileName);
        if (entry == null || !entry.error().trim().isEmpty()) {
            return false;
        }
        applyFileOperation(BlueprintLibraryFileOperations.saveAs(entry));
        return true;
    }

    boolean delete(String fileName) {
        BlueprintEntry entry = entryByFileName(fileName);
        if (entry == null) {
            return false;
        }
        applyFileOperation(BlueprintLibraryFileOperations.delete(entry));
        return true;
    }

    BlueprintEntry entryByFileName(String fileName) {
        ensureLoaded();
        return repository.findByFileName(fileName);
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
        if (!result.selectedFileName().trim().isEmpty()) {
            if (result.selectionMode() == BlueprintLibraryFileOperations.SelectionMode.FULL) {
                BlueprintEntry entry = entryByFileName(result.selectedFileName());
                if (entry != null) {
                    select(entry);
                }
            } else if (result.selectionMode()
                    == BlueprintLibraryFileOperations.SelectionMode.INDEX_ONLY) {
                selectIndexByFileName(result.selectedFileName());
            }
        }
        if (result.status() != null && !result.messageKey().trim().isEmpty()) {
            status.set(result.status(), result.messageKey(), result.detail());
        }
    }

    BlueprintCaptureSaveCoordinator.Completion pollCaptureSave(
            BlueprintCaptureController capture) {
        BlueprintCaptureSaveCoordinator.Completion completion =
                BlueprintCaptureSaveCoordinator.poll(capture, repository);
        if (completion != null && !completion.selectedFileName().trim().isEmpty()) {
            selectIndexByFileName(completion.selectedFileName());
        }
        return completion;
    }

    void clearSelection() {
        selectedIndex = -1;
        selectionChanged.accept(null);
    }

    private void selectIndexByFileName(String fileName) {
        int index = repository.indexOfFileName(fileName);
        if (index >= 0) {
            selectedIndex = index;
            selectionChanged.accept(repository.get(index));
        }
    }

    private void select(BlueprintEntry entry) {
        selectedIndex = repository.indexOf(entry);
        selectionChanged.accept(entry);
        status.set(
                entry.error().trim().isEmpty()
                        ? S2CBlueprintStatusPayload.INFO
                        : S2CBlueprintStatusPayload.ERROR,
                entry.error().trim().isEmpty()
                        ? "screen.rtsbuilding.blueprints.status.selected"
                        : "screen.rtsbuilding.blueprints.status.parse_failed",
                entry.error().trim().isEmpty() ? entry.name() : entry.error());
    }
}
