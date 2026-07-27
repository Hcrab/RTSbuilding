package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiAction;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;
import net.minecraft.client.gui.FontRenderer;

import java.util.List;

/**
 * 底部蓝图库左键与滚轮的唯一生产解析边界。
 *
 * <p>本类只把 Kit 的半开 {@link BlueprintLibraryLayout.Hit} 转成现有 Core 动作；
 * 文件选择器、磁盘写入、捕获状态和网络副作用仍由
 * {@link BlueprintLibraryUiAdapter} / {@link BlueprintPanel} 持有。</p>
 */
final class BlueprintLibraryPanelInput {
    private BlueprintLibraryPanelInput() {
    }

    static boolean mouseClicked(
            double mouseX,
            double mouseY,
            FontRenderer font,
            int x,
            int y,
            int width,
            int height,
            BlueprintLibraryUiState state,
            ClientRtsController controller) {
        BlueprintLibraryLayout.Geometry geometry =
                BlueprintLibraryLayout.geometry(
                        x,
                        y,
                        width,
                        height);
        BlueprintLibraryLayout.TopBar top =
                BlueprintLibraryPanelRenderer.topBar(
                        font,
                        x,
                        width,
                        state.captureLocked);
        BlueprintLibraryLayout.Hit hit =
                BlueprintLibraryLayout.hitAt(
                        geometry,
                        top,
                        state,
                        BlueprintLibraryPanelRenderer.actionWidths(font),
                        mouseX,
                        mouseY);
        switch (hit.control) {
            case OPEN_FOLDER:
                return dispatch(
                        BlueprintLibraryUiAction.Type.OPEN_FOLDER,
                        controller);
            case IMPORT_FILE:
                return dispatch(
                        BlueprintLibraryUiAction.Type.IMPORT_FILE,
                        controller);
            case SYNC_CREATE:
                return dispatch(
                        BlueprintLibraryUiAction.Type.SYNC_CREATE,
                        controller);
            case TOGGLE_CAPTURE:
                return dispatch(
                        BlueprintLibraryUiAction.Type.TOGGLE_CAPTURE,
                        controller);
            case CAPTURE_LOCKED_BODY:
                blur(controller);
                BlueprintPanel.setStatus(
                        S2CBlueprintStatusPayload.INFO,
                        state.captureSaving
                                ? "screen.rtsbuilding.blueprints.status.save_busy"
                                : "screen.rtsbuilding.blueprints.status.capture_locked",
                        "");
                return true;
            case SEARCH:
                return dispatch(
                        BlueprintLibraryUiAction.Type.FOCUS_SEARCH,
                        controller);
            case SAVE_AS:
            case RENAME:
            case DELETE:
            case SELECT:
                blur(controller);
                return dispatchEntry(hit, state, controller);
            case DETAILS:
            case LIST_GAP:
            case PANEL_GAP:
                blur(controller);
                return true;
            case NONE:
            default:
                blur(controller);
                return false;
        }
    }

    static boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollY,
            int x,
            int y,
            int width,
            int height,
            BlueprintLibraryUiState state,
            ClientRtsController controller) {
        BlueprintLibraryLayout.Geometry geometry =
                BlueprintLibraryLayout.geometry(
                        x,
                        y,
                        width,
                        height);
        if (!geometry.listBounds.contains(mouseX, mouseY)) {
            return false;
        }
        List<BlueprintLibraryUiEntry> filtered =
                state.filteredEntries();
        int next = BlueprintLibraryLayout.scrollRows(
                state.scrollRows,
                filtered.size(),
                geometry.listW,
                geometry.listH,
                scrollY);
        return BlueprintLibraryUiAdapter.dispatch(
                BlueprintLibraryUiAction.amount(
                        BlueprintLibraryUiAction.Type.SCROLL_ROWS,
                        next - state.scrollRows),
                controller);
    }

    private static boolean dispatchEntry(
            BlueprintLibraryLayout.Hit hit,
            BlueprintLibraryUiState state,
            ClientRtsController controller) {
        List<BlueprintLibraryUiEntry> filtered =
                state.filteredEntries();
        if (hit.filteredIndex < 0
                || hit.filteredIndex >= filtered.size()) {
            return true;
        }
        String fileName =
                filtered.get(hit.filteredIndex).fileName;
        BlueprintLibraryUiAction.Type type =
                hit.control == BlueprintLibraryLayout.Control.SAVE_AS
                        ? BlueprintLibraryUiAction.Type.SAVE_AS_ENTRY
                        : hit.control == BlueprintLibraryLayout.Control.RENAME
                                ? BlueprintLibraryUiAction.Type.RENAME_ENTRY
                                : hit.control == BlueprintLibraryLayout.Control.DELETE
                                        ? BlueprintLibraryUiAction.Type.DELETE_ENTRY
                                        : BlueprintLibraryUiAction.Type.SELECT_ENTRY;
        return BlueprintLibraryUiAdapter.dispatch(
                BlueprintLibraryUiAction.text(type, fileName),
                controller);
    }

    private static boolean dispatch(
            BlueprintLibraryUiAction.Type type,
            ClientRtsController controller) {
        return BlueprintLibraryUiAdapter.dispatch(
                BlueprintLibraryUiAction.simple(type),
                controller);
    }

    private static void blur(ClientRtsController controller) {
        BlueprintLibraryUiAdapter.dispatch(
                BlueprintLibraryUiAction.simple(
                        BlueprintLibraryUiAction.Type.BLUR_SEARCH),
                controller);
    }
}
