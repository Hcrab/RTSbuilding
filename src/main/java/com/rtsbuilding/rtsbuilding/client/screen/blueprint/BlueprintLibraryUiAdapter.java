package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiAction;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiTransition;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 将旧版蓝图库的真实文件/选择状态投影为无平台依赖的 Core 快照。
 *
 * <p>文件选择框、磁盘读写、捕获任务和放置状态不在这里复制；动作仍回到
 * {@link BlueprintPanel} 的既有生产入口，因此远程材料统计和捕获流程不增加新门槛。</p>
 */
final class BlueprintLibraryUiAdapter {
    private BlueprintLibraryUiAdapter() {
    }

    static BlueprintLibraryUiState snapshot(ClientRtsController controller) {
        return snapshot(controller, Set.of());
    }

    static BlueprintLibraryUiState snapshotForViewport(
            ClientRtsController controller,
            int listWidth,
            int listHeight) {
        BlueprintLibraryUiState lightweight = snapshot(controller);
        if (lightweight.captureLocked || lightweight.entries.isEmpty()) {
            return lightweight;
        }
        List<BlueprintLibraryUiEntry> filtered = lightweight.filteredEntries();
        BlueprintLibraryLayout.VisibleWindow window = BlueprintLibraryLayout.visibleWindow(
                filtered.size(), lightweight.scrollRows, listWidth, listHeight);
        Set<String> details = new HashSet<>();
        for (int index = window.fromIndex; index < window.toIndex; index++) {
            details.add(filtered.get(index).fileName);
        }
        if (!lightweight.selectedFileName.isBlank()) {
            details.add(lightweight.selectedFileName);
        }
        return snapshot(controller, details);
    }

    private static BlueprintLibraryUiState snapshot(
            ClientRtsController controller,
            Set<String> detailedFiles) {
        List<BlueprintLibraryUiEntry> rows = new ArrayList<>();
        for (BlueprintEntry entry : BlueprintPanel.libraryEntries()) {
            boolean detailed = detailedFiles.contains(entry.fileName());
            BuildStats stats = detailed
                    ? BlueprintMaterialInspector.buildStats(entry, controller)
                    : new BuildStats(0, 0, 0, 0, 0, 0);
            List<String> previewIds = new ArrayList<>();
            if (detailed) {
                for (ItemStack stack : entry.previewItems()) {
                    if (!stack.isEmpty()) {
                        previewIds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    }
                }
            }
            rows.add(new BlueprintLibraryUiEntry(
                    entry.fileName(), entry.name(),
                    entry.format().extension().toUpperCase(Locale.ROOT),
                    entry.sizeText(), entry.blockCount(), stats.percent(),
                    detailed ? BlueprintMaterialInspector.materialSummary(
                            entry, controller, stats) : "",
                    entry.error(), previewIds));
        }
        BlueprintEntry selected = BlueprintPanel.librarySelectedEntry();
        return new BlueprintLibraryUiState(
                rows,
                BlueprintPanel.libraryQuery(),
                BlueprintPanel.librarySearchFocused(),
                BlueprintPanel.libraryScrollRows(),
                selected == null ? "" : selected.fileName(),
                BlueprintPanel.isCaptureModeActive(),
                BlueprintPanel.isCaptureSaving(),
                BlueprintPanel.statusText().getString(),
                BlueprintPanel.statusColor());
    }

    static boolean dispatch(
            BlueprintLibraryUiAction action,
            ClientRtsController controller) {
        BlueprintLibraryUiTransition transition = BlueprintLibraryUiReducer.apply(
                snapshot(controller), action);
        switch (transition.command) {
            case OPEN_FOLDER -> BlueprintPanel.openBlueprintFolderFromUi();
            case IMPORT_FILE -> BlueprintPanel.importBlueprintFileFromUi();
            case SYNC_CREATE -> BlueprintPanel.syncCreateBlueprintsFromUi();
            case TOGGLE_CAPTURE -> BlueprintPanel.toggleCaptureModeFromUi();
            case SET_QUERY, FOCUS_SEARCH, BLUR_SEARCH, SCROLL_ROWS ->
                    BlueprintPanel.applyLibraryViewState(
                            transition.state.query,
                            transition.state.searchFocused,
                            transition.state.scrollRows);
            case SELECT_ENTRY -> {
                return BlueprintPanel.selectLibraryEntry(action.text);
            }
            case SAVE_AS_ENTRY -> {
                return BlueprintPanel.saveLibraryEntryAs(action.text);
            }
            case RENAME_ENTRY -> {
                return BlueprintPanel.renameLibraryEntry(action.text);
            }
            case DELETE_ENTRY -> {
                return BlueprintPanel.deleteLibraryEntry(action.text);
            }
            case NONE -> {
                return false;
            }
        }
        return true;
    }
}
