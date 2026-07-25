package com.rtsbuilding.rtsbuilding.uicore.culling;

/** 范围剔除面板与编辑命令的纯状态机。 */
public final class CullingUiReducer {
    private CullingUiReducer() {}

    public static CullingUiTransition apply(CullingUiState state, CullingUiAction action) {
        if (state == null || action == null) throw new IllegalArgumentException("state/action");
        if (!state.enabled) return none(state, action);
        switch (action.type) {
            case DELETE_SELECTED:
                return state.hasSelection()
                        ? transition(state.afterDelete(), action, CullingUiTransition.Command.DELETE_SELECTED)
                        : none(state, action);
            case CONFIRM_DRAFT:
                return state.hasCompleteDraft()
                        ? transition(state.cancelledDraft(), action, CullingUiTransition.Command.CONFIRM_DRAFT)
                        : none(state, action);
            case CANCEL_DRAFT:
                return state.phase != CullingUiPhase.IDLE
                        ? transition(state.cancelledDraft(), action, CullingUiTransition.Command.CANCEL_DRAFT)
                        : none(state, action);
            case CLOSE:
                return transition(state.closed(), action, CullingUiTransition.Command.CLOSE);
            case ADJUST_HEIGHT:
                return state.phase != CullingUiPhase.IDLE && action.value != 0
                        ? transition(state.withPreviewHeight(state.previewHeight + action.value), action,
                                CullingUiTransition.Command.ADJUST_HEIGHT)
                        : none(state, action);
            case WORLD_PRIMARY:
                return state.open
                        ? transition(state, action, CullingUiTransition.Command.WORLD_PRIMARY)
                        : none(state, action);
            case RESIZE_HANDLE:
                return state.hasSelection() && action.direction != null && action.value != 0
                        ? transition(state, action, CullingUiTransition.Command.RESIZE_HANDLE)
                        : none(state, action);
            default:
                return none(state, action);
        }
    }

    private static CullingUiTransition transition(CullingUiState state, CullingUiAction action,
            CullingUiTransition.Command command) {
        return new CullingUiTransition(state, action, command);
    }
    private static CullingUiTransition none(CullingUiState state, CullingUiAction action) {
        return transition(state, action, CullingUiTransition.Command.NONE);
    }
}
