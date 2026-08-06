package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

/**
 * Quick Build 的无副作用 reducer。
 *
 * <p>它只维护玩家可见的主模式、目录和参数投影；插件门禁仍只限制 Destroy，实际放置、Smart Fill
 * 和便利破坏业务均由平台适配器沿用既有调用链执行。</p>
 */
public final class QuickBuildUiReducer {
    private QuickBuildUiReducer() {
    }

    public static QuickBuildUiTransition apply(QuickBuildUiState state, QuickBuildUiAction action) {
        if (state == null || action == null) {
            throw new IllegalArgumentException("state/action");
        }
        switch (action.type) {
            case SELECT_MODE:
                if (action.mode == QuickBuildUiMode.DESTROY && !state.destroyEnabled) {
                    return none(state, action);
                }
                return result(state.withMode(action.mode),
                        QuickBuildUiTransition.Command.SELECT_MODE, action);
            case SELECT_SHAPE:
                for (QuickBuildUiShapeOption option : state.shapes) {
                    if (option.shape == action.shape) {
                        return option.enabled
                                ? result(state.withShape(action.shape),
                                        QuickBuildUiTransition.Command.SELECT_SHAPE, action)
                                : none(state, action);
                    }
                }
                return none(state, action);
            case ACTIVATE_CONTROL:
                QuickBuildUiControl control = state.control(action.control);
                return control != null && control.enabled
                        ? result(state.withControl(action.control),
                                QuickBuildUiTransition.Command.ACTIVATE_CONTROL, action)
                        : none(state, action);
            case SET_CHAIN_LIMIT:
                return state.chainMode()
                        ? result(state.withChainLimit(action.value),
                                QuickBuildUiTransition.Command.SET_CHAIN_LIMIT, action)
                        : none(state, action);
            case SELECT_CATALOG_PAGE:
                if (action.catalogPage == null) {
                    return none(state, action);
                }
                if (state.mode == QuickBuildUiMode.DESTROY) {
                    return result(state.withCatalogPage(action.catalogPage),
                            QuickBuildUiTransition.Command.SELECT_CATALOG_PAGE, action);
                }
                QuickBuildUiMode buildMode =
                        action.catalogPage == QuickBuildUiCatalogPage.CONVENIENCE_TOOLS
                                ? QuickBuildUiMode.SMART_FILL : QuickBuildUiMode.BUILD;
                return result(state.withMode(buildMode),
                        QuickBuildUiTransition.Command.SELECT_CATALOG_PAGE, action);
            case SELECT_CONVENIENCE_TOOL:
                return state.mode == QuickBuildUiMode.DESTROY && action.convenienceTool != null
                        ? result(state.withConvenienceTool(action.convenienceTool),
                                QuickBuildUiTransition.Command.SELECT_CONVENIENCE_TOOL, action)
                        : none(state, action);
            case SET_CONVENIENCE_PARAMETER:
                return state.convenienceMode() && action.convenienceParameter != null
                        ? result(state.withConvenienceParameter(
                                action.convenienceParameter, action.value),
                                QuickBuildUiTransition.Command.SET_CONVENIENCE_PARAMETER, action)
                        : none(state, action);
            case SET_SMART_FILL_MAX_BLOCKS:
                return state.mode == QuickBuildUiMode.SMART_FILL
                        ? result(state.withSmartFillMaxBlocks(action.value),
                                QuickBuildUiTransition.Command.SET_SMART_FILL_MAX_BLOCKS, action)
                        : none(state, action);
            case SET_SMART_FILL_DIAMETER:
                return state.mode == QuickBuildUiMode.SMART_FILL
                        ? result(state.withSmartFillDiameter(action.value),
                                QuickBuildUiTransition.Command.SET_SMART_FILL_DIAMETER, action)
                        : none(state, action);
            case CLOSE:
                return result(state.closed(), QuickBuildUiTransition.Command.CLOSE, action);
            default:
                return none(state, action);
        }
    }

    private static QuickBuildUiTransition result(QuickBuildUiState state,
            QuickBuildUiTransition.Command command, QuickBuildUiAction action) {
        return new QuickBuildUiTransition(state, command, action);
    }

    private static QuickBuildUiTransition none(QuickBuildUiState state,
            QuickBuildUiAction action) {
        return result(state, QuickBuildUiTransition.Command.NONE, action);
    }
}
