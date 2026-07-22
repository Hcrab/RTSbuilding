package com.rtsbuilding.rtsbuilding.uicore.ultimine;

/** reducer 结果；命令只描述副作用意图，不携带平台对象。 */
public final class UltimineUiTransition {
    public enum Command { NONE, START_CHAIN, CANCEL, SET_LIMIT }

    public final UltimineUiState state;
    public final UltimineUiAction action;
    public final Command command;

    public UltimineUiTransition(UltimineUiState state, UltimineUiAction action, Command command) {
        this.state = state;
        this.action = action;
        this.command = command;
    }
}
