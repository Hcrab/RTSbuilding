package com.rtsbuilding.rtsbuilding.uicore.ultimine;

/** 连锁破坏预览、确认和进度状态机。 */
public final class UltimineUiReducer {
    private UltimineUiReducer() {}

    public static UltimineUiTransition apply(UltimineUiState state, UltimineUiAction action) {
        if (state == null || action == null) {
            throw new IllegalArgumentException("state/action");
        }
        switch (action.type) {
            case CONFIRM_PREVIEW:
                return state.canConfirm()
                        ? new UltimineUiTransition(state.confirmed(), action,
                                UltimineUiTransition.Command.START_CHAIN)
                        : none(state, action);
            case CANCEL:
                return new UltimineUiTransition(state.cancelled(), action,
                        state.phase == UltimineUiPhase.IDLE
                                ? UltimineUiTransition.Command.NONE
                                : UltimineUiTransition.Command.CANCEL);
            case SET_LIMIT:
                return state.enabled
                        ? new UltimineUiTransition(state.withLimit(action.value), action,
                                UltimineUiTransition.Command.SET_LIMIT)
                        : none(state, action);
            case SERVER_PROGRESS:
                return new UltimineUiTransition(state.progressed(action.value, action.total),
                        action, UltimineUiTransition.Command.NONE);
            default:
                return none(state, action);
        }
    }

    private static UltimineUiTransition none(UltimineUiState state, UltimineUiAction action) {
        return new UltimineUiTransition(state, action, UltimineUiTransition.Command.NONE);
    }
}
