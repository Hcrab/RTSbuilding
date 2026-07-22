package com.rtsbuilding.rtsbuilding.uicore.culling;

/** reducer 结果；世界几何副作用由平台 adapter 执行。 */
public final class CullingUiTransition {
    public enum Command { NONE, DELETE_SELECTED, CONFIRM_DRAFT, CANCEL_DRAFT,
        CLOSE, ADJUST_HEIGHT, WORLD_PRIMARY, RESIZE_HANDLE }

    public final CullingUiState state;
    public final CullingUiAction action;
    public final Command command;

    public CullingUiTransition(CullingUiState state, CullingUiAction action, Command command) {
        this.state = state;
        this.action = action;
        this.command = command;
    }
}
