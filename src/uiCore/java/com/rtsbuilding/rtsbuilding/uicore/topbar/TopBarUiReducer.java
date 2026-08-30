package com.rtsbuilding.rtsbuilding.uicore.topbar;

/** 顶栏可见性、锁定模式与切换按钮的权威纯逻辑。 */
public final class TopBarUiReducer {
    private TopBarUiReducer() {
    }

    public static TopBarUiTransition apply(TopBarUiState state, TopBarUiAction action) {
        if (state == null || action == null) throw new IllegalArgumentException("state/action");
        TopBarUiButton button = state.button(action.buttonId);
        if (button == null || !button.visible) {
            return new TopBarUiTransition(state, TopBarUiTransition.Command.NONE, action);
        }
        if (state.blueprintPlacementLocked && action.buttonId.modeButton) {
            return new TopBarUiTransition(state.withMode(TopBarUiState.Mode.INTERACT),
                    TopBarUiTransition.Command.FORCE_INTERACT, action);
        }

        TopBarUiState next = state;
        TopBarUiTransition.Command command;
        switch (action.buttonId) {
            case INTERACT:
                next = state.withMode(TopBarUiState.Mode.INTERACT);
                command = TopBarUiTransition.Command.INTERACT;
                break;
            case LINK:
                next = state.withMode(TopBarUiState.Mode.LINK_STORAGE);
                command = TopBarUiTransition.Command.LINK;
                break;
            case FUNNEL:
                next = state.withMode(TopBarUiState.Mode.FUNNEL);
                command = TopBarUiTransition.Command.FUNNEL;
                break;
            case ROTATE:
                next = state.withMode(TopBarUiState.Mode.ROTATE);
                command = TopBarUiTransition.Command.ROTATE;
                break;
            case QUICK_BUILD:
                next = state.toggle(action.buttonId);
                command = TopBarUiTransition.Command.QUICK_BUILD;
                break;
            case QUEST_DETECT:
                command = TopBarUiTransition.Command.QUEST_DETECT;
                break;
            case CHUNK_VIEW:
                next = state.toggle(action.buttonId);
                command = TopBarUiTransition.Command.CHUNK_VIEW;
                break;
            case RANGE_CULLING:
                next = state.toggle(action.buttonId);
                command = TopBarUiTransition.Command.RANGE_CULLING;
                break;
            case GUIDE:
                next = state.toggle(action.buttonId);
                command = TopBarUiTransition.Command.GUIDE;
                break;
            case DEVELOPER:
                command = TopBarUiTransition.Command.DEVELOPER;
                break;
            case GEAR:
                next = state.toggle(action.buttonId);
                command = TopBarUiTransition.Command.GEAR;
                break;
            default:
                command = TopBarUiTransition.Command.NONE;
        }
        return new TopBarUiTransition(next, command, action);
    }
}
