package com.rtsbuilding.rtsbuilding.server.history;

/**
 * 一条撤回历史在原操作发生时的固定语义。
 *
 * <p>模式必须在操作完成时冻结，不能在 Ctrl+Z 时重新读取玩家当前游戏模式，
 * 否则切换模式会错误地恢复 NBT 或绕过生存资源消耗。</p>
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

    public boolean destructive() {
        return destructive;
    }

    public boolean creative() {
        return creative;
    }
}
