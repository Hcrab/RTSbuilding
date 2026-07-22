package com.rtsbuilding.rtsbuilding.uicore.bottom;

/** Reducer 结果；command 由生产适配器翻译为真实控制器副作用。 */
public final class BottomBarUiTransition {
    public enum Command { NONE, APPLY_VIEW_STATE, EXECUTE }
    public final BottomBarUiState state;
    public final Command command;
    public final BottomBarUiAction action;
    public BottomBarUiTransition(BottomBarUiState state, Command command, BottomBarUiAction action) {
        this.state=state; this.command=command; this.action=action;
    }
}
