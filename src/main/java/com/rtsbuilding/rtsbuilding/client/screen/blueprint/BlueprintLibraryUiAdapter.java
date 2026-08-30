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
 * 底部蓝图空间与 Java 8 Core 列表模型的唯一生产适配边界。
 * 文件选择器、磁盘写入和 Minecraft 物品注册表仍只存在于这一侧。
 */
final class BlueprintLibraryUiAdapter {
    private BlueprintLibraryUiAdapter() {
    }

    static BlueprintLibraryUiState snapshot(ClientRtsController controller) {
        return snapshot(controller, Set.of());
    }

    /**
     * 为当前可见行和已选详情生成材料统计，其余行只搬运轻量文件元数据。
     * 这样搜索、按键和滚轮不会触发所有蓝图的逐方块扫描。
     */
    static BlueprintLibraryUiState snapshotForViewport(ClientRtsController controller,
                                                        int listWidth, int listHeight) {
        BlueprintLibraryUiState lightweight = snapshot(controller);
        if (lightweight.captureLocked || lightweight.entries.isEmpty()) {
            return lightweight;
        }
        List<BlueprintLibraryUiEntry> filtered = lightweight.filteredEntries();
        BlueprintLibraryLayout.VisibleWindow window = BlueprintLibraryLayout.visibleWindow(
                filtered.size(), lightweight.scrollRows, listWidth, listHeight);
        Set<String> detailedFiles = new HashSet<>();
        for (int index = window.fromIndex; index < window.toIndex; index++) {
            detailedFiles.add(filtered.get(index).fileName);
        }
        if (!lightweight.selectedFileName.isBlank()) {
            detailedFiles.add(lightweight.selectedFileName);
        }
        return snapshot(controller, detailedFiles);
    }

    private static BlueprintLibraryUiState snapshot(ClientRtsController controller,
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
                    entry.fileName(), entry.name(), entry.format().extension().toUpperCase(Locale.ROOT),
                    entry.sizeText(), entry.blockCount(), stats.percent(),
                    detailed ? BlueprintMaterialInspector.materialSummary(entry, controller, stats) : "",
                    entry.error(), previewIds));
        }
        BlueprintEntry selected = BlueprintPanel.librarySelectedEntry();
        return new BlueprintLibraryUiState(rows, BlueprintPanel.libraryQuery(),
                BlueprintPanel.librarySearchFocused(), BlueprintPanel.libraryScrollRows(),
                selected == null ? "" : selected.fileName(), BlueprintPanel.isCaptureModeActive(),
                BlueprintPanel.isCaptureSaving(), BlueprintPanel.statusText().getString(),
                BlueprintPanel.statusColor());
    }

    static boolean dispatch(BlueprintLibraryUiAction action, ClientRtsController controller) {
        BlueprintLibraryUiTransition transition = BlueprintLibraryUiReducer.apply(snapshot(controller), action);
        switch (transition.command) {
            case OPEN_FOLDER:
                BlueprintPanel.openBlueprintFolderFromUi();
                return true;
            case IMPORT_FILE:
                BlueprintPanel.importBlueprintFileFromUi();
                return true;
            case SYNC_CREATE:
                BlueprintPanel.syncCreateBlueprintsFromUi();
                return true;
            case TOGGLE_CAPTURE:
                BlueprintPanel.toggleCaptureModeFromUi();
                return true;
            case SET_QUERY:
            case FOCUS_SEARCH:
            case BLUR_SEARCH:
            case SCROLL_ROWS:
                BlueprintPanel.applyLibraryViewState(transition.state.query,
                        transition.state.searchFocused, transition.state.scrollRows);
                return true;
            case SELECT_ENTRY:
                return BlueprintPanel.selectLibraryEntry(action.text);
            case SAVE_AS_ENTRY:
                return BlueprintPanel.saveLibraryEntryAs(action.text);
            case RENAME_ENTRY:
                return BlueprintPanel.renameLibraryEntry(action.text);
            case DELETE_ENTRY:
                return BlueprintPanel.deleteLibraryEntry(action.text);
            case NONE:
            default:
                return false;
        }
    }
}
