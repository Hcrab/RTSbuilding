package com.rtsbuilding.rtsbuilding.server.workflow.model;

/**
 * 工作流操作的优先级级别。
 *
 * <p>优先级决定了当多个工作流可能同时活动，或某个操作需要抢占另一个操作时，
 * 系统应如何处理冲突、资源分配和 UI 强调。</p>
 */
public enum RtsWorkflowPriority {

    /** 后台/低重要性任务（例如空闲的区域填充）。 */
    LOW(0),

    /** 大多数玩家发起操作的默认优先级。 */
    NORMAL(1),

    /** 较高优先级的任务，应中断低优先级工作。 */
    HIGH(2),

    /** 关键任务，必须优先完成（例如工具即将损坏）。 */
    CRITICAL(3);

    private final int rank;

    RtsWorkflowPriority(int rank) {
        this.rank = rank;
    }

    /**
     * 返回此优先级的数字等级。值越大表示越紧急。
     */
    public int rank() {
        return this.rank;
    }

    /**
     * 返回 {@code true} 表示此优先级严格高于给定的另一优先级。
     */
    public boolean isHigherThan(RtsWorkflowPriority other) {
        return this.rank > other.rank;
    }
}
