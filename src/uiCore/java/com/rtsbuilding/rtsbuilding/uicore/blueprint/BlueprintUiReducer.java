package com.rtsbuilding.rtsbuilding.uicore.blueprint;

/** BlueprintWindowPanel 输入语义的权威纯逻辑 reducer。 */
public final class BlueprintUiReducer {
    private static final int WORLD_LIMIT = 30000000;

    private BlueprintUiReducer() {
    }

    public static BlueprintUiTransition apply(BlueprintUiState state, BlueprintUiAction action) {
        if (state == null || action == null) throw new IllegalArgumentException("state/action");
        BlueprintUiState next = state;
        switch (action.type) {
            case ACCEPT_CAPTURE_POINT:
                if (state.mode == BlueprintUiState.Mode.CAPTURE_WAITING_FIRST) {
                    next = state.withMode(BlueprintUiState.Mode.CAPTURE_WAITING_SECOND);
                } else if (state.mode == BlueprintUiState.Mode.CAPTURE_WAITING_SECOND) {
                    next = state.withMode(BlueprintUiState.Mode.CAPTURE_READY);
                }
                break;
            case RESIZE_CAPTURE:
                if (state.mode == BlueprintUiState.Mode.CAPTURE_READY) {
                    BlueprintInt3 size = state.captureSize.add(action.x, action.y, action.z);
                    next = state.withCaptureSize(new BlueprintInt3(
                            Math.max(1, size.x), Math.max(0, size.y), Math.max(1, size.z)));
                }
                break;
            case SET_CAPTURE_SIZE:
                if (state.mode == BlueprintUiState.Mode.CAPTURE_READY) {
                    next = state.withCaptureSize(new BlueprintInt3(
                            Math.max(1, action.x), Math.max(1, action.y), Math.max(1, action.z)));
                }
                break;
            case SAVE_CAPTURE:
                if (state.canSaveCapture()) next = state.openNameWindow(true, "", false);
                break;
            case CANCEL_CAPTURE:
            case CLEAR:
                next = state.withAnchor(null).withMode(BlueprintUiState.Mode.HIDDEN)
                        .withMaterialWindow(false).withNameWindow(false, false, "");
                break;
            case PIN_PREVIEW:
            case SET_ANCHOR:
                if (state.mode == BlueprintUiState.Mode.PLACEMENT_SELECTED
                        || state.mode == BlueprintUiState.Mode.PLACEMENT_PINNED) {
                    next = state.withAnchor(clamp(new BlueprintInt3(action.x, action.y, action.z)));
                }
                break;
            case NUDGE_ANCHOR:
                if (state.isPinned()) next = state.withAnchor(clamp(state.anchor.add(action.x, action.y, action.z)));
                break;
            case ROTATE_Y:
                next = state.withRotations(state.yRotationSteps + action.y,
                        state.xRotationSteps, state.zRotationSteps);
                break;
            case ROTATE_X:
                next = state.withRotations(state.yRotationSteps,
                        state.xRotationSteps + action.x, state.zRotationSteps);
                break;
            case ROTATE_Z:
                next = state.withRotations(state.yRotationSteps,
                        state.xRotationSteps, state.zRotationSteps + action.z);
                break;
            case RESET_ROTATION:
                next = state.withRotations(0, 0, 0);
                break;
            case OPEN_MATERIALS:
                if (state.mode == BlueprintUiState.Mode.PLACEMENT_SELECTED || state.isPinned()) {
                    next = state.withMaterialWindow(true);
                }
                break;
            case CLOSE_MATERIALS:
                next = state.withMaterialWindow(false);
                break;
            case SCROLL_MATERIALS:
                if (state.materialWindowOpen) {
                    next = state.withMaterialScroll(state.materialScroll + action.y);
                }
                break;
            case OPEN_NAME_CAPTURE:
                if (state.mode == BlueprintUiState.Mode.CAPTURE_READY) {
                    next = state.openNameWindow(true, action.text, false);
                }
                break;
            case OPEN_NAME_RENAME:
                next = state.openNameWindow(false, action.text, true);
                break;
            case SET_NAME_DRAFT:
                if (state.nameWindowOpen) next = state.withNameDraft(action.text);
                break;
            case APPEND_NAME_CHAR:
                if (state.nameWindowOpen) next = state.appendName(action.text);
                break;
            case BACKSPACE_NAME:
                if (state.nameWindowOpen) next = state.backspaceName();
                break;
            case CONFIRM_NAME:
                if (!state.nameWindowOpen || state.nameDraft.trim().isEmpty()) {
                    break;
                }
                next = state.closeNameWindow();
                if (state.captureNameMode) next = next.withMode(BlueprintUiState.Mode.CAPTURE_SAVING);
                break;
            case CANCEL_NAME:
                next = state.closeNameWindow();
                break;
            default:
                break;
        }
        BlueprintUiTransition.Command command = BlueprintUiTransition.Command.valueOf(action.type.name());
        if ((action.type == BlueprintUiAction.Type.BUILD && !state.canBuild())
                || (action.type == BlueprintUiAction.Type.SAVE_CAPTURE && !state.canSaveCapture())
                || (action.type == BlueprintUiAction.Type.CONFIRM_NAME
                        && (!state.nameWindowOpen || state.nameDraft.trim().isEmpty()))) {
            command = BlueprintUiTransition.Command.NONE;
        }
        return new BlueprintUiTransition(next, command, action);
    }

    private static BlueprintInt3 clamp(BlueprintInt3 value) {
        return new BlueprintInt3(clamp(value.x), clamp(value.y), clamp(value.z));
    }

    private static int clamp(int value) {
        return Math.max(-WORLD_LIMIT, Math.min(WORLD_LIMIT, value));
    }
}
