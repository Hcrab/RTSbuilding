package com.rtsbuilding.rtsbuilding.server.history;

/**
 * 冻结一条历史在原操作发生时的游戏模式和动作类型。
 *
 * <p>撤销时不能重新读取玩家当前模式，否则中途切换模式会错误恢复方块实体 NBT，
 * 或绕过生存模式的资源消耗。该类型只描述历史语义，不负责执行世界修改。</p>
 */
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

    public boolean destructive() { return destructive; }
    public boolean creative() { return creative; }
}
