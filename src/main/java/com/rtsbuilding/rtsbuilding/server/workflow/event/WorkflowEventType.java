package com.rtsbuilding.rtsbuilding.server.workflow.event;

/**
 * 工作流引擎发出的生命周期事件类型。
 *
 * <p>子系统（存储页面刷新、历史记录、音效等）可以订阅这些事件，
 * 而无需在每个代码路径中显式串联回调。</p>
 */
public enum WorkflowEventType {
    /** 创建了一个新的工作流条目。 */
    STARTED,
    /** 进度更新（方块已完成/失败）。 */
    PROGRESS,
    /** 工作流被挂起（等待物品）。 */
    SUSPENDED,
    /** 已挂起的工作流被恢复。 */
    RESUMED,
    /** 工作流成功完成（之后条目会被移除）。 */
    COMPLETED,

    /**
     * 管道同步阶段成功完成。
     *
     * <p>与 {@link #COMPLETED} 不同，此事件<b>不</b>表示
     * 工作流本身已完成——仅表示管道的同步设置阶段结束。
     * 工作流可能仍在异步执行
     *（例如已入队的放置批次作业仍在处理中）。
     * 触发此事件时条目<b>不会</b>被移除。</p>
     */
    SYNC_PHASE_COMPLETED,
    /** 用户取消了工作流。 */
    CANCELLED,
    /** 工作流因超时被自动清理。 */
    TIMEOUT,
    /** 用户暂停了工作流。 */
    PAUSED,
    /** 用户取消暂停了工作流。 */
    UNPAUSED
}
