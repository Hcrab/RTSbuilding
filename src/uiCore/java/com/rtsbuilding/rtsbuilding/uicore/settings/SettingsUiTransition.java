package com.rtsbuilding.rtsbuilding.uicore.settings;

/** 设置输入的纯状态结果和生产副作用命令。 */
public final class SettingsUiTransition {
    public enum Command {
        NONE, APPLY_VIEW_STATE, TOGGLE_VALUE, ADJUST_VALUE, SET_SENSITIVITY
    }

    public final SettingsUiState state;
    public final Command command;
    public final SettingsUiAction action;

    public SettingsUiTransition(SettingsUiState state, Command command, SettingsUiAction action) {
        this.state = state;
        this.command = command;
        this.action = action;
    }
}
