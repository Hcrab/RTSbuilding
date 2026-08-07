package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiAction;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;
import net.minecraft.client.gui.Font;

import java.util.List;

/**
 * 蓝图库左键与滚轮的唯一生产命中边界。
 *
 * <p>命中检测完全由 Kit 的半开矩形给出，随后转换为 Core 动作；真实文件操作和捕获
 * 状态继续委托给蓝图库原有后端。</p>
 */
final class BlueprintLibraryPanelInput {
    private BlueprintLibraryPanelInput() {
    }

    static boolean mouseClicked(
            double mouseX,
            double mouseY,
            Font font,
            int x,
            int y,
            int width,
            int height,
            BlueprintLibraryUiState state,
            ClientRtsController controller) {
        BlueprintLibraryLayout.Geometry geometry = BlueprintLibraryLayout.geometry(
                x, y, width, height);
        BlueprintLibraryLayout.TopBar top = BlueprintLibraryPanelRenderer.topBar(
                font, x, width, state.captureLocked);
        BlueprintLibraryLayout.Hit hit = BlueprintLibraryLayout.hitAt(
                geometry, top, state,
                BlueprintLibraryPanelRenderer.actionWidths(font), mouseX, mouseY);
        return switch (hit.control) {
            case OPEN_FOLDER -> dispatch(BlueprintLibraryUiAction.Type.OPEN_FOLDER, controller);
            case IMPORT_FILE -> dispatch(BlueprintLibraryUiAction.Type.IMPORT_FILE, controller);
            case SYNC_CREATE -> dispatch(BlueprintLibraryUiAction.Type.SYNC_CREATE, controller);
            case TOGGLE_CAPTURE -> dispatch(BlueprintLibraryUiAction.Type.TOGGLE_CAPTURE, controller);
            case CAPTURE_LOCKED_BODY -> {
                blur(controller);
                BlueprintPanel.setStatus(S2CBlueprintStatusPayload.INFO,
                        state.captureSaving
                                ? "screen.rtsbuilding.blueprints.status.save_busy"
                                : "screen.rtsbuilding.blueprints.status.capture_locked",
                        "");
                yield true;
            }
            case SEARCH -> dispatch(BlueprintLibraryUiAction.Type.FOCUS_SEARCH, controller);
            case SAVE_AS, RENAME, DELETE, SELECT -> {
                blur(controller);
                yield dispatchEntry(hit, state, controller);
            }
            case DETAILS, LIST_GAP, PANEL_GAP -> {
                blur(controller);
                yield true;
            }
            case NONE -> {
                blur(controller);
                yield false;
            }
        };
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
        BlueprintLibraryLayout.Geometry geometry = BlueprintLibraryLayout.geometry(
                x, y, width, height);
        if (!geometry.listBounds.contains(mouseX, mouseY)) {
            return false;
        }
        List<BlueprintLibraryUiEntry> filtered = state.filteredEntries();
        int next = BlueprintLibraryLayout.scrollRows(
                state.scrollRows, filtered.size(), geometry.listW, geometry.listH, scrollY);
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
        List<BlueprintLibraryUiEntry> filtered = state.filteredEntries();
        if (hit.filteredIndex < 0 || hit.filteredIndex >= filtered.size()) {
            return true;
        }
        String fileName = filtered.get(hit.filteredIndex).fileName;
        BlueprintLibraryUiAction.Type type = switch (hit.control) {
            case SAVE_AS -> BlueprintLibraryUiAction.Type.SAVE_AS_ENTRY;
            case RENAME -> BlueprintLibraryUiAction.Type.RENAME_ENTRY;
            case DELETE -> BlueprintLibraryUiAction.Type.DELETE_ENTRY;
            default -> BlueprintLibraryUiAction.Type.SELECT_ENTRY;
        };
        return BlueprintLibraryUiAdapter.dispatch(
                BlueprintLibraryUiAction.text(type, fileName), controller);
    }

    private static boolean dispatch(
            BlueprintLibraryUiAction.Type type,
            ClientRtsController controller) {
        return BlueprintLibraryUiAdapter.dispatch(
                BlueprintLibraryUiAction.simple(type), controller);
    }

    private static void blur(ClientRtsController controller) {
        BlueprintLibraryUiAdapter.dispatch(
                BlueprintLibraryUiAction.simple(BlueprintLibraryUiAction.Type.BLUR_SEARCH),
                controller);
    }
}
