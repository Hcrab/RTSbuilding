package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

/** Quick Build 的模式、形状、填充与连锁上限 reducer。 */
public final class QuickBuildUiReducer {
    private QuickBuildUiReducer() {}
    public static QuickBuildUiTransition apply(QuickBuildUiState state, QuickBuildUiAction action) {
        if (state == null || action == null) throw new IllegalArgumentException("state/action");
        switch (action.type) {
            case SELECT_MODE:
                if (action.mode == QuickBuildUiMode.DESTROY && !state.destroyEnabled) return none(state, action);
                return result(state.withMode(action.mode), QuickBuildUiTransition.Command.SELECT_MODE, action);
            case SELECT_SHAPE:
                for (QuickBuildUiShapeOption option : state.shapes) {
                    if (option.shape == action.shape) {
                        return option.enabled ? result(state.withShape(action.shape),
                                QuickBuildUiTransition.Command.SELECT_SHAPE, action) : none(state, action);
                    }
                }
                return none(state, action);
            case ACTIVATE_CONTROL:
                QuickBuildUiControl control = state.control(action.control);
                return control != null && control.enabled ? result(state.withControl(action.control),
                        QuickBuildUiTransition.Command.ACTIVATE_CONTROL, action) : none(state, action);
            case SET_CHAIN_LIMIT:
                return state.chainMode() ? result(state.withChainLimit(action.value),
                        QuickBuildUiTransition.Command.SET_CHAIN_LIMIT, action) : none(state, action);
            case CLOSE:
                return result(state.closed(), QuickBuildUiTransition.Command.CLOSE, action);
            default:
                return none(state, action);
        }
    }
    private static QuickBuildUiTransition result(QuickBuildUiState s, QuickBuildUiTransition.Command c,
                                                  QuickBuildUiAction a){return new QuickBuildUiTransition(s,c,a);}
    private static QuickBuildUiTransition none(QuickBuildUiState s,QuickBuildUiAction a){return result(s,QuickBuildUiTransition.Command.NONE,a);}
}
