package com.rtsbuilding.rtsbuilding.uicore.blueprint;

/** 一次蓝图 UI 输入的纯状态结果和必须交给生产适配器执行的命令。 */
public final class BlueprintUiTransition {
    public enum Command {
        NONE, SELECT_PREVIOUS, SELECT_NEXT, OPEN_MATERIALS, CLOSE_MATERIALS, SCROLL_MATERIALS,
        ACCEPT_CAPTURE_POINT, MOVE_CAPTURE, RESIZE_CAPTURE, SET_CAPTURE_SIZE, SAVE_CAPTURE, CANCEL_CAPTURE,
        PIN_PREVIEW, SET_ANCHOR, NUDGE_ANCHOR, NUDGE_ANCHOR_RELATIVE,
        ROTATE_Y, ROTATE_X, ROTATE_Z, RESET_ROTATION,
        BUILD, CLEAR, OPEN_NAME_CAPTURE, OPEN_NAME_RENAME, SET_NAME_DRAFT, APPEND_NAME_CHAR,
        BACKSPACE_NAME, CONFIRM_NAME, CANCEL_NAME
    }

    public final BlueprintUiState state;
    public final Command command;
    public final BlueprintUiAction action;

    public BlueprintUiTransition(BlueprintUiState state, Command command, BlueprintUiAction action) {
        this.state = state;
        this.command = command;
        this.action = action;
    }
}
