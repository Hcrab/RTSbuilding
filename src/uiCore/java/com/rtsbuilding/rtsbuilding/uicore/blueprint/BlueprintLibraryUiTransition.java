package com.rtsbuilding.rtsbuilding.uicore.blueprint;

/** 一次蓝图库输入的纯状态结果与生产副作用命令。 */
public final class BlueprintLibraryUiTransition {
    public enum Command {
        NONE, OPEN_FOLDER, IMPORT_FILE, SYNC_CREATE, TOGGLE_CAPTURE,
        SET_QUERY, FOCUS_SEARCH, BLUR_SEARCH, SCROLL_ROWS,
        SELECT_ENTRY, SAVE_AS_ENTRY, RENAME_ENTRY, DELETE_ENTRY
    }

    public final BlueprintLibraryUiState state;
    public final Command command;
    public final BlueprintLibraryUiAction action;

    public BlueprintLibraryUiTransition(BlueprintLibraryUiState state, Command command,
                                        BlueprintLibraryUiAction action) {
        this.state = state;
        this.command = command;
        this.action = action;
    }
}
