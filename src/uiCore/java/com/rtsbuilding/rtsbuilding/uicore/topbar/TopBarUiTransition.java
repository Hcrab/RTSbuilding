package com.rtsbuilding.rtsbuilding.uicore.topbar;

/** 一次顶栏输入的状态结果和必须交给生产端执行的命令。 */
public final class TopBarUiTransition {
    public enum Command {
        NONE, FORCE_INTERACT, INTERACT, LINK, FUNNEL, ROTATE,
        QUICK_BUILD, QUEST_DETECT, CHUNK_VIEW, RANGE_CULLING,
        GUIDE, DEVELOPER, GEAR
    }

    public final TopBarUiState state;
    public final Command command;
    public final TopBarUiAction action;

    public TopBarUiTransition(TopBarUiState state, Command command, TopBarUiAction action) {
        this.state = state;
        this.command = command;
        this.action = action;
    }
}
