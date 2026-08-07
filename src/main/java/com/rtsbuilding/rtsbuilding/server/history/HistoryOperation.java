package com.rtsbuilding.rtsbuilding.server.history;

/** 一条历史在原操作发生时冻结的动作与游戏模式语义。 */
public enum HistoryOperation {
    CREATIVE_BREAK(true, true),
    CREATIVE_PLACEMENT(false, true),
    SURVIVAL_BREAK(true, false),
    SURVIVAL_PLACEMENT(false, false);

    private final boolean destructive;
    private final boolean creative;

    HistoryOperation(boolean destructive, boolean creative) {
        this.destructive = destructive;
        this.creative = creative;
    }

    public boolean destructive() {
        return destructive;
    }

    public boolean creative() {
        return creative;
    }
}
