package com.rtsbuilding.rtsbuilding.uicore.storage;

/** 储存绑定 reducer 结果。 */
public final class StorageUiTransition {
    public enum Command { NONE, SCROLL, SET_PRIORITY, TOGGLE_EXTRACT, UNLINK }
    public final StorageUiState state; public final StorageUiAction action; public final Command command;
    public StorageUiTransition(StorageUiState state,StorageUiAction action,Command command){
        this.state=state;this.action=action;this.command=command;
    }
}
