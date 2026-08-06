package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

/** Quick Build reducer 结果；生产 adapter 据此执行唯一的平台副作用。 */
public final class QuickBuildUiTransition {
    public enum Command {
        NONE, SELECT_MODE, SELECT_SHAPE, ACTIVATE_CONTROL, SET_CHAIN_LIMIT,
        SELECT_CATALOG_PAGE, SELECT_CONVENIENCE_TOOL, SET_CONVENIENCE_PARAMETER,
        SET_SMART_FILL_MAX_BLOCKS, SET_SMART_FILL_DIAMETER,
        CLOSE
    }

    public final QuickBuildUiState state;
    public final Command command;
    public final QuickBuildUiAction action;

    public QuickBuildUiTransition(QuickBuildUiState state, Command command,
            QuickBuildUiAction action) {
        this.state = state;
        this.command = command;
        this.action = action;
    }
}
