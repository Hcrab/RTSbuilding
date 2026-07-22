package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintInt3;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiAction;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiState;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiTransition;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintMaterialUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 生产 BlueprintPanel 与纯 Java 蓝图 UI 状态机之间的唯一适配边界。
 * 本类读取现有权威业务状态并执行既有文件/世界/网络命令，不复制这些副作用。
 */
final class BlueprintUiStateAdapter {
    private BlueprintUiStateAdapter() {
    }

    static BlueprintUiState snapshot() {
        return snapshot(null);
    }

    static BlueprintUiState snapshot(ClientRtsController controller) {
        BlueprintUiState.Mode mode;
        if (BlueprintPanel.isCaptureModeActive()) {
            mode = BlueprintPanel.isCaptureSaving()
                    ? BlueprintUiState.Mode.CAPTURE_SAVING
                    : BlueprintPanel.isCaptureSelectionComplete()
                            ? BlueprintUiState.Mode.CAPTURE_READY
                            : BlueprintPanel.getCapturePointA() == null
                                    ? BlueprintUiState.Mode.CAPTURE_WAITING_FIRST
                                    : BlueprintUiState.Mode.CAPTURE_WAITING_SECOND;
        } else if (BlueprintPanel.hasPinnedPreview()) {
            mode = BlueprintUiState.Mode.PLACEMENT_PINNED;
        } else if (BlueprintPanel.hasSelectedBlueprint()) {
            mode = BlueprintUiState.Mode.PLACEMENT_SELECTED;
        } else {
            mode = BlueprintUiState.Mode.HIDDEN;
        }
        BlockPos anchor = BlueprintPanel.getPinnedAnchor();
        BlockPos pointA = BlueprintPanel.getCapturePointA();
        BlockPos pointB = BlueprintPanel.getCapturePointB();
        return new BlueprintUiState(
                mode,
                BlueprintPanel.selectedBlueprintName(),
                BlueprintPanel.selectedBlueprintSizeText(),
                BlueprintPanel.selectedBlueprintIndex(),
                BlueprintPanel.blueprintEntryCount(),
                new BlueprintInt3(BlueprintPanel.captureSizeX(), BlueprintPanel.captureSizeY(),
                        BlueprintPanel.captureSizeZ()),
                toCore(pointA), toCore(pointB),
                BlueprintPanel.countCaptureBlocks(),
                mode == BlueprintUiState.Mode.CAPTURE_SAVING
                        ? BlueprintPanel.captureSaveProgressLine()
                        : BlueprintPanel.statusText().getString(),
                BlueprintPanel.statusColor(),
                anchor == null ? null : new BlueprintInt3(anchor.getX(), anchor.getY(), anchor.getZ()),
                BlueprintPanel.getYRotationSteps(), BlueprintPanel.getXRotationSteps(),
                BlueprintPanel.getZRotationSteps(),
                BlueprintPanel.isMaterialDialogOpen(), BlueprintPanel.materialDialogScroll(),
                BlueprintPanel.isNameDialogOpen(), BlueprintPanel.isNameDialogCaptureMode(),
                BlueprintPanel.nameDialogValue(), BlueprintPanel.nameDialogReplaceOnType(),
                materialState(controller));
    }

    static BlueprintMaterialUiState materialState(ClientRtsController controller) {
        BlueprintEntry entry = BlueprintPanel.materialDialogEntry();
        if (entry == null) {
            return BlueprintMaterialUiState.EMPTY;
        }
        BuildStats stats = BlueprintMaterialInspector.buildStats(entry, controller);
        List<BlueprintMaterialUiState.Row> rows = new ArrayList<>();
        for (DetailLine line : BlueprintMaterialInspector.detailLines(entry, controller)) {
            String iconId = line.preview().isEmpty() ? ""
                    : BuiltInRegistries.ITEM.getKey(line.preview().getItem()).toString();
            rows.add(new BlueprintMaterialUiState.Row(
                    iconId, line.label(), line.detail(), line.color()));
        }
        return new BlueprintMaterialUiState(entry.name(), stats.percent(), stats.buildable(), stats.total(),
                stats.missingTypes(), stats.unsupportedTypes(), stats.missingBlockTypes(), rows);
    }

    static boolean dispatch(BlueprintUiAction action, ClientRtsController controller) {
        BlueprintUiTransition transition = BlueprintUiReducer.apply(snapshot(), action);
        switch (transition.command) {
            case SELECT_PREVIOUS:
                BlueprintPanel.selectRelativeBlueprint(-1);
                return true;
            case SELECT_NEXT:
                BlueprintPanel.selectRelativeBlueprint(1);
                return true;
            case OPEN_MATERIALS:
                BlueprintPanel.openMaterialDialog();
                return true;
            case CLOSE_MATERIALS:
                BlueprintPanel.closeMaterialDialog();
                return true;
            case SCROLL_MATERIALS:
                BlueprintPanel.setMaterialDialogScroll(transition.state.materialScroll);
                return true;
            case SAVE_CAPTURE:
                BlueprintPanel.saveCapturedArea();
                return true;
            case CANCEL_CAPTURE:
                BlueprintPanel.cancelCaptureMode();
                return true;
            case RESIZE_CAPTURE:
                BlueprintPanel.adjustCaptureSize(action.x, action.y, action.z);
                return true;
            case SET_CAPTURE_SIZE:
                BlueprintPanel.setCaptureSize(action.x, action.y, action.z);
                return true;
            case ACCEPT_CAPTURE_POINT:
                return BlueprintPanel.acceptCapturePointDirect(new BlockPos(action.x, action.y, action.z));
            case MOVE_CAPTURE:
                BlueprintPanel.moveCaptureSelection(action.x, action.y, action.z);
                return true;
            case SET_ANCHOR:
            case PIN_PREVIEW:
                return BlueprintPanel.setPinnedAnchor(new BlockPos(action.x, action.y, action.z), controller);
            case NUDGE_ANCHOR:
                return BlueprintPanel.nudgePinnedAnchor(action.x, action.y, action.z, controller);
            case NUDGE_ANCHOR_RELATIVE:
                return BlueprintPanel.nudgePinnedAnchorRelative(action.x, action.y, action.z, controller);
            case ROTATE_Y:
                return BlueprintPanel.rotateSelectedBlueprintY(action.y);
            case ROTATE_X:
                return BlueprintPanel.rotateSelectedBlueprintX(action.x);
            case ROTATE_Z:
                return BlueprintPanel.rotateSelectedBlueprintZ(action.z);
            case RESET_ROTATION:
                BlueprintPanel.resetSelectedBlueprintRotation();
                return true;
            case BUILD:
                return BlueprintPanel.confirmPinnedPreview();
            case CLEAR:
                BlueprintPanel.clearSelectedBlueprint();
                return true;
            case SET_NAME_DRAFT:
            case APPEND_NAME_CHAR:
            case BACKSPACE_NAME:
                BlueprintPanel.setNameDialogValueFromUi(transition.state.nameDraft);
                return true;
            case CONFIRM_NAME:
                BlueprintPanel.confirmActiveNameDialog();
                return true;
            case CANCEL_NAME:
                BlueprintPanel.cancelActiveNameDialog();
                return true;
            case NONE:
            default:
                return false;
        }
    }

    private static BlueprintInt3 toCore(BlockPos pos) {
        return pos == null ? null : new BlueprintInt3(pos.getX(), pos.getY(), pos.getZ());
    }
}
