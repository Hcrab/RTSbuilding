package com.rtsbuilding.rtsbuilding.uicore.blueprint;

/** 底部蓝图空间搜索、选择和命令门槛的权威纯逻辑。 */
public final class BlueprintLibraryUiReducer {
    private BlueprintLibraryUiReducer() {
    }

    public static BlueprintLibraryUiTransition apply(BlueprintLibraryUiState state,
                                                      BlueprintLibraryUiAction action) {
        if (state == null || action == null) throw new IllegalArgumentException("state/action");
        BlueprintLibraryUiState next = state;
        BlueprintLibraryUiTransition.Command command =
                BlueprintLibraryUiTransition.Command.valueOf(action.type.name());
        switch (action.type) {
            case SET_QUERY:
                next = state.withQuery(action.text);
                break;
            case FOCUS_SEARCH:
                next = state.withSearchFocused(true);
                break;
            case BLUR_SEARCH:
                next = state.withSearchFocused(false);
                break;
            case SCROLL_ROWS:
                if (!state.captureLocked) next = state.withScrollRows(state.scrollRows + action.amount);
                else command = BlueprintLibraryUiTransition.Command.NONE;
                break;
            case TOGGLE_CAPTURE:
                if (state.captureSaving) command = BlueprintLibraryUiTransition.Command.NONE;
                else next = state.withCaptureLocked(!state.captureLocked);
                break;
            case SELECT_ENTRY:
                if (state.captureLocked || find(state, action.text) == null) {
                    command = BlueprintLibraryUiTransition.Command.NONE;
                } else {
                    next = state.withSelectedFileName(action.text);
                }
                break;
            case SAVE_AS_ENTRY:
            case RENAME_ENTRY:
                BlueprintLibraryUiEntry editable = find(state, action.text);
                if (state.captureLocked || editable == null || !editable.valid()) {
                    command = BlueprintLibraryUiTransition.Command.NONE;
                }
                break;
            case DELETE_ENTRY:
                if (state.captureLocked || find(state, action.text) == null) {
                    command = BlueprintLibraryUiTransition.Command.NONE;
                }
                break;
            default:
                break;
        }
        return new BlueprintLibraryUiTransition(next, command, action);
    }

    private static BlueprintLibraryUiEntry find(BlueprintLibraryUiState state, String fileName) {
        for (BlueprintLibraryUiEntry entry : state.entries) {
            if (entry.fileName.equals(fileName)) return entry;
        }
        return null;
    }
}
