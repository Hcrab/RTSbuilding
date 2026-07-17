package com.rtsbuilding.rtsbuilding.server.task.placement;

/**
 * 缺材料任务显式恢复后的逐目标冲突策略。
 *
 * <p>策略属于 TaskStore 快照，而不是 UI 缓存或 Session Job；这样世界修改前必然经过
 * 对应 revision 的持久化 ACK。</p>
 */
public enum PlacementResumePolicy {
    DEFAULT,
    SKIP_CONFLICTS,
    OVERWRITE_CONFLICTS
}
